"""The film's GIFs: every scene's frames (run/<target>/screenshots/film-<scene>-<n>.png, one a game tick,
written by the in-game film, Film.java) made one looping GIF, gallery/dragonsworn-<scene>.gif in the project's root.

    python3 tools/film_gifs.py [--target 1.21.1-fabric] [--width 480] [--step 2] [scene ...]

--step 2 (the default) keeps every other frame: 10 fps, half the size of --step 1's 20 fps. No dithering: the
moving End stone's dither noise would double the size. Needs Pillow.
"""
import argparse
import re
from collections import defaultdict
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
FRAME = re.compile(r"film-(.+)-(\d{4})\.png$")
SKIP = 2


def frames(shots):
    scenes = defaultdict(list)
    for p in shots.glob("film-*.png"):
        m = FRAME.match(p.name)
        if m:
            scenes[m.group(1)].append((int(m.group(2)), p))
    return {s: [p for _, p in sorted(v)] for s, v in scenes.items()}


def palette(images):
    """One palette for the whole clip (no flicker between frames): quantized from a strip of every 8th frame."""
    sample = images[::8] or images
    w, h = sample[0].size
    strip = Image.new("RGB", (w, h * len(sample)))
    for i, im in enumerate(sample):
        strip.paste(im, (0, i * h))
    return strip.quantize(colors=255, method=Image.Quantize.MEDIANCUT)


def gif(scene, paths, width, step):
    images = []
    # a frame is grabbed before that tick's camera move is drawn: the first ones still show the last shot
    for p in paths[SKIP::step]:
        im = Image.open(p).convert("RGB")
        images.append(im.resize((width, round(im.height * width / im.width)), Image.Resampling.LANCZOS))
    pal = palette(images)
    out = [im.quantize(palette=pal, dither=Image.Dither.NONE) for im in images]
    target = ROOT / "gallery" / f"dragonsworn-{scene}.gif"
    target.parent.mkdir(exist_ok=True)
    out[0].save(target, save_all=True, append_images=out[1:], duration=50 * step, loop=0, optimize=False, disposal=1)
    print(f"{target.name}: {len(out)} frames, {target.stat().st_size / 1e6:.1f} MB")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--target", default="1.21.1-fabric")
    ap.add_argument("--width", type=int, default=480)
    ap.add_argument("--step", type=int, default=2)
    ap.add_argument("scenes", nargs="*")
    args = ap.parse_args()
    found = frames(ROOT / "run" / args.target / "screenshots")
    for scene in args.scenes or sorted(found):
        if scene not in found:
            print(f"{scene}: no frames")
            continue
        gif(scene, found[scene], args.width, args.step)


if __name__ == "__main__":
    main()
