"""Builds the void flame particle sprites: Ice and Fire's dragon fire, recolored to Dragon's Breath.

    python3 tools/particles.py        (build_assets.py runs it too)

Source: tools/source/iaf_dragon_flame.png, the fire-breath particle of Ice and Fire
(https://github.com/AlexModGuy/Ice_and_Fire, branch 1.18.2,
src/main/resources/assets/iceandfire/textures/particles/dragon_flame.png, LGPL-3.0).
It is an 8x8 flame in four colors, hottest to coolest: cream core, yellow, orange, red tip.

Each source color maps to a Dragon's Breath shade (the bottle's white core and pink-purple wisps), and
the sprite cools over its life: frame 0 is a white-hot lilac flame, the last a dim violet ember. The
game picks the frame from the particle's age (VoidFlameParticle). These are the small flames: the
embers in the mouth and the flames licking out of breath clouds.

The stream's own sprites (void_breath_*, drawn here, no source image) are a puff's whole life: a
cloud-like ball of purple fire, white-hot inside, whose lumpy edge darkens into smoke that eats inward
until it is a dark, roiling smoke puff with a few purple flames still flickering in it, dying out.
"""
import math
import os
import random

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


BREATH_FRAMES, BREATH_SIZE = 10, 16
# fire, hottest to coolest; smoke, light to dark; the last flames left in the smoke
FIRE = [(255, 236, 255), (242, 168, 255), (214, 102, 246), (156, 48, 214), (108, 30, 168)]
SMOKE = [(78, 58, 96), (58, 42, 74), (42, 30, 56), (29, 21, 40)]
EMBER = [(232, 120, 255), (178, 64, 230)]


def breath_frame(f, noise, puffs):
	"""Frame f of the stream's puff: k = 0 a ball of purple fire, 1 dark smoke with dying flames."""
	k = f / (BREATH_FRAMES - 1)
	n, c = BREATH_SIZE, (BREATH_SIZE - 1) / 2.0
	img = Image.new('RGBA', (n, n))
	smoke = min(1.0, max(0.0, (k - 0.15) / 0.6))       # how far the smoke has eaten in from the edge
	fade = min(1.0, max(0.0, (k - 0.55) / 0.45))        # the flames left in the smoke dying out
	for y in range(n):
		for x in range(n):
			# a cloud: round puffs blended into one (metaballs), billowing out as it ages
			field = 0.0
			for px, py, pr in puffs:
				spread = 1.0 + 0.35 * k
				dx, dy = x - (c + px * spread), y - (c + py * spread)
				r = pr * (0.85 + 0.25 * k)
				field += r * r / (dx * dx + dy * dy + 0.5)
			field *= 1.0 + 0.25 * (noise[y][x] - 0.5)
			if field < 1.0:
				continue
			d = 1.0 / math.sqrt(field)                          # 0 at the heart, 1 at the rim
			if d > 1.0 - 1.1 * smoke ** 1.5:
				# smoke: purple-black, darker toward the rim and as it ages, mottled
				shade = min(len(SMOKE) - 1, int((d * 0.7 + k * 0.4 + noise[(y + 5) % n][(x + 3) % n] * 0.5) * len(SMOKE) / 1.4))
				alpha = round(230 - 70 * d * d)
				# a few flames still flickering in the smoke, fewer and fewer, never at the rim
				if noise[(y + 9) % n][(x + 7) % n] > 0.82 + 0.18 * fade and d < 0.75:
					img.putpixel((x, y), (*EMBER[int(noise[y][(x + 1) % n] * 2)], 255))
				else:
					img.putpixel((x, y), (*SMOKE[shade], alpha))
			else:
				# fire: white-hot heart, magenta, purple rim; it cools as it ages
				heat = 0.85 * d * d * (1.0 - smoke * 0.6) + 0.3 * k + 0.1 * noise[(y + 2) % n][x]
				img.putpixel((x, y), (*FIRE[min(len(FIRE) - 1, int(heat * len(FIRE)))], 255))
	return img


def build_breath():
	rng = random.Random(7)
	noise = [[rng.random() for _ in range(BREATH_SIZE)] for _ in range(BREATH_SIZE)]
	# a big middle puff and smaller ones round it
	puffs = [(0.0, 0.0, 2.6)] + [(math.cos(a) * d, math.sin(a) * d, rng.uniform(1.4, 2.1))
								 for a, d in ((i * 2 * math.pi / 6 + rng.uniform(-0.35, 0.35), rng.uniform(3.6, 4.6)) for i in range(6))]
	os.makedirs(DST, exist_ok=True)
	for f in range(BREATH_FRAMES):
		path = os.path.join(DST, f'void_breath_{f}.png')
		breath_frame(f, noise, puffs).save(path)
		print('  ->', os.path.relpath(path, os.path.dirname(HERE)))


if __name__ == '__main__':
	build()
	build_breath()
