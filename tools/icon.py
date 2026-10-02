"""Draws the mod icon: pixel art, 128 x 128, no source image.

    python3 tools/icon.py [preview.png]     (the preview is the icon x4, nearest neighbour)

The scene: the End at night. The dragon hangs over the island, wings spread, its head turned down toward a
player who stands on the end stone in the foreground, seen from behind, looking up at it. Spiral obsidian
spires (`arena/Monolith`'s, flat tops) stand round it, each crowned by its End crystal, their healing beams
running to the dragon's chest. A pale violet glow behind the dragon keeps its black hide readable.

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
GLOW = [(56, 30, 80), (74, 42, 102), (96, 58, 128), (122, 80, 154), (150, 108, 180)]
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

DRAGON_GLOW = (70, 36, 30)      # middle and radius of the light behind the dragon
CHEST = (61, 49)                # where the crystal beams meet
HORIZON = 100


def sky(img):
	px = img.load()
	rng = random.Random(7)
	gx, gy, gr = DRAGON_GLOW
	for y in range(N):
		for x in range(N):
			t = y / HORIZON
			c = SKY[dither(0.15 + 0.75 * t, len(SKY), x, y)]
			d = math.hypot((x - gx) / 1.15, y - gy) / gr
			if d < 1.6:
				g = max(0.0, 1 - d / 1.6) ** 2 * 1.1
				i = dither(g, len(GLOW) + 2, x, y) - 2       # the two lowest steps keep the sky
				c = GLOW[i] if i >= 0 else c
			px[x, y] = c
	# stars: single texels, a few with a faint cross
	for _ in range(70):
		x, y = rng.randrange(N), rng.randrange(HORIZON - 6)
		if math.hypot(x - gx, y - gy) < gr * 1.05:
			continue
		b = rng.random()
		px[x, y] = (200, 180, 230) if b > 0.85 else (130, 110, 170) if b > 0.4 else (84, 66, 120)
		if b > 0.93:
			for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
				if 0 <= x + dx < N and 0 <= y + dy < N:
					px[x + dx, y + dy] = (100, 80, 140)


# spires: (x of the middle, top y, half width at the top, half width at the base, twist turns)
SPIRES = [
	(14, 44, 6, 8, 0.55),
	(115, 58, 6, 7, 0.45),
	(95, 70, 4, 5, 0.35),
	(33, 72, 3, 4, 0.3),
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
	"""The island: end stone from a bumpy horizon down, darker toward the bottom edge and in the craters."""
	px = img.load()
	rng = random.Random(3)
	bumps = [HORIZON + round(2 * math.sin(x * 0.11) + 1.2 * math.sin(x * 0.37 + 1)) for x in range(N)]
	for x in range(N):
		for y in range(bumps[x], N):
			t = (y - bumps[x]) / (N - HORIZON)
			light = 0.95 - 0.55 * t + (0.25 if y == bumps[x] else 0)
			# glow from the sky on the far ground
			px[x, y] = STONE[dither(light, len(STONE), x, y)]
	# end stone's speckles
	for _ in range(160):
		x, y = rng.randrange(N), rng.randrange(HORIZON + 2, N)
		if y > bumps[x] + 1:
			r, g, b = px[x, y]
			px[x, y] = (int(r * 0.82), int(g * 0.82), int(b * 0.8))
	return bumps


# ---- the dragon ------------------------------------------------------------------------------------------

def wing(d, shoulder, wrist, tips, root):
	"""A wing membrane: shoulder -> wrist (the arm), then finger tips, back to the root on the flank,
	scalloped between the fingers. Returns the finger lines (wrist -> tip) for the bones."""
	pts = [shoulder, wrist] + tips + [root]
	d.polygon(pts, fill=1)


def scallop(d, a, b, depth):
	"""Cuts an arc into the membrane edge between finger tips a and b."""
	mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
	dx, dy = b[0] - a[0], b[1] - a[1]
	L = math.hypot(dx, dy)
	# the cut's middle, pushed outward (away from the wing) by most of the radius
	nx, ny = dy / L, -dx / L
	r = L / 2
	cx, cy = mx + nx * (r - depth), my + ny * (r - depth)
	d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=0)


FAR_WING = dict(shoulder=(64, 44), wrist=(46, 12), tips=[(22, 6), (14, 18), (16, 32), (30, 40)], root=(58, 50))
NEAR_WING = dict(shoulder=(72, 44), wrist=(92, 6), tips=[(120, 2), (126, 18), (122, 34), (106, 44)], root=(80, 50))


def wing_mask(w, outward):
	def draw(d):
		wing(d, w['shoulder'], w['wrist'], w['tips'], w['root'])
		tips = w['tips'] + [w['root']]
		for a, b in zip(tips, tips[1:]):
			scallop(d, a if outward else b, b if outward else a, 3)
	return mask(draw)


def body_mask():
	def draw(d):
		# torso: chest to hips
		d.polygon([(56, 46), (62, 41), (74, 40), (84, 42), (88, 46), (84, 51), (72, 53), (60, 52)], fill=1)
		# neck: from the chest up and over, the head turned down toward the player
		neck = [(58, 46), (54, 40), (49, 35), (43, 32), (37, 32)]
		for (x0, y0), (x1, y1), r in zip(neck, neck[1:], (4, 3.5, 3, 2.6)):
			for k in range(6):
				t = k / 5
				x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
				d.ellipse([x - r, y - r, x + r, y + r], fill=1)
		# head: skull, the snout pointing down-left, jaw a little open; horns swept back
		d.polygon([(40, 29), (35, 30), (29, 35), (27, 39), (29, 40), (33, 37), (37, 36), (41, 35)], fill=1)
		d.polygon([(32, 38), (28, 42), (30, 43), (35, 39)], fill=1)                     # lower jaw
		d.polygon([(39, 30), (44, 26), (47, 25), (43, 29)], fill=1)                     # horn
		d.polygon([(37, 30), (40, 25), (41, 26), (39, 30)], fill=1)                     # second horn
		# tail: from the hips, sweeping down and right, tapering
		tail = [(86, 47), (93, 53), (100, 57), (108, 58), (115, 55), (120, 50), (123, 46)]
		for i, ((x0, y0), (x1, y1)) in enumerate(zip(tail, tail[1:])):
			r0, r1 = 3.2 - i * 0.45, 3.2 - (i + 1) * 0.45
			for k in range(8):
				t = k / 7
				r = max(0.6, r0 + (r1 - r0) * t)
				x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
				d.ellipse([x - r, y - r, x + r, y + r], fill=1)
		# the tail's spade
		d.polygon([(121, 47), (126, 41), (125, 47), (123, 49)], fill=1)
		# legs: the front pair tucked, the hind pair hanging, toes curled
		d.polygon([(60, 50), (63, 51), (62, 56), (60, 59), (58, 58), (59, 55)], fill=1)
		d.polygon([(78, 50), (84, 50), (83, 56), (81, 61), (78, 61), (79, 56)], fill=1)
		d.polygon([(77, 61), (80, 63), (82, 61)], fill=1)
		d.polygon([(57, 58), (59, 61), (61, 58)], fill=1)
		# spines along the back
		for x, y in ((66, 40), (71, 39), (76, 39), (81, 40), (52, 37), (47, 33)):
			d.polygon([(x - 1, y + 1), (x + 1, y - 3), (x + 2, y + 1)], fill=1)
	return mask(draw)


def wing_bones(d, w):
	d.line([w['shoulder'], w['wrist']], fill=1, width=2)
	for t in w['tips'][:-1]:
		d.line([w['wrist'], t], fill=1, width=1)
	d.line([w['wrist'], w['tips'][-1]], fill=1, width=1)


def dragon(img):
	gx, gy, _ = DRAGON_GLOW
	far, near, body = wing_mask(FAR_WING, False), wing_mask(NEAR_WING, True), body_mask()

	def membrane(base, rim_m):
		def shade(x, y):
			# the light behind shines through the membrane: lighter toward the glow's middle
			d = math.hypot(x - gx, y - gy) / 40
			v = base + 0.5 * max(0.0, 1 - d)
			if edge(rim_m, x, y, 0, -1) or edge(rim_m, x, y, -1, 0) or edge(rim_m, x, y, 1, 0):
				v += 0.25
			return HIDE[dither(v, len(HIDE), x, y)]
		return shade

	paint(img, far, membrane(0.24, far))
	paint(img, mask(lambda d: wing_bones(d, FAR_WING)), lambda x, y: HIDE[1])
	paint(img, body, lambda x, y: HIDE[4] if edge(body, x, y, 0, -1) or edge(body, x, y, 1, -1) else
		HIDE[2] if edge(body, x, y, 0, 1) else HIDE[1])
	paint(img, near, membrane(0.36, near))
	paint(img, mask(lambda d: wing_bones(d, NEAR_WING)), lambda x, y: HIDE[2])
	# the shoulder over the near wing's root
	paint(img, mask(lambda d: d.ellipse([68, 40, 77, 49], fill=1)), lambda x, y: HIDE[1])
	# the eye and the glow round it
	px = img.load()
	px[35, 32], px[36, 32] = EYE[1], EYE[2]
	px[34, 32], px[37, 33] = EYE[0], EYE[0]


# ---- the player ------------------------------------------------------------------------------------------

def player(img, fx, fy):
	"""Steve seen from behind, feet at (fx, fy): 24 px tall, head tilted back to look up, sword in hand."""
	px = img.load()
	HAIR = [(44, 28, 14), (61, 40, 22), (82, 56, 30)]
	SHIRT = [(0, 96, 100), (0, 140, 145), (0, 175, 178)]
	PANTS = [(36, 34, 100), (52, 50, 130), (70, 68, 160)]
	SKIN = [(150, 100, 70), (190, 130, 95)]
	SHOE = [(50, 50, 50), (80, 80, 80)]
	RIM = (190, 150, 230)

	def rect(x0, y0, w, h, ramp, lit_left=True):
		for y in range(y0, y0 + h):
			for x in range(x0, x0 + w):
				u = (x - x0) / max(1, w - 1)
				v = 2 if (u < 0.34) == lit_left and h > 2 else 1
				if y == y0 + h - 1 or (x == x0 + w - 1 if lit_left else x == x0):
					v = 0
				px[x, y] = ramp[min(v, len(ramp) - 1)]

	# shadow on the ground
	for x in range(fx - 9, fx + 10):
		for y in (fy, fy + 1):
			if abs(x - fx) < 9 - (y - fy) * 2:
				r, g, b = px[x, y]
				px[x, y] = (int(r * 0.6), int(g * 0.6), int(b * 0.65))
	top = fy - 24
	# legs (8 tall: pants, shoes at the bottom)
	rect(fx - 4, top + 15, 4, 8, PANTS)
	rect(fx, top + 15, 4, 8, PANTS)
	for x in range(fx - 4, fx + 4):
		px[x, top + 22] = SHOE[0] if x in (fx - 1, fx + 3) else SHOE[1]
		px[x, top + 23] = SHOE[0]
	# body
	rect(fx - 4, top + 7, 8, 8, SHIRT)
	# arms: the left hangs, the right holds the sword down at its side
	rect(fx - 7, top + 7, 3, 8, SHIRT)
	rect(fx + 4, top + 7, 3, 8, SHIRT, lit_left=False)
	for x in (fx - 7, fx - 6, fx - 5):
		px[x, top + 15] = SKIN[0]
	for x in (fx + 4, fx + 5, fx + 6):
		px[x, top + 15] = SKIN[0]
	# the sword (diamond), point down past the right hand
	px[fx + 5, top + 14] = (60, 40, 20)
	for y in range(top + 16, top + 23):
		px[fx + 6, y] = (120, 230, 220) if y < top + 20 else (70, 180, 175)
		px[fx + 7, y] = (40, 120, 130)
	px[fx + 4, top + 16], px[fx + 7, top + 16] = (40, 40, 40), (40, 40, 40)
	# head, tilted back (looking up): the back of the head, seen a little from below -> 7 tall
	rect(fx - 4, top, 8, 7, HAIR)
	px[fx - 4, top + 6], px[fx + 3, top + 6] = SKIN[1], SKIN[0]          # ears / jaw at the sides
	# rim light from the dragon's glow on the top edges
	for x in range(fx - 4, fx + 4):
		px[x, top] = RIM if x < fx + 2 else HAIR[2]
	px[fx - 7, top + 7], px[fx - 6, top + 7] = RIM, (150, 200, 210)
	px[fx + 4, top + 7] = (150, 200, 210)


def build():
	img = Image.new('RGB', (N, N))
	sky(img)
	tops = [spire(img, *s) for s in SPIRES]
	for t in tops:
		beam(img, t, CHEST)
	dragon(img)
	ground(img)
	player(img, 46, 122)
	return img


if __name__ == '__main__':
	icon = build()
	os.makedirs(os.path.dirname(OUT), exist_ok=True)
	icon.save(OUT)
	print('wrote', OUT)
	if len(sys.argv) > 1:
		icon.resize((N * 4, N * 4), Image.NEAREST).save(sys.argv[1])
