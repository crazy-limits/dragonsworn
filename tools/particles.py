"""Builds the void flame particle sprites: Ice and Fire's dragon fire, recolored to Dragon's Breath.

    python3 tools/particles.py        (build_assets.py runs it too)

Source: tools/source/iaf_dragon_flame.png, the fire-breath particle of Ice and Fire
(https://github.com/AlexModGuy/Ice_and_Fire, branch 1.18.2,
src/main/resources/assets/iceandfire/textures/particles/dragon_flame.png, LGPL-3.0).
It is an 8x8 flame in four colors, hottest to coolest: cream core, yellow, orange, red tip.

Each source color maps to a Dragon's Breath shade (the bottle's white core and pink-purple wisps), and
the sprite cools over its life: frame 0 is a white-hot lilac flame, the last a dim violet ember. The
game picks the frame from the particle's age (VoidFlameParticle), so a stream is white at the mouth
and purple where it ends.
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, 'source', 'iaf_dragon_flame.png')
DST = os.path.join(os.path.dirname(HERE), 'src', 'mc', 'shared', 'resources', 'assets', 'dragonfall', 'textures', 'particle')
FRAMES = 4

# source color -> (hot, cold) replacement
PALETTE = {
	(255, 245, 198): ((255, 255, 255), (238, 196, 255)),   # core: white -> pale lilac
	(255, 216, 0): ((246, 210, 255), (196, 112, 236)),     # inner: lilac -> orchid
	(255, 106, 0): ((214, 120, 246), (140, 52, 196)),      # body: Dragon's Breath magenta-purple -> purple
	(255, 0, 0): ((156, 62, 214), (78, 22, 128)),          # tip: violet -> deep violet
}


def mix(a, b, k):
	return tuple(round(x + (y - x) * k) for x, y in zip(a, b))


def build():
	src = Image.open(SRC).convert('RGBA')
	unknown = {px[:3] for px in src.getdata() if px[3] and px[:3] not in PALETTE}
	if unknown:
		raise SystemExit(f'unmapped source colors: {sorted(unknown)}')
	os.makedirs(DST, exist_ok=True)
	for f in range(FRAMES):
		k = f / (FRAMES - 1)
		out = Image.new('RGBA', src.size)
		out.putdata([(*mix(*PALETTE[px[:3]], k), px[3]) if px[3] else (0, 0, 0, 0) for px in src.getdata()])
		path = os.path.join(DST, f'void_flame_{f}.png')
		out.save(path)
		print('  ->', os.path.relpath(path, os.path.dirname(HERE)))


if __name__ == '__main__':
	build()
