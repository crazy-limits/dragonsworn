"""Repacks the dragon's texture: every face gets its own per-face UV in a small atlas.

    python3 tools/pack_uv.py      (build_assets.py runs it, after heat.py)

Reads out/ender_dragon.geo.json and the textures drawn on its layout (the skin, the glowmask and the heat
frames, all treated as one stack of channels), and writes them back repacked:

* the right wing wears the left wing's art, mirrored across the body: each right face samples the left
  face at the mirror image of its points;
* faces whose texels overlap keep sharing them; identical regions share one patch, also when one is the other rotated or flipped (GeckoLib's per-face
  `uv_size` may be negative and `uv_rotation` turns it by 90 degrees);
* faces that are transparent in every texture (and faces with no area) all sample one clear texel;
* the patches are packed (MaxRects) into the smallest power-of-two texture they fit in.

Every move maps whole texels onto whole texels, so each face samples exactly the texels it did before;
`verify` checks that for every face against the GeckoLib quad builder's UVs (BakedModelFactory / GeoQuad).
"""
import glob
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


def corners(params):
	"""The texture point at each of the face's four vertices."""
	(u, v), (us, vs), rot = params
	val = {'A': u + us, 'B': v, 'C': u, 'D': v + vs}
	return [(val[a], val[b]) for a, b in ROTATIONS[rot]]


def params_for(target):
	"""(uv, uv_size, rotation) giving the four vertices exactly these texture points."""
	for rot, layout in enumerate(ROTATIONS):
		val, ok = {}, True
		for (a, b), (tu, tv) in zip(layout, target):
			for k, t in ((a, tu), (b, tv)):
				if val.setdefault(k, t) != t:
					ok = False
		if ok:
			return (val['C'], val['B']), (val['A'] - val['C'], val['D'] - val['B']), rot
	raise ValueError(f'not a rectangle: {target}')


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


# ---- patches ----------------------------------------------------------------------------------------

def orient(arr, t):
	"""One of the 8 symmetries of a patch: t = (transpose, flip u, flip v)."""
	tr, fu, fv = t
	if tr:
		arr = arr.transpose(1, 0, 2)
	if fu:
		arr = arr[:, ::-1]
	if fv:
		arr = arr[::-1]
	return arr


def orient_point(lu, lv, w, h, t):
	"""Where a point (lu, lv) of a w x h patch lands in orient(patch, t)."""
	tr, fu, fv = t
	if tr:
		lu, lv, w, h = lv, lu, h, w
	return (w - lu if fu else lu), (h - lv if fv else lv)


SYMMETRIES = [(tr, fu, fv) for tr in (0, 1) for fu in (0, 1) for fv in (0, 1)]


def maxrects(sizes, width, height):
	"""Places (w, h) rectangles (biggest first, best short side fit, 90 degree turns allowed); None if they do not fit."""
	free = [(0, 0, width, height)]
	placed = {}
	for i in sorted(range(len(sizes)), key=lambda i: (-max(sizes[i]), -min(sizes[i]))):
		w, h = sizes[i]
		best = None
		for fx, fy, fw, fh in free:
			for turned, (rw, rh) in ((False, (w, h)), (True, (h, w))):
				if rw <= fw and rh <= fh:
					score = (min(fw - rw, fh - rh), max(fw - rw, fh - rh), fy, fx)
					if best is None or score < best[0]:
						best = (score, fx, fy, rw, rh, turned)
		if best is None:
			return None
		_, x, y, rw, rh, turned = best
		placed[i] = (x, y, turned)
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
			params, qs = face_params(c), quads(c)
			for f in FACES:
				if f in params:
					faces[(b['name'], ci, f)] = {'corners': corners(params[f]), 'area': has_area(qs[f])}
	mirror_wing(g['bones'], faces)

	# faces whose texels overlap (a mirrored face beside its original, a split chain face) share one region
	live = {}
	for key, face in faces.items():
		us, vs = zip(*face['corners'])
		x0, x1, y0, y1 = math.floor(min(us)), math.ceil(max(us)), math.floor(min(vs)), math.ceil(max(vs))
		if face['area'] and x1 > x0 and y1 > y0 and stack[y0:y1, x0:x1, 3::4].any():
			live[key] = (x0, y0, x1, y1)
		else:
			face['patch'] = None        # transparent everywhere, or no area: the clear texel
	group = {k: k for k in live}

	def root(k):
		while group[k] != k:
			group[k] = group[group[k]]
			k = group[k]
		return k
	items = sorted(live.items(), key=lambda kv: kv[1])
	for i, (a, ra) in enumerate(items):
		for b, rb in items[i + 1:]:
			if rb[0] >= ra[2]:
				break
			if rb[1] < ra[3] and ra[1] < rb[3]:
				group[root(a)] = root(b)
	regions = {}
	for k, r in live.items():
		g0 = regions.get(root(k))
		regions[root(k)] = r if g0 is None else (min(g0[0], r[0]), min(g0[1], r[1]), max(g0[2], r[2]), max(g0[3], r[3]))

	patches, keys = [], {}         # unique patches (canonical orientation) and their index by content
	patches.append(np.zeros((1, 1, stack.shape[2]), np.uint8))   # the clear texel
	for key in live:
		x0, y0, x1, y1 = regions[root(key)]
		patch = stack[y0:y1, x0:x1]
		forms = [(orient(patch, t), t) for t in SYMMETRIES]
		canon, t = min(forms, key=lambda ft: (ft[0].shape, ft[0].tobytes()))
		k = (canon.shape, canon.tobytes())
		if k not in keys:
			keys[k] = len(patches)
			patches.append(np.ascontiguousarray(canon))
		faces[key].update(patch=keys[k], origin=(x0, y0), size=(x1 - x0, y1 - y0), t=t)

	sizes = [(p.shape[1], p.shape[0]) for p in patches]
	area = sum(w * h for w, h in sizes)
	for width, height in sorted(((2 ** a, 2 ** b) for a in range(4, 12) for b in range(4, 12)), key=lambda s: (s[0] * s[1], abs(math.log2(s[0] / s[1])), -s[0])):
		if width * height >= area and (placed := maxrects(sizes, width, height)):
			break
	else:
		raise RuntimeError('patches do not fit in 2048x2048')
	atlas = np.zeros((height, width, stack.shape[2]), np.uint8)
	for i, (x, y, turned) in placed.items():
		p = orient(patches[i], (1, 0, 0)) if turned else patches[i]
		atlas[y:y + p.shape[0], x:x + p.shape[1]] = p

	for b in g['bones']:
		for ci, c in enumerate(b.get('cubes', [])):
			uv = {}
			for f in FACES:
				face = faces.get((b['name'], ci, f))
				if face is None:
					continue
				if face['patch'] is None:
					x, y, _ = placed[0]
					target = [(x + 1, y), (x, y), (x, y + 1), (x + 1, y + 1)]   # the clear texel
				else:
					x, y, turned = placed[face['patch']]
					(x0, y0), (w, h) = face['origin'], face['size']
					cw, ch = (h, w) if face['t'][0] else (w, h)        # the canonical patch's size
					target = []
					for cu, cv in face['corners']:
						lu, lv = orient_point(cu - x0, cv - y0, w, h, face['t'])
						if turned:
							lu, lv = orient_point(lu, lv, cw, ch, (1, 0, 0))
						target.append((x + lu, y + lv))
				(u, v), (su, sv), rot = params_for(target)
				uv[f] = {'uv': [u, v], 'uv_size': [su, sv]}
				if rot:
					uv[f]['uv_rotation'] = rot * 90
			c['uv'] = uv
	g['description']['texture_width'], g['description']['texture_height'] = width, height

	verify(faces, stack, g, atlas)
	json.dump(geo, open(GEO, 'w'))
	for i, f in enumerate(files):
		Image.fromarray(np.ascontiguousarray(atlas[..., 4 * i:4 * i + 4])).save(os.path.join(OUT, f), optimize=True)
	print(f'  uv: {W}x{H} -> {width}x{height}, {len(patches) - 1} patches for {sum(1 for f in faces.values() if f["patch"] is not None)} faces'
		  f' ({100 * area / (width * height):.0f} % used)')


def verify(faces, old, g, new):
	"""Every face samples the same texels (all textures) through its new UVs as through its old ones."""
	bad = 0
	for b in g['bones']:
		for ci, c in enumerate(b.get('cubes', [])):
			params = face_params(c)
			for f, p in params.items():
				face = faces[(b['name'], ci, f)]
				nc = np.array(corners(p), float)
				if face['patch'] is None:
					tex = new[int(math.floor(nc[:, 1].min())), int(math.floor(nc[:, 0].min()))]
					bad += int(tex.any())
					continue
				s, t = grid(face['corners'])
				bad += int((sample(face['corners'], old, s, t) != sample(nc, new, s, t)).any())
	if bad:
		raise AssertionError(f'{bad} faces sample different texels after packing')


if __name__ == '__main__':
	pack()
