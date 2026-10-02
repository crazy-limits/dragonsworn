"""Lays the dragon's texture out as box UV: every cube wears its own unfolded net, grouped by body part.

    python3 tools/pack_uv.py      (build_assets.py runs it, after heat.py)

Reads out/ender_dragon.geo.json and the textures drawn on its layout (the skin, the glowmask and the heat
frames, all treated as one stack of channels), and writes them back laid out for editing:

* every cube gets box UV (Blockbench's "Box UV": one `uv` offset, the faces unfolded round the top face);
  no per-face UV is left;
* the right wing wears the left wing's nets through the `mirror` flag (its cubes are the left's mirror image
  across x = 0), so the wing is painted once;
* cubes that would wear identical nets share one (the toes, the fingers, the horns), also mirrored;
* the nets are packed by body part (body, neck, head, legs and feet, tail, wing), each part in its own block,
  GAP texels apart, and the blocks into the smallest power-of-two texture they fit in.

A net face as big as the face's old texels gets exactly those texels; GeckoLib sizes a box-UV face by the
cube's size rounded down, so a face of fractional size (the tail's split segments) is resampled (nearest)
onto it. `verify` checks every other face texel for texel against the GeckoLib quad builder's UVs
(BakedModelFactory / GeoQuad).
"""
import glob
import itertools
import json
import math
import os

import numpy as np
from PIL import Image

from rig import Rig, apply

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'out')
GEO = os.path.join(OUT, 'ender_dragon.geo.json')
TEXTURES = ['ender_dragon.png', 'ender_dragon_glowmask.png']   # + heat/ender_dragon_heat_*.png
FACES = ['north', 'east', 'south', 'west', 'up', 'down']
MIRROR_FACE = {'east': 'west', 'west': 'east'}     # what a face is across x = 0
GAP, PART_GAP = 2, 4                               # clear texels between nets, between body parts


def texture_files():
	return TEXTURES + sorted(os.path.relpath(p, OUT) for p in glob.glob(os.path.join(OUT, 'heat', 'ender_dragon_heat_*.png')))


# ---- GeckoLib's quads -------------------------------------------------------------------------------

def quads(cube):
	"""Editor-space vertices of each face, in GeckoLib 4/5's VertexSet order (not mirrored)."""
	(ox, oy, oz), (sx, sy, sz) = cube['origin'], cube['size']
	inf = cube.get('inflate', 0)
	x0, x1 = -(ox + sx) - inf, -ox + inf
	y0, y1, z0, z1 = oy - inf, oy + sy + inf, oz - inf, oz + sz + inf
	bl_b, br_b, tl_b, tr_b = (x0, y0, z0), (x0, y0, z1), (x0, y1, z0), (x0, y1, z1)
	tl_f, tr_f, bl_f, br_f = (x1, y1, z0), (x1, y1, z1), (x1, y0, z0), (x1, y0, z1)
	return {
		'west': [tr_b, tl_b, bl_b, br_b], 'east': [tl_f, tr_f, br_f, bl_f],
		'north': [tl_b, tl_f, bl_f, bl_b], 'south': [tr_f, tr_b, br_b, br_f],
		'up': [tr_b, tr_f, tl_f, tl_b], 'down': [bl_b, bl_f, br_f, br_b],
	}


# Which of (A, B) / (C, D) each vertex gets for uv_rotation 0, 90, 180, 270 (FaceUV.Rotation.rotateUvs,
# called with A = u + us, B = v, C = u, D = v + vs once GeoQuad.build has swapped u for an unmirrored cube).
ROTATIONS = [
	[('A', 'B'), ('C', 'B'), ('C', 'D'), ('A', 'D')],
	[('C', 'B'), ('C', 'D'), ('A', 'D'), ('A', 'B')],
	[('C', 'D'), ('A', 'D'), ('A', 'B'), ('C', 'B')],
	[('A', 'D'), ('A', 'B'), ('C', 'B'), ('C', 'D')],
]


def face_params(cube):
	"""{face: (uv, uv_size, rotation)} as GeckoLib reads them (box UV unfolded like BakedModelFactory)."""
	uv = cube['uv']
	if isinstance(uv, dict):
		return {f: (d['uv'], d['uv_size'], d.get('uv_rotation', 0) // 90 % 4) for f, d in uv.items()}
	u, v = uv
	x, y, z = (math.floor(s) for s in cube['size'])
	return {
		'west': ((u + z + x, v + z), (z, y), 0), 'east': ((u, v + z), (z, y), 0),
		'north': ((u + z, v + z), (x, y), 0), 'south': ((u + z + x + z, v + z), (x, y), 0),
		'up': ((u + z, v), (x, z), 0), 'down': ((u + z + x, v + z), (x, -z), 0),
	}


def corners(params, mirror=False):
	"""The texture point at each of the face's four vertices (a mirrored cube's u runs the other way:
	GeoQuad.build skips its swap)."""
	(u, v), (us, vs), rot = params
	val = {'A': u + us, 'B': v, 'C': u, 'D': v + vs}
	if mirror:
		val['A'], val['C'] = val['C'], val['A']
	return [(val[a], val[b]) for a, b in ROTATIONS[rot]]


def drawn(cube):
	"""{face drawn: texture corners} as GeckoLib builds the cube: a mirrored cube's east net goes on its west
	face and back (BakedModelFactory.VertexSet.verticesForQuad), with u reversed."""
	mirror = bool(cube.get('mirror'))
	box = not isinstance(cube['uv'], dict)
	out = {}
	for f, p in face_params(cube).items():
		g = f
		if mirror:
			g = {'west': 'east', 'east': 'west'}.get(f, f)
			if not box:
				g = {'up': 'down', 'down': 'up'}.get(g, g)
		out[g] = corners(p, mirror)
	return out


def has_area(quad):
	a, b, d = (np.array(p, float) for p in (quad[0], quad[1], quad[3]))
	return np.linalg.norm(b - a) > 1e-9 and np.linalg.norm(d - a) > 1e-9


# ---- the mirrored membranes -------------------------------------------------------------------------

def mirror_wing(bones, faces):
	"""Points every face of the right wing at the left face it mirrors across x = 0 (build_wings.py makes
	the right wing the left's exact mirror): vertex k samples what the left vertex at its mirror image does."""
	mats = Rig(GEO).matrices({})
	by = {b['name']: b for b in bones}
	for right in bones:
		if not right['name'].startswith('right_wing'):
			continue
		left = by['left' + right['name'][5:]]
		for ci, (rc, lc) in enumerate(zip(right.get('cubes', []), left.get('cubes', []))):
			lq, rq = quads(lc), quads(rc)
			mirrored = {f: np.array([[-p[0], p[1], p[2]] for p in (apply(mats[left['name']], p) for p in lq[f])]) for f in lq}
			for f in rq:
				if not faces[(right['name'], ci, f)]['area']:
					continue
				rw = np.array([apply(mats[right['name']], p) for p in rq[f]])
				lf = MIRROR_FACE.get(f, f)     # by name: a flat cube's two faces share one plane
				lw = mirrored[lf]
				pick = [int(np.argmin(np.linalg.norm(lw - p, axis=1))) for p in rw]
				assert sorted(pick) == [0, 1, 2, 3] and max(np.linalg.norm(lw[i] - p) for i, p in zip(pick, rw)) < 1e-3, (right['name'], f)
				lcs = faces[(left['name'], ci, lf)]['corners']
				faces[(right['name'], ci, f)]['corners'] = [lcs[i] for i in pick]


def sample(cs, tex, s, t):
	"""The texels at face points (s, t) (0-1 across it), as GeckoLib maps the face's corners: vertices
	1, 0, 3, 2 sit at the face's (0,0), (1,0), (1,1), (0,1)."""
	cs = np.array(cs, float)
	pts = (1 - s) * (1 - t) * cs[1] + s * (1 - t) * cs[0] + s * t * cs[3] + (1 - s) * t * cs[2]
	u, v = np.floor(pts[:, 0]).astype(int), np.floor(pts[:, 1]).astype(int)
	inside = (u >= 0) & (v >= 0) & (u < tex.shape[1]) & (v < tex.shape[0])
	out = np.zeros((len(u), tex.shape[2]), tex.dtype)
	out[inside] = tex[v[inside], u[inside]]
	return out


def grid(cs):
	"""Sample points spread over a face (4 per texel), never on a texel boundary."""
	cs = np.array(cs, float)
	n = 4 * int(max(np.abs(cs[1] - cs[0]).max(), np.abs(cs[3] - cs[0]).max(), 1)) + 1
	s, t = np.meshgrid((np.arange(n) + 0.5 + 1e-3 * math.pi) / n, (np.arange(n) + 0.5 + 1e-3 * math.e) / n)
	return s.ravel()[:, None], t.ravel()[:, None]


def rect(cs):
	"""The texel rectangle (u0, v0, u1, v1) a face's corners span."""
	us, vs = zip(*cs)
	return math.floor(min(us)), math.floor(min(vs)), math.ceil(max(us)), math.ceil(max(vs))


# ---- nets -------------------------------------------------------------------------------------------

def net(cube, mirror, look, stack):
	"""The box-UV net (offset 0, 0) on which the cube, mirrored or not, shows `look` ({face: corners in
	`stack`}), and its footprint: the rectangle round every face it draws."""
	x, y, z = (math.floor(s) for s in cube['size'])
	arr = np.zeros((z + y, 2 * (z + x), stack.shape[2]), np.uint8)
	box = {**cube, 'uv': [0, 0], 'mirror': mirror}
	foot = None
	for f, cs in drawn(box).items():
		if f not in look:
			continue
		u0, v0, u1, v1 = rect(cs)
		if u1 <= u0 or v1 <= v0:
			continue
		foot = (u0, v0, u1, v1) if foot is None else (min(foot[0], u0), min(foot[1], v0), max(foot[2], u1), max(foot[3], v1))
		cs = np.array(cs, float)
		uu, vv = np.meshgrid(np.arange(u0, u1) + 0.5, np.arange(v0, v1) + 0.5)
		st = np.linalg.solve(np.array([cs[0] - cs[1], cs[2] - cs[1]]).T, np.stack([uu.ravel(), vv.ravel()]) - cs[1][:, None])
		arr[v0:v1, u0:u1] = sample(look[f], stack, st[0][:, None], st[1][:, None]).reshape(v1 - v0, u1 - u0, -1)
	return arr, foot


def part(bone):
	"""Which block of the texture a bone's nets go in."""
	for prefix, name in (('left_wing', 'wing'), ('right_wing', 'wing'), ('neck', 'neck'), ('tail', 'tail'),
						 ('upperleg', 'legs'), ('lowerleg', 'legs'), ('foot', 'legs'), ('body', 'body')):
		if bone.startswith(prefix):
			return name
	assert bone.startswith(('jaw', 'eyebrow', 'horn')), bone
	return 'head'


PARTS = ['body', 'neck', 'head', 'legs', 'tail', 'wing']


def maxrects(sizes, width, height, mins=None):
	"""Places (w, h) rectangles (biggest first, best short side fit, never turned: a net cannot be), item i no
	nearer the top left than mins[i]; None if they do not fit."""
	free = [(0, 0, width, height)]
	placed = {}
	for i in sorted(range(len(sizes)), key=lambda i: (-max(sizes[i]), -min(sizes[i]))):
		rw, rh = sizes[i]
		mx, my = mins[i] if mins else (0, 0)
		best = None
		for fx, fy, fw, fh in free:
			x, y = max(fx, mx), max(fy, my)
			if x + rw <= fx + fw and y + rh <= fy + fh:
				score = (y, min(fx + fw - x - rw, fy + fh - y - rh), x)
				if best is None or score < best[0]:
					best = (score, x, y)
		if best is None:
			return None
		_, x, y = best
		placed[i] = (x, y)
		split = []
		for fx, fy, fw, fh in free:
			if x >= fx + fw or x + rw <= fx or y >= fy + fh or y + rh <= fy:
				split.append((fx, fy, fw, fh))
				continue
			if x > fx: split.append((fx, fy, x - fx, fh))
			if x + rw < fx + fw: split.append((x + rw, fy, fx + fw - x - rw, fh))
			if y > fy: split.append((fx, fy, fw, y - fy))
			if y + rh < fy + fh: split.append((fx, y + rh, fw, fy + fh - y - rh))
		free = [r for r in split if not any(o != r and o[0] <= r[0] and o[1] <= r[1] and o[0] + o[2] >= r[0] + r[2]
											and o[1] + o[3] >= r[1] + r[3] for o in split)]
		free = list(dict.fromkeys(free))
	return placed


def shapes(sizes, gap, keep=6):
	"""The ways to pack rectangles gap apart into a block: for each width the least height, only those no other
	beats on both, the keep smallest by area: [(width, height, places)]."""
	found = []
	padded = [(w + gap, h + gap) for w, h in sizes]
	widest, total = max(w for w, _ in sizes), sum(w + gap for w, _ in sizes)
	for width in range(widest, total + 1):
		lo, hi = max(h for _, h in sizes), sum(h + gap for _, h in sizes)
		if found and found[-1][1] == lo:
			break                       # as low as it gets: wider only wastes
		while lo < hi:
			mid = (lo + hi) // 2
			if maxrects(padded, width + gap, mid + gap):
				hi = mid
			else:
				lo = mid + 1
		if not found or lo < found[-1][1]:
			found.append((width, lo))
	found = sorted(found, key=lambda s: s[0] * s[1])[:keep]
	return [(w, h, maxrects(padded, w + gap, h + gap)) for w, h in found]


def pack():
	geo = json.load(open(GEO))
	g = geo['minecraft:geometry'][0]
	files = texture_files()
	stack = np.concatenate([np.array(Image.open(os.path.join(OUT, f)).convert('RGBA')) for f in files], axis=2)
	H, W = stack.shape[:2]
	assert (g['description']['texture_width'], g['description']['texture_height']) == (W, H)

	faces = {}
	for b in g['bones']:
		for ci, c in enumerate(b.get('cubes', [])):
			qs = quads(c)
			for f, cs in drawn(c).items():
				faces[(b['name'], ci, f)] = {'corners': cs, 'area': has_area(qs[f])}
	mirror_wing(g['bones'], faces)

	# every cube's net, shared by cubes that would wear the same one (mirrored or not); the left wing before the right
	nets, keys, wears = [], {}, {}
	cubes = [(b, ci, c) for b in g['bones'] for ci, c in enumerate(b.get('cubes', []))]
	cubes.sort(key=lambda bc: bc[0]['name'].startswith('right_wing'))
	for b, ci, c in cubes:
		look = {f: faces[(b['name'], ci, f)]['corners'] for f in FACES
				if (b['name'], ci, f) in faces and faces[(b['name'], ci, f)]['area']}
		for mirror in (False, True):
			arr, foot = net(c, mirror, look, stack)
			key = (arr.shape, foot, arr.tobytes())
			if key in keys:
				wears[id(c)] = (keys[key], mirror)
				break
		else:
			assert not b['name'].startswith('right_wing'), f'{b["name"]} does not mirror the left wing'
			arr, foot = net(c, False, look, stack)
			keys[(arr.shape, foot, arr.tobytes())] = len(nets)
			wears[id(c)] = (len(nets), False)
			nets.append({'arr': arr, 'foot': foot, 'part': part(b['name']), 'name': c['name']})

	# each body part's nets packed into a block, the blocks into the smallest texture (trying each block's shapes)
	options = []
	for name in PARTS:
		members = [i for i, n in enumerate(nets) if n['part'] == name]
		sizes = [(nets[i]['foot'][2] - nets[i]['foot'][0], nets[i]['foot'][3] - nets[i]['foot'][1]) for i in members]
		options.append([(members, bw, bh, places) for bw, bh, places in shapes(sizes, GAP)])
	combos = sorted(itertools.product(*options), key=lambda bs: sum((bw + PART_GAP) * (bh + PART_GAP) for _, bw, bh, _ in bs))

	def fit(width, height):
		for blocks in combos:
			sizes = [(bw + PART_GAP, bh + PART_GAP) for _, bw, bh, _ in blocks]
			if sum(w * h for w, h in sizes) > width * height:
				return None
			# a net's offset may not go negative (Blockbench): its block sits at least as far in as its footprint is
			mins = [(max(max(nets[i]['foot'][0] - places[k][0], 0) for k, i in enumerate(members)),
					 max(max(nets[i]['foot'][1] - places[k][1], 0) for k, i in enumerate(members))) for members, _, _, places in blocks]
			if placed := maxrects(sizes, width + PART_GAP, height + PART_GAP, mins):
				return blocks, placed
	for width, height in sorted(((2 ** a, 2 ** b) for a in range(4, 12) for b in range(4, 12)), key=lambda s: (s[0] * s[1], abs(math.log2(s[0] / s[1])), -s[0])):
		if found := fit(width, height):
			blocks, placed = found
			break
	else:
		raise RuntimeError('the nets do not fit in 2048x2048')
	atlas = np.zeros((height, width, stack.shape[2]), np.uint8)
	for (members, _, _, places), (bx, by) in zip(blocks, (placed[k] for k in range(len(blocks)))):
		for k, i in enumerate(members):
			n = nets[i]
			u0, v0, u1, v1 = n['foot']
			x, y = bx + places[k][0], by + places[k][1]
			n['uv'] = [x - u0, y - v0]
			assert min(n['uv']) >= 0, n['name']
			atlas[y:y + v1 - v0, x:x + u1 - u0] = n['arr'][v0:v1, u0:u1]

	for _, _, c in cubes:
		i, mirror = wears[id(c)]
		c['uv'] = list(nets[i]['uv'])
		c.pop('mirror', None)
		if mirror:
			c['mirror'] = True
	g['description']['texture_width'], g['description']['texture_height'] = width, height

	resampled = verify(g, faces, stack, atlas)
	json.dump(geo, open(GEO, 'w'))
	for i, f in enumerate(files):
		Image.fromarray(np.ascontiguousarray(atlas[..., 4 * i:4 * i + 4])).save(os.path.join(OUT, f), optimize=True)
	print(f'  uv: {W}x{H} -> {width}x{height} box UV, {len(nets)} nets for {len(cubes)} cubes'
		  f' ({sum(1 for _, m in wears.values() if m)} mirrored, {resampled} faces resampled)')


def verify(g, faces, old, new):
	"""Every face whose net is as big as its old texels samples the same texels (all textures) as before, a
	clear one stays clear; returns how many faces were resampled instead."""
	bad = resampled = 0
	for b in g['bones']:
		for ci, c in enumerate(b.get('cubes', [])):
			assert not isinstance(c['uv'], dict)
			for f, cs in drawn(c).items():
				face = faces[(b['name'], ci, f)]
				if not face['area']:
					continue
				o, n = rect(face['corners']), rect(cs)
				s, t = grid(face['corners'])
				was = sample(face['corners'], old, s, t)
				if not was.any():
					bad += int(sample(cs, new, s, t).any())     # clear stays clear
				elif sorted((o[2] - o[0], o[3] - o[1])) != sorted((n[2] - n[0], n[3] - n[1])):
					resampled += 1
				else:
					bad += int((was != sample(cs, new, s, t)).any())
	if bad:
		raise AssertionError(f'{bad} faces sample different texels after packing')
	return resampled


if __name__ == '__main__':
	pack()
