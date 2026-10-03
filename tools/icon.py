"""Draws the mod icon: pixel art, 128 x 128, no source image.

    python3 tools/icon.py [preview.png]     (the preview is the icon x4, nearest neighbour)

The scene: the End at night, seen from low down. The dragon flies over the island, wings spread (its
silhouette traced from `source/icon_dragon.png`), a great pale light behind it: its hide is dark against the
light and the membranes glow with it. Spiral obsidian spires (`arena/Monolith`'s, flat tops) frame it, tall at the
edges, each crowned by its End crystal, their healing beams running to the dragon's chest. In the foreground
a player, a black silhouette, stands braced on the end stone with the sword raised against it.

Everything is drawn on the 128 grid without anti-aliasing (masks filled with polygons/ellipses, then shaded
per pixel), colours from the dragon texture's palette; gradients are ordered-dithered (Bayer 4x4) in steps.
"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), 'src', 'mc', 'shared', 'resources', 'assets', 'dragonsworn', 'icon.png')
N = 128

BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]

# the dragon texture's palette (hide) and its eyes
HIDE = [(2, 0, 2), (8, 7, 14), (21, 17, 21), (32, 24, 32), (41, 33, 45), (49, 39, 53), (74, 63, 71), (90, 79, 87)]
EYE = [(166, 50, 167), (241, 56, 241), (255, 200, 255)]
SKY = [(6, 3, 12), (12, 6, 22), (20, 10, 34), (30, 15, 48), (42, 21, 62), (56, 30, 80)]
GLOW = [(56, 30, 80), (74, 42, 102), (96, 58, 128), (122, 80, 154), (150, 108, 180), (184, 146, 206),
	(216, 188, 232)]
MEMBRANE = [(14, 8, 22), (26, 16, 40), (42, 26, 62), (62, 38, 88), (86, 54, 114), (112, 74, 140)]
SILHOUETTE = (4, 2, 8)
STONE = [(110, 104, 70), (150, 146, 98), (190, 188, 130), (219, 222, 158), (236, 238, 186)]
OBSIDIAN = [(6, 4, 12), (15, 10, 24), (27, 18, 41), (44, 28, 66), (72, 46, 104), (110, 76, 150)]
CRYSTAL = [(120, 40, 140), (200, 90, 210), (250, 170, 250), (255, 240, 255)]
BEAM = [(140, 70, 170), (210, 140, 230), (255, 220, 255)]


def dither(t, steps, x, y):
	"""Index 0..steps-1 for t in [0, 1], ordered-dithered between neighbouring steps."""
	v = max(0.0, min(1.0, t)) * (steps - 1)
	i = int(v)
	return min(steps - 1, i + (1 if (v - i) * 16 > BAYER[y % 4][x % 4] + 0.5 else 0))


def mask(draw_fn):
	m = Image.new('1', (N, N), 0)
	draw_fn(ImageDraw.Draw(m))
	return m


def paint(img, m, shade):
	"""Colours every pixel set in mask m with shade(x, y)."""
	px, mp = img.load(), m.load()
	for y in range(N):
		for x in range(N):
			if mp[x, y]:
				c = shade(x, y)
				if c is not None:
					px[x, y] = c


def edge(m, x, y, dx, dy):
	"""Whether (x, y) of mask m has an empty neighbour at (x + dx, y + dy)."""
	nx, ny = x + dx, y + dy
	return not (0 <= nx < N and 0 <= ny < N) or not m.getpixel((nx, ny))


# ---- the scene -------------------------------------------------------------------------------------------

DRAGON_GLOW = (64, 30, 44)      # middle and radius of the light behind the dragon
CHEST = (70, 32)                # where the crystal beams meet
HORIZON = 100


def sky(img):
	px = img.load()
	rng = random.Random(7)
	gx, gy, gr = DRAGON_GLOW
	for y in range(N):
		for x in range(N):
			t = y / HORIZON
			c = SKY[dither(0.1 + 0.8 * t, len(SKY), x, y)]
			d = math.hypot((x - gx) / 1.2, y - gy) / gr
			if d < 1.5:
				g = max(0.0, 1 - d / 1.5) ** 1.6 * 1.35
				i = dither(g, len(GLOW) + 2, x, y) - 2       # the two lowest steps keep the sky
				c = GLOW[i] if i >= 0 else c
			px[x, y] = c
	# rays: faint spokes out of the light, every other one
	for y in range(N):
		for x in range(N):
			d = math.hypot(x - gx, y - gy)
			if gr * 0.55 < d < gr * 1.6:
				a = math.atan2(y - gy, x - gx)
				if math.cos(a * 9 + 0.6) > 0.82 and BAYER[y % 4][x % 4] < 8 * (1 - d / (gr * 1.6)) + 2:
					r, g, b = px[x, y]
					px[x, y] = (min(255, r + 18), min(255, g + 12), min(255, b + 24))
	# stars: single texels, a few with a faint cross
	for _ in range(80):
		x, y = rng.randrange(N), rng.randrange(HORIZON - 10)
		if math.hypot(x - gx, y - gy) < gr * 1.2:
			continue
		b = rng.random()
		px[x, y] = (200, 180, 230) if b > 0.85 else (130, 110, 170) if b > 0.4 else (84, 66, 120)
		if b > 0.93:
			for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
				if 0 <= x + dx < N and 0 <= y + dy < N:
					px[x + dx, y + dy] = (100, 80, 140)


def haze(img):
	"""A violet mist on the horizon: the spires' feet fade into it and the player's shoulders stand out on it."""
	px = img.load()
	for y in range(HORIZON - 26, HORIZON + 2):
		k = (y - (HORIZON - 26)) / 26
		for x in range(N):
			if BAYER[y % 4][x % 4] < k * k * 16:
				r, g, b = px[x, y]
				w = 0.55 * k
				px[x, y] = (int(r + (120 - r) * w), int(g + (84 - g) * w), int(b + (160 - b) * w))


# spires: (x of the middle, top y, half width at the top, half width at the base, twist turns)
SPIRES = [
	(31, 70, 3, 4, 0.3),
	(100, 76, 4, 5, 0.35),
	(9, 34, 7, 9, 0.6),
	(119, 46, 6, 8, 0.5),
]
LIGHT = math.radians(-60)       # where the light comes from, round the towers (toward the glow, a little front)


def spire(img, sx, top, w0, w1, turns):
	"""A twisted square prism (Monolith's CROWN, flat top): each row is the square turned a little further;
	its faces toward the viewer are shaded by how they face the light, the corners between them lit."""
	base = HORIZON + 4
	px = img.load()
	light = LIGHT if sx < DRAGON_GLOW[0] else math.pi - LIGHT
	for y in range(top, base):
		t = (y - top) / (base - top)
		R = (w0 + (w1 - w0) * t) / math.sqrt(2) * 1.25
		a = turns * 2 * math.pi * (1 - t) + sx
		corners = [a + k * math.pi / 2 for k in range(4)]
		for k in range(4):
			c0, c1 = corners[k], corners[(k + 1) % 4]
			n = (c0 + c1) / 2 if k < 3 else c0 + math.pi / 4
			if math.sin(n) <= 0:                         # faces away from the viewer
				continue
			x0, x1 = sorted((sx + R * math.cos(c0) * 1.41, sx + R * math.cos(c1) * 1.41))
			lam = max(0.0, math.cos(n - light))
			v = 0.12 + 0.55 * lam - 0.3 * t
			for x in range(round(x0), round(x1) + 1):
				if 0 <= x < N:
					px[x, y] = OBSIDIAN[dither(v, len(OBSIDIAN), x, y)]
			for xc, cc in ((x0, c0), (x1, c1)):         # a corner on the near side catches the light
				if math.sin(cc) > 0.3 and 0 <= round(xc) < N:
					px[round(xc), y] = OBSIDIAN[min(5, 3 + int(2 * lam + 0.5 - t))]
	# the flat top: a lighter rim
	for x in range(int(sx - w0), int(sx + w0) + 1):
		px[x, top] = OBSIDIAN[4]
	# bedrock and the crystal over it
	for x in range(sx - 1, sx + 2):
		px[x, top - 1] = (60, 60, 60)
	crystal(img, sx, top - 6)
	return (sx, top - 6)


def crystal(img, cx, cy):
	px = img.load()
	# a soft halo
	for y in range(cy - 6, cy + 7):
		for x in range(cx - 6, cx + 7):
			d = math.hypot(x - cx, y - cy)
			if d < 6 and 0 <= x < N and 0 <= y < N:
				r, g, b = px[x, y]
				k = (6 - d) / 6 * 0.45
				px[x, y] = (int(r + (200 - r) * k), int(g + (100 - g) * k), int(b + (220 - b) * k))
	# the cube seen corner on: a diamond with a bright core
	for dy in range(-3, 4):
		for dx in range(-3, 4):
			d = abs(dx) + abs(dy)
			if d <= 3:
				c = CRYSTAL[3] if d == 0 else CRYSTAL[2] if d == 1 else CRYSTAL[1] if d == 2 else CRYSTAL[0]
				px[cx + dx, cy + dy] = c


def beam(img, a, b):
	"""A crystal's healing beam: a 1-px line, brightest in the middle third, with dashes (the runes)."""
	px = img.load()
	(x0, y0), (x1, y1) = a, b
	n = int(max(abs(x1 - x0), abs(y1 - y0)))
	for i in range(3, n - 1):
		t = i / n
		x, y = round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t)
		c = BEAM[2] if i % 5 == 0 else BEAM[1] if i % 5 in (1, 4) else BEAM[0]
		px[x, y] = c


def ground(img):
	"""The island: end stone from a bumpy horizon down, darker toward the bottom edge (a low camera)."""
	px = img.load()
	rng = random.Random(3)
	bumps = [HORIZON + round(2 * math.sin(x * 0.11) + 1.2 * math.sin(x * 0.37 + 1)) for x in range(N)]
	for x in range(N):
		for y in range(bumps[x], N):
			t = (y - bumps[x]) / (N - HORIZON)
			light = 1.0 - 0.8 * t + (0.25 if y == bumps[x] else 0)
			px[x, y] = STONE[dither(light, len(STONE), x, y)]
	# end stone's speckles
	for _ in range(160):
		x, y = rng.randrange(N), rng.randrange(HORIZON + 2, N)
		if y > bumps[x] + 1:
			r, g, b = px[x, y]
			px[x, y] = (int(r * 0.82), int(g * 0.82), int(b * 0.8))
	return bumps


# ---- the dragon ------------------------------------------------------------------------------------------

# the silhouette (tools/source/icon_dragon.png, black on white), drawn DRAGON_W wide with its top left at DRAGON_AT;
# everything on it is placed in fractions (u, v) of its width and height
DRAGON_SRC = os.path.join(HERE, 'source', 'icon_dragon.png')
DRAGON_AT, DRAGON_W = (14, 3), 100
# the wings' bones: the wrist (the leading edge's peak) to the points between the scallops
BONES = [((0.375, 0.0), [(0.01, 0.26), (0.10, 0.31), (0.23, 0.41), (0.33, 0.48)]),
	((0.84, 0.19), [(0.99, 0.50), (0.90, 0.48), (0.77, 0.55)])]
EYE_UV = (0.605, 0.225)


def silhouette():
	src = Image.open(DRAGON_SRC).convert('L')
	h = round(src.height * DRAGON_W / src.width)
	small = src.resize((DRAGON_W, h), Image.LANCZOS)
	m = Image.new('1', (N, N), 0)
	m.paste(small.point(lambda v: 255 if v < 150 else 0).convert('1'), DRAGON_AT)
	return m, h


def at(uv, h):
	return DRAGON_AT[0] + uv[0] * (DRAGON_W - 1), DRAGON_AT[1] + uv[1] * (h - 1)


def membrane_at(u, v):
	"""Whether (u, v) is on a wing's membrane (the rest is body, neck, head, legs, tail)."""
	return v < 0.5 and (u < 0.41 or u > 0.69)


def dragon(img):
	gx, gy, gr = DRAGON_GLOW
	m, h = silhouette()
	ux = lambda x: (x - DRAGON_AT[0]) / (DRAGON_W - 1)
	vy = lambda y: (y - DRAGON_AT[1]) / (h - 1)

	def shade(x, y):
		rim = edge(m, x, y, 0, -1) or edge(m, x, y, -1, -1) or edge(m, x, y, 1, -1)
		if membrane_at(ux(x), vy(y)):
			# the light behind shines through the membrane: brighter toward the light's middle, dark rims
			d = math.hypot(x - gx, y - gy) / (gr * 1.3)
			v = 0.25 + 0.75 * max(0.0, 1 - d)
			if rim or edge(m, x, y, -1, 0) or edge(m, x, y, 1, 0) or edge(m, x, y, 0, 1):
				v -= 0.35
			return MEMBRANE[dither(v, len(MEMBRANE), x, y)]
		# the body: dark, its upper edges rimmed by the light behind
		return HIDE[5] if rim else HIDE[3] if edge(m, x, y, 1, 0) or edge(m, x, y, -1, 0) else HIDE[1]
	paint(img, m, shade)

	def bones(d):
		for wrist, tips in BONES:
			for t in tips:
				d.line([at(wrist, h), at(t, h)], fill=1)
	mp = m.load()
	paint(img, mask(bones), lambda x, y: HIDE[1] if mp[x, y] and not edge(m, x, y, 0, -1) else None)
	# the eye, glowing
	px = img.load()
	ex, ey = at(EYE_UV, h)
	ex, ey = round(ex), round(ey)
	px[ex, ey], px[ex - 1, ey] = EYE[2], EYE[1]
	px[ex + 1, ey] = EYE[0]


# ---- the player ------------------------------------------------------------------------------------------

def player(img, fx, fy):
	"""Steve from behind, a black silhouette, feet at (fx, fy): braced wide, the left arm thrown out, the
	sword raised high in the right hand toward the dragon. 34 px tall, the sword's point at ~fy - 55."""
	def draw(d):
		top = fy - 34
		# legs: wide stance, the right one stepped back
		d.polygon([(fx - 4, top + 20), (fx, top + 20), (fx - 6, fy), (fx - 11, fy)], fill=1)
		d.polygon([(fx, top + 20), (fx + 4, top + 20), (fx + 11, fy), (fx + 6, fy)], fill=1)
		# body, the shoulders a little broad
		d.rectangle([fx - 5, top + 9, fx + 4, top + 21], fill=1)
		# head
		d.rectangle([fx - 4, top + 1, fx + 3, top + 8], fill=1)
		# the left arm thrown out to the side, fist clenched
		d.polygon([(fx - 5, top + 9), (fx - 5, top + 13), (fx - 14, top + 17), (fx - 15, top + 13)], fill=1)
		d.rectangle([fx - 17, top + 13, fx - 14, top + 17], fill=1)
		# the right arm raised high, the sword up and leaning toward the dragon
		d.polygon([(fx + 1, top + 9), (fx + 4, top + 9), (fx + 9, top - 2), (fx + 6, top - 3)], fill=1)
		d.rectangle([fx + 6, top - 5, fx + 9, top - 2], fill=1)                 # the fist
		d.line([(fx + 5, top - 4), (fx + 11, top - 5)], fill=1, width=2)        # the guard
		d.line([(fx + 8, top - 6), (fx + 12, top - 20)], fill=1, width=2)       # the blade
		d.point((fx + 13, top - 21), fill=1)
	m = mask(draw)
	px = img.load()
	# the shadow, long, toward the viewer
	for y in range(fy - 1, N):
		w = 10 - (y - fy) * 0.5
		for x in range(int(fx - w), int(fx + w) + 1):
			r, g, b = px[x, y]
			px[x, y] = (int(r * 0.55), int(g * 0.55), int(b * 0.62))
	paint(img, m, lambda x, y: SILHOUETTE)


def build():
	img = Image.new('RGB', (N, N))
	sky(img)
	tops = [spire(img, *s) for s in SPIRES]
	haze(img)
	for t in tops:
		beam(img, t, CHEST)
	dragon(img)
	ground(img)
	player(img, 63, 126)
	return img


if __name__ == '__main__':
	icon = build()
	os.makedirs(os.path.dirname(OUT), exist_ok=True)
	icon.save(OUT)
	print('wrote', OUT)
	if len(sys.argv) > 1:
		icon.resize((N * 4, N * 4), Image.NEAREST).save(sys.argv[1])
