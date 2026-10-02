"""Builds the breath's heat glow: an emissive texture animation of the chest, throat and jaw lighting up.

    python3 tools/heat.py        (build_assets.py runs it, between build_wings.py and pack_uv.py)

Before the stream breath the dragon heats up from inside: its chest starts to glow first and fast, the
heat climbs the underside of the neck, then the jaw and the mouth light up, and the fire comes. Each
texel of those parts gets an ignition time t0 in [0, 1] of the inhale (by how far forward it sits on the
model, chest first) and a peak brightness; at time t it shines ease((t - t0) / RAMP) of its peak.

The animation is FRAMES emissive textures, frame k the glow at t = k / FRAMES (frame 0 is dark, so it is
not written). HeatGlowLayer draws the dragon again with them, additively: with the inhale at t between
two frames it draws both, weighted, which is exactly the per-texel glow interpolated between them.

Texel -> model point follows GeckoLib 4's quad building (BakedModelFactory / GeoQuad, not mirrored):
for a face with uv (u, v) and uv_size (us, vs) the texture corners (u, v), (u + us, v), (u, v + vs),
(u + us, v + vs) land on the face's 2nd, 1st, 3rd and 4th vertex (VertexSet.quadNorth() etc.). Box UV
is first unfolded into per-face rectangles the same way GeckoLib does it.
"""
import json
import math
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'out')
SKIN = os.path.join(OUT, 'ender_dragon.png')          # baked on build_wings.py's layout; pack_uv.py repacks it
GEO = os.path.join(OUT, 'ender_dragon.geo.json')
DST = os.path.join(OUT, 'heat')
FRAMES = 8              # HeatGlowLayer.FRAMES

# glow colors (the rune magenta of the dragon texture), by brightness: embers, glow, white-hot
EMBER, GLOW, HOT = (96, 22, 140), (241, 56, 241), (255, 176, 255)

CHEST_BACK, CHEST_FRONT = -4.0, -29.0      # model z: where the chest heat starts, the body's front face
NECK_END, JAW_END = -85.0, -116.0          # model z: the head's pivot, the jaw's tip


def ease(k):
	k = max(0.0, min(1.0, k))
	return k * k * (3 - 2 * k)


def ignition(z):
	"""(t0, ramp) for a texel at model z: the chest (0 - 0.25, quick), the neck (0.3 - 0.65), the jaw (0.68 - 0.85)."""
	if z > CHEST_FRONT:
		return 0.25 * (CHEST_BACK - z) / (CHEST_BACK - CHEST_FRONT), 0.12
	if z > NECK_END:
		return 0.3 + 0.35 * (CHEST_FRONT - z) / (CHEST_FRONT - NECK_END), 0.15
	return 0.68 + 0.17 * min(1.0, (NECK_END - z) / (NECK_END - JAW_END)), 0.15


def band(y, top, soft):
	"""1 up to `soft` px below `top`, fading to 0 at `top` and above."""
	return 1.0 - ease((y - top) / soft + 1.0) if soft else float(y <= top)


# which faces heat up, and how much (0-1) at a model point p = (x, y, z); None: the face stays dark
def body_face(face, p, cube):
	if cube['size'][0] == 0:
		return 0.0   # the back fin
	front = 1.0 - ease((p[2] - CHEST_BACK) / 8.0 + 1.0)     # fades out behind the chest
	if face == 'down':
		return front
	if face == 'north':
		return band(p[1], 42.0, 6.0)                          # below the neck's root
	if face in ('east', 'west'):
		return front * band(p[1], 38.0, 6.0) * 0.8            # the lower flanks
	return 0.0


def neck_face(face, p, cube):
	if cube['size'][0] == 0:
		return 0.0
	y0 = cube['origin'][1]
	if face == 'down':
		return 1.0
	if face in ('east', 'west'):
		return band(p[1], y0 + 0.4 * cube['size'][1], 5.0) * 0.85   # the throat's sides
	return 0.0


def jaw_face(face, p, cube):
	return 0.0 if cube['size'][0] == 0 else (1.0 if face != 'up' else 0.9)   # up: the floor of the mouth


def palate_face(face, p, cube):
	return 0.8 if face == 'down' and cube['size'][0] > 0 and cube['size'][1] <= 4 else 0.0   # the roof of the mouth


HEATED = {'body': body_face, 'neck_1': neck_face, 'neck_2': neck_face, 'neck_3': neck_face, 'neck_4': neck_face,
		  'jaw_group': jaw_face, 'jaw_upper': palate_face}


def faces(cube):
	"""(face, (u, v), (us, vs), corners) per face, as GeckoLib 4 builds them; corners match uv corners
	(u+us, v), (u, v), (u, v+vs), (u+us, v+vs)."""
	(ox, oy, oz), (sx, sy, sz) = cube['origin'], cube['size']
	x0, x1 = -(ox + sx), -ox      # GeckoLib mirrors x
	y0, y1, z0, z1 = oy, oy + sy, oz, oz + sz
	bl_b, br_b, tl_b, tr_b = (x0, y0, z0), (x0, y0, z1), (x0, y1, z0), (x0, y1, z1)
	tl_f, tr_f, bl_f, br_f = (x1, y1, z0), (x1, y1, z1), (x1, y0, z0), (x1, y0, z1)
	quads = {
		'west': [tr_b, tl_b, bl_b, br_b], 'east': [tl_f, tr_f, br_f, bl_f],
		'north': [tl_b, tl_f, bl_f, bl_b], 'south': [tr_f, tr_b, br_b, br_f],
		'up': [tr_b, tr_f, tl_f, tl_b], 'down': [bl_b, bl_f, br_f, br_b],
	}
	uv = cube['uv']
	if isinstance(uv, dict):
		rects = {f: (d['uv'], d['uv_size']) for f, d in uv.items()}
	else:
		u, v = uv
		x, y, z = (math.floor(s) for s in (sx, sy, sz))
		rects = {
			'west': ((u + z + x, v + z), (z, y)), 'east': ((u, v + z), (z, y)),
			'north': ((u + z, v + z), (x, y)), 'south': ((u + z + x + z, v + z), (x, y)),
			'up': ((u + z, v), (x, z)), 'down': ((u + z + x, v + z), (x, -z)),
		}
	for f, (st, size) in rects.items():
		if size[0] and size[1]:
			yield f, st, size, quads[f]


def texels(st, size):
	"""(px, py, a, b): the texels a face covers, with their position across it (0-1 along u and v)."""
	(u, v), (us, vs) = st, size
	ua, ub = sorted((u, u + us))
	va, vb = sorted((v, v + vs))
	for py in range(math.floor(va), math.ceil(vb)):
		for px in range(math.floor(ua), math.ceil(ub)):
			yield px, py, (px + 0.5 - u) / us, (py + 0.5 - v) / vs


def point(c, a, b):
	"""The model point at (a, b) across a face with uv corners c = [(1,0), (0,0), (0,1), (1,1)]."""
	return tuple((1 - a) * (1 - b) * c[1][i] + a * (1 - b) * c[0][i] + (1 - a) * b * c[2][i] + a * b * c[3][i] for i in range(3))


def build():
	skin = Image.open(SKIN).convert('RGBA')
	w, h = skin.size
	bones = json.load(open(GEO))['minecraft:geometry'][0]['bones']
	heat = {}      # texel -> [(t0, ramp, peak)]
	other = set()  # texels some unheated face also uses
	for bone in bones:
		rule = HEATED.get(bone['name'])
		for cube in bone.get('cubes', []):
			for face, st, size, corners in faces(cube):
				for px, py, a, b in texels(st, size):
					if not (0 <= px < w and 0 <= py < h) or skin.getpixel((px, py))[3] == 0:
						continue
					p = point(corners, a, b)
					weight = rule(face, p, cube) if rule else 0.0
					if weight <= 0.0:
						other.add((px, py))
						continue
					r, g, bl, _ = skin.getpixel((px, py))
					dark = 1.0 - min(1.0, (0.3 * r + 0.59 * g + 0.11 * bl) / 60.0)   # the hide is 0-50: the seams between scales glow most
					t0, ramp = ignition(p[2])
					heat.setdefault((px, py), []).append((t0, ramp, weight * (0.25 + 0.75 * dark ** 1.5)))
	shared = [t for t in heat if t in other]
	for t in shared:
		del heat[t]     # never light up a texel that another (unheated) part wears too
	print(f'  heat: {len(heat)} texels ({len(shared)} shared with unheated faces left dark)')

	os.makedirs(DST, exist_ok=True)
	for k in range(1, FRAMES + 1):
		t = k / FRAMES
		out = Image.new('RGBA', (w, h), (0, 0, 0, 0))
		for (px, py), ignitions in heat.items():
			glow = max(peak * ease((t - t0) / ramp) for t0, ramp, peak in ignitions)
			if glow <= 0.0:
				continue
			color = mix(mix(EMBER, GLOW, ease(glow / 0.7)), HOT, ease((glow - 0.85) / 0.15))
			out.putpixel((px, py), (*(round(c * glow) for c in color), 255))
		path = os.path.join(DST, f'ender_dragon_heat_{k}.png')
		out.save(path, optimize=True)
		print('  ->', os.path.relpath(path, HERE))


def mix(a, b, k):
	return tuple(x + (y - x) * k for x, y in zip(a, b))


if __name__ == '__main__':
	build()
