"""Builds the dragon egg: block model, blockstate and a texture per face, for each hatch stage.

    python3 tools/egg.py [preview.png]     (build_assets.py runs it too)

The model is the sniffer egg's (one box, its own texture on each face), made as deep as it is wide: 14 x 16 x 14,
vanilla's dragon egg shape (its outline). The block gets a `hatch` property 0..2 (`DragonEggBlockMixin`, vanilla's
`BlockStateProperties.HATCH`, as the sniffer egg's) and the blockstate picks a model per stage, sniffer-style:

* `dragon_egg` (hatch 0)            -- textures `dragon_egg_not_cracked_<face>`
* `dragon_egg_slightly_cracked` (1) -- `dragon_egg_slightly_cracked_<face>`
* `dragon_egg_very_cracked` (2)     -- `dragon_egg_very_cracked_<face>`

Nothing raises the stage yet (the hatching comes later), so every egg shows hatch 0.

The art: overlapping shell scales in the dragon texture's palette, laid out on the egg as a whole: rows of
scales (`BANDS`) from the top tip down round the sides to the bottom tip, small at the top and growing toward
the bottom. Each scale is a seed point on the box (`SEEDS`; on the caps a row is a square ring round the middle,
each ring turned a little further, `TWIST`, so the rows wind into the tips in a spiral) and a texel shows its
nearest seed, measured in 3D, so the scales run on across the faces' edges. The hatch stages crack the shell:
thin dark cracks, then open ones with chipped, lit edges.
"""
import json
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(os.path.dirname(HERE), 'src', 'mc', 'shared', 'resources', 'assets', 'minecraft')
STAGES = ['not_cracked', 'slightly_cracked', 'very_cracked']
SIDES = ['north', 'west', 'south', 'east']   # round the egg in the direction the faces' u runs
W, H = 14, 16      # the box: 14 wide and deep, 16 tall
RINGS = W // 2     # a cap's square rings, its middle 2x2 to its edge
LATS = 2 * RINGS + H

# the dragon texture's palette (tools/out/ender_dragon.png), darkest to lightest
HIDE = [(2, 0, 2), (8, 7, 14), (21, 17, 21), (32, 24, 32), (41, 33, 45), (49, 39, 53), (74, 63, 71), (90, 79, 87)]

# the rows of scales from the top tip down: (rows of lat, scales round the egg); the top cap is lat 0..6, the
# sides 7..22, the bottom cap 23..29. The sides' rows divide the 56 texels round them evenly (scales 4, 7, 8 wide).
BANDS = [(3, 5), (4, 11), (3, 14), (3, 14), (3, 8), (4, 8), (3, 7), (4, 6), (3, 3)]
assert sum(h for h, _ in BANDS) == LATS
TWIST = 0.04       # turns per cap ring, so the rows spiral into the tips
# some scales a shade darker, so the rows do not read as a grid
VARY = [0, 1, 0, 0, 1, 0, 1, 0, 0]

# the cracks, as polylines: on the sides (s round the 56-texel strip, d down from the top edge, both in texels,
# texel centers at +0.5; s past 56 wraps), or on the top cap (x east, z south, 0..14 like the box). Each has its
# look per stage: None (not there yet), 1 (a thin dark crack), 2 (open: black, its edges chipped and lit).
CRACKS = [
	# down the north face from the shoulder, forking over the belly
	('side', [(9.5, 0.5), (9.5, 3.5), (8.5, 5.5), (7.5, 7.5), (6.5, 8.5)], (None, 1, 2)),
	('side', [(6.5, 8.5), (5.5, 10.5), (3.5, 12.5)], (None, None, 2)),
	('side', [(7.5, 7.5), (9.5, 8.5), (10.5, 9.5)], (None, None, 1)),
	('top', [(5.5, 0.5), (6.5, 2.5), (7.0, 5.0)], (None, 1, 2)),
	# the south face, up to the shoulder
	('side', [(33.5, 9.5), (35.5, 6.5), (35.5, 4.5), (37.5, 1.5), (38.5, 0.5)], (None, 1, 2)),
	('side', [(33.5, 13.5), (34.5, 11.5), (33.5, 9.5)], (None, None, 2)),
	('top', [(10.5, 13.5), (9.5, 11.5), (7.0, 9.0), (7.0, 5.0)], (None, 1, 2)),
	# the east face
	('side', [(48.5, 12.5), (47.5, 10.5), (49.5, 8.5), (50.5, 5.5), (52.5, 3.5)], (None, None, 2)),
	('side', [(47.5, 10.5), (45.5, 9.5)], (None, None, 1)),
	# the west face, the shell starring round the tip
	('side', [(20.5, 8.5), (22.5, 5.5), (21.5, 2.5)], (None, 1, 2)),
	('side', [(21.5, 11.5), (20.5, 8.5)], (None, None, 2)),
	('side', [(22.5, 5.5), (25.5, 4.5)], (None, None, 1)),
	('top', [(0.5, 6.5), (3.5, 6.5), (7.0, 5.0), (9.5, 3.5), (12.5, 3.5)], (None, None, 2)),
]


def cap_ring_point(f, h):
	"""The point a fraction f round the square ring of half-size h about a cap's middle (dx, dz), from the
	north-east corner westward, as the side faces' u runs."""
	t = (f % 1.0) * 8 * h
	if t < 2 * h:
		return h - t, -h                 # north edge, running west
	if t < 4 * h:
		return -h, t - 3 * h             # west edge, running south
	if t < 6 * h:
		return t - 5 * h, h              # south edge, running east
	return h, 7 * h - t                  # east edge, running north


def place(f, lat):
	"""The point on the box at (lon f, continuous lat)."""
	if lat < RINGS:
		dx, dz = cap_ring_point(f, lat)
		return 8 + dx, H, 8 + dz
	if lat > RINGS + H:
		dx, dz = cap_ring_point(f, LATS - lat)
		return 8 + dx, 0, 8 + dz
	dx, dz = cap_ring_point(f, RINGS)
	return 8 + dx, H - (lat - RINGS), 8 + dz


def twist(lat):
	"""How far a cap ring is turned, so the rows wind into the tips."""
	if lat < RINGS:
		return TWIST * (RINGS - lat)
	if lat > RINGS + H:
		return -TWIST * (lat - RINGS - H)
	return 0.0


def make_seeds():
	seeds, top = [], 0
	for band, (rows, count) in enumerate(BANDS):
		mid = top + rows / 2
		for k in range(count):
			f = (k + 0.5 + (0.5 if band % 2 else 0.0)) / count + twist(mid)
			seeds.append((place(f, mid), band, k))
		top += rows
	return seeds


SEEDS = make_seeds()


def texel_frame(face, i, j):
	"""A face texel's center on the box, its lat, and the surface's right (the way lon runs) and down (toward
	the bottom tip) there."""
	if face in SIDES:
		right = {'north': (-1, 0, 0), 'west': (0, 0, 1), 'south': (1, 0, 0), 'east': (0, 0, -1)}[face]
		x, _, z = place((SIDES.index(face) * W + i + 0.5) / (4 * W), RINGS)
		return (x, H - j - 0.5, z), RINGS + j + 0.5, right, (0, -1, 0)
	x = 1 + i + 0.5
	z = 1 + (j if face == 'top' else W - 1 - j) + 0.5   # the down face's v runs north from the south edge
	dx, dz = x - 8, z - 8
	n = math.hypot(dx, dz)
	out = (dx / n, 0, dz / n)
	right = (out[2], 0, -out[0])
	ring = max(abs(dx), abs(dz))
	if face == 'top':
		return (x, H, z), ring, right, out
	return (x, 0, z), LATS - ring, right, (-out[0], 0, -out[2])


def dot(a, b):
	return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def scale_shade(face, i, j):
	"""The shell's shade at a face texel: the scale it shows is the nearest seed (in 3D, so scales run on across
	the faces' edges). Each is rimmed dark along its lower edge (where it lies over the next row), shadowed
	under the row above it, a dark seam on its left; its body lit high and right of its middle."""
	p, lat, right, down = texel_frame(face, i, j)
	near = sorted((math.dist(p, sp), band, k, sp) for sp, band, k in SEEDS)[:2]
	(d1, band, k, sp), (d2, *_) = near
	rows, count = BANDS[band]
	h = lat if lat < RINGS else LATS - lat if lat > RINGS + H else RINGS
	width = 8 * h / count
	o = (p[0] - sp[0], p[1] - sp[1], p[2] - sp[2])
	ex, ey = dot(o, right) / (width / 2), dot(o, down) / (rows / 2)
	if d2 - d1 < 0.7:
		shade = 1 if ey > 0.25 else 2 if ey < -0.25 or ex < 0 else 5
	elif 0 < ex < 0.65 and -0.7 < ey < 0.15:
		shade = 7
	else:
		shade = 6 if ey < 0.35 else 5
	if shade > 2:
		shade -= VARY[(band * 5 + k) % len(VARY)]
	if lat > RINGS + H:
		shade -= 1          # the underside in shade
	elif lat > RINGS + 12:
		shade -= 1
	return max(0, min(len(HIDE) - 1, shade))


def seg_dist(px, pz, ax, az, bx, bz):
	"""Distance from (px, pz) to the segment a-b."""
	dx, dz = bx - ax, bz - az
	qx, qz = px - ax, pz - az
	t = 0.0 if dx == dz == 0 else max(0.0, min(1.0, (qx * dx + qz * dz) / (dx * dx + dz * dz)))
	return math.hypot(qx - t * dx, qz - t * dz)


def crack(stage, kind, px, pz):
	"""(core, edge): the worst crack through a point, and whether an open one runs beside it."""
	core, edge = None, False
	for k, pts, look in CRACKS:
		level = look[stage]
		if k != kind or level is None:
			continue
		for wrap in ((-4 * W, 0, 4 * W) if kind == 'side' else (0,)):
			for (ax, az), (bx, bz) in zip(pts, pts[1:]):
				dist = seg_dist(px, pz, ax + wrap, az, bx + wrap, bz)
				if dist < 0.5:
					core = max(core or 0, level)
				elif dist < 1.2 and level >= 2:
					edge = True
	return core, edge


def texel(stage, face, i, j):
	shade = scale_shade(face, i, j)
	if face in SIDES:
		core, edge = crack(stage, 'side', SIDES.index(face) * W + i + 0.5, j + 0.5)
	elif face == 'top':
		core, edge = crack(stage, 'top', i + 0.5, j + 0.5)
	else:
		core, edge = None, False
	if core is not None:
		return HIDE[0] if core >= 2 else HIDE[1]
	if edge:   # the open crack's chipped rim catches the light
		shade = min(len(HIDE) - 1, shade + 2)
	return HIDE[shade]


def face_texture(stage, face):
	img = Image.new('RGBA', (16, 16))
	for j in range(H if face in SIDES else W):
		for i in range(W):
			img.putpixel((i, j), (*texel(stage, face, i, j), 255))
	return img


def textures(stage):
	return {face: face_texture(stage, face) for face in SIDES + ['top', 'bottom']}


# the item: the sniffer egg item's tilted egg (its long axis from the upper left down to the lower right, the
# narrow end up), in the block's scales, lit from above, rimmed a shade darker
ITEM_AXIS = (math.sqrt(0.5), math.sqrt(0.5))   # toward the blunt end
ITEM_CENTER = (7.7, 7.9)
ITEM_LENGTH, ITEM_WIDTH = 8.3, 5.6             # half axes
CELL_W, CELL_H = 4, 3
LOBE = [
	[1, 5, 6, 4],
	[2, 4, 5, 3],
	[1, 2, 3, 1],
]


def item_inside(x, y):
	"""Where (x, y) lies in the egg: along its axis and across, -1..1 each inside, else None."""
	px, py = x - ITEM_CENTER[0], y - ITEM_CENTER[1]
	a = (px * ITEM_AXIS[0] + py * ITEM_AXIS[1]) / ITEM_LENGTH
	b = -px * ITEM_AXIS[1] + py * ITEM_AXIS[0]
	if abs(a) >= 1:
		return None
	half = ITEM_WIDTH * math.sqrt(1 - a * a) * (1 + 0.12 * a)   # wider at the blunt end
	return (a, b / half) if abs(b) < half else None


def item_texture():
	img = Image.new('RGBA', (16, 16))
	for y in range(16):
		for x in range(16):
			at = item_inside(x + 0.5, y + 0.5)
			if at is None:
				continue
			a, b = at
			row = y // CELL_H
			col = (x + (CELL_W // 2 if row % 2 else 0)) % CELL_W
			shade = LOBE[y % CELL_H][col] - VARY[(row * 5 + (x + 2 * (row % 2)) // CELL_W) % len(VARY)]
			# lit from above: the upper right flank (b < 0) and the narrow end brighter, the lower left in shade
			shade += round(-1.8 * b - 0.7 * a + 0.6)
			rim = any(item_inside(x + 0.5 + dx, y + 0.5 + dy) is None for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
			if rim:
				shade = 1 if b > -0.3 else 2
			shade = max(0, min(len(HIDE) - 1, shade))
			img.putpixel((x, y), (*HIDE[shade], 255))
	return img


def model_name(stage):
	return 'dragon_egg' if stage == 0 else f'dragon_egg_{STAGES[stage]}'


def model(stage):
	textures = {face: f'minecraft:block/dragon_egg_{STAGES[stage]}_{face}' for face in SIDES + ['top', 'bottom']}
	if stage:
		return {'parent': 'minecraft:block/dragon_egg', 'textures': textures}
	side = {'uv': [0, 0, W, H]}
	cap = {'uv': [0, 0, W, W]}
	return {
		'parent': 'minecraft:block/block',
		'textures': {'particle': '#north', **textures},
		'elements': [{
			'from': [1, 0, 1],
			'to': [15, 16, 15],
			'faces': {
				'north': {**side, 'texture': '#north'},
				'east': {**side, 'texture': '#east'},
				'south': {**side, 'texture': '#south'},
				'west': {**side, 'texture': '#west'},
				'up': {**cap, 'texture': '#top', 'cullface': 'up'},
				'down': {**cap, 'texture': '#bottom', 'cullface': 'down'},
			},
		}],
	}


def write_json(path, data):
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, 'w') as f:
		json.dump(data, f, indent='\t')
		f.write('\n')
	print('  ->', os.path.relpath(path, os.path.dirname(HERE)))


def preview(path):
	"""Every stage as two nets, 12x: the top with the sides folded down round it (seen from above), the bottom
	with the sides folded up round it (seen from below); then the item."""
	z = 12
	net = W + 2 * H + 2
	out = Image.new('RGBA', ((2 * net + 18) * z, 3 * net * z), (40, 40, 48, 255))

	def put(img, w, h, turn, x, y):
		img = img.crop((0, 0, w, h)).rotate(turn, expand=True)
		img = img.resize((img.width * z, img.height * z), Image.NEAREST)
		out.alpha_composite(img, (x * z, y * z))

	for stage in range(len(STAGES)):
		tex = textures(stage)
		y0 = stage * net
		put(tex['top'], W, W, 0, H, y0 + H)
		put(tex['north'], W, H, 180, H, y0)
		put(tex['south'], W, H, 0, H, y0 + H + W)
		put(tex['west'], W, H, -90, 0, y0 + H)
		put(tex['east'], W, H, 90, H + W, y0 + H)
		x0 = net
		put(tex['bottom'], W, W, 0, x0 + H, y0 + H)
		put(tex['south'], W, H, 0, x0 + H, y0)
		put(tex['north'], W, H, 180, x0 + H, y0 + H + W)
		put(tex['west'], W, H, 90, x0, y0 + H)
		put(tex['east'], W, H, -90, x0 + H + W, y0 + H)
	put(item_texture(), 16, 16, 0, 2 * net + 1, 0)
	out.save(path)


def build():
	for stage, name in enumerate(STAGES):
		for face, img in textures(stage).items():
			path = os.path.join(RES, 'textures', 'block', f'dragon_egg_{name}_{face}.png')
			os.makedirs(os.path.dirname(path), exist_ok=True)
			img.save(path)
		print('  ->', os.path.relpath(os.path.join(RES, 'textures', 'block', f'dragon_egg_{name}_*.png'), os.path.dirname(HERE)))
		write_json(os.path.join(RES, 'models', 'block', model_name(stage) + '.json'), model(stage))
	write_json(os.path.join(RES, 'blockstates', 'dragon_egg.json'),
			{'variants': {f'hatch={k}': {'model': 'minecraft:block/' + model_name(k)} for k in range(len(STAGES))}})
	path = os.path.join(RES, 'textures', 'item', 'dragon_egg.png')
	os.makedirs(os.path.dirname(path), exist_ok=True)
	item_texture().save(path)
	print('  ->', os.path.relpath(path, os.path.dirname(HERE)))
	write_json(os.path.join(RES, 'models', 'item', 'dragon_egg.json'),
			{'parent': 'minecraft:item/generated', 'textures': {'layer0': 'minecraft:item/dragon_egg'}})
	# 1.21.4+: the item definition (vanilla's points at the block model)
	write_json(os.path.join(RES, 'items', 'dragon_egg.json'), {'model': {'type': 'minecraft:model', 'model': 'minecraft:item/dragon_egg'}})
	old = os.path.join(RES, 'textures', 'block', 'dragon_egg.png')   # the one texture of vanilla's layered model
	if os.path.exists(old):
		os.remove(old)


if __name__ == '__main__':
	if len(sys.argv) > 1:
		preview(sys.argv[1])
	else:
		build()
