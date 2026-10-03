"""The gallery's stills: picked frames of the film's gallery bursts (run/<target>/screenshots/still-<shot>-<n>.png,
`-Pdragonsworn.film=gallery`), graded (shadows lifted, a little more colour, a soft vignette: the End is dark)
and saved 1920x1080 as gallery/<name>.jpg in the project's root (quality 92, no chroma subsampling: ~0.5 MB,
~40 dB PSNR against the PNG, no visible loss).

    python3 tools/gallery.py [--target 1.21.1-fabric] [name=shot-n ...]

Without picks it grades PICKS. Needs Pillow + numpy.
"""
import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageEnhance

ROOT = Path(__file__).resolve().parent.parent
SIZE = (1920, 1080)
PICKS = {
    "01-hover-breath": "hover-013",
    "02-death": "death-029",
    "03-breath": "breath-026",
    "04-breath-inhale": "breath-014",
    "05-breath-pass": "pass-036",
    "06-jaw-grab": "jaws-020",
    "07-claw-grab": "snatch-020",
    "08-landing": "landing-034",
    "09-flight": "flight-055",
    "10-takeoff": "takeoff-010",
}


def grade(im):
    im = im.convert("RGB").resize(SIZE, Image.Resampling.LANCZOS)
    a = np.asarray(im).astype(np.float32) / 255.0
    a = a ** 0.8                                   # lift the shadows, keep the highlights
    h, w = a.shape[:2]
    y, x = np.mgrid[0:h, 0:w]
    r = np.hypot((x - w / 2) / (w / 2), (y - h / 2) / (h / 2)) / np.sqrt(2)
    a *= (1.0 - 0.35 * r ** 2.2)[..., None]        # a soft vignette
    im = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))
    im = ImageEnhance.Color(im).enhance(1.15)
    return ImageEnhance.Contrast(im).enhance(1.05)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--target", default="1.21.1-fabric")
    ap.add_argument("picks", nargs="*", help="name=shot-n")
    args = ap.parse_args()
    picks = dict(p.split("=", 1) for p in args.picks) or PICKS
    shots = ROOT / "run" / args.target / "screenshots"
    out = ROOT / "gallery"
    out.mkdir(exist_ok=True)
    for name, shot in picks.items():
        src = shots / f"still-{shot}.png"
        if not src.exists():
            print(f"{name}: no {src.name}")
            continue
        grade(Image.open(src)).save(out / f"{name}.jpg", "JPEG", quality=92, optimize=True, progressive=True, subsampling=0)
        print(f"gallery/{name}.jpg <- {src.name}")


if __name__ == "__main__":
    main()
