"""Splits the long neck and tail boxes into short segments so they can bend smoothly.

The neck's two boxes become four segments (swan neck, look-at), the tail's three become nine (it
trails through turns and whips in a sweep instead of folding at two hinges).

A box-UV cube cannot be cut without moving its texture, so each cut cube is first converted to
per-face UV that reproduces its box-UV net exactly, then sliced along Z: the faces that run along the
chain (east, west, up, down) are divided at the same fraction of their rectangle, the front piece keeps
the north face, the back piece the south face, and the new faces at the cut reuse those end-caps.
Each piece also reaches OVERLAP px into its neighbour, so a bend opens no visible gap (the joint
filler the rig rules ask for).

Face rectangles are written in Blockbench's convention (what its own Bedrock exporter writes):
[x1, y1] + uv_size [x2 - x1, y2 - y1], with up/down flipped as Blockbench lays them out.

Poses may still name the original bones (neck_rot1/2, tail_rot1/2/3): `expand` shares such a rotation
equally between the bone's segments (the same total curve, bent evenly instead of at one hinge).
"""

OVERLAP = 2.0

# Each chain, root (body) first: the original bones, and for each its segments as
# (name, pivot, (z_lo, z_hi)). The front of the model is -z, so the neck runs toward -z and the tail
# toward +z. Segment pivots sit on the original bone's axis at the segment's root end.
CHAINS = {
	'neck': [
		('neck_rot1', [('neck_1', [0, 53.125, -29], (-44, -29)), ('neck_2', [0, 53.125, -44], (-59, -44))]),
		('neck_rot2', [('neck_3', [0, 55.125, -58.5], (-72, -59)), ('neck_4', [0, 55.125, -72], (-85, -72))]),
	],
	'tail': [
		('tail_rot1', [('tail_1', [0, 53, 43.5], (44, 59.5)), ('tail_2', [0, 53, 59.5], (59.5, 75))]),
		('tail_rot2', [('tail_3', [0, 57, 75.5], (75, 89.33)), ('tail_4', [0, 57, 89.33], (89.33, 103.67)),
					   ('tail_5', [0, 57, 103.67], (103.67, 118))]),
		('tail_rot3', [('tail_6', [0, 60, 118], (118, 137)), ('tail_7', [0, 60, 137], (137, 156)),
					   ('tail_8', [0, 60, 156], (156, 175)), ('tail_9', [0, 60, 175], (175, 194))]),
	],
}
# which way each chain grows from its root: -1 toward -z (the neck), +1 toward +z (the tail)
DIRECTION = {'neck': -1, 'tail': 1}

NECK = [seg[0] for _, segs in CHAINS['neck'] for seg in segs]
TAIL = [seg[0] for _, segs in CHAINS['tail'] for seg in segs]
# original bone -> its segment names
SEGMENTS = {orig: [seg[0] for seg in segs] for chain in CHAINS.values() for orig, segs in chain}


def box_faces(cube):
	"""Blockbench's box-UV net for a cube: face -> (x1, y1, x2, y2)."""
	(u, v), (w, h, d) = cube['uv'], cube['size']
	return {
		'north': (u + d, v + d, u + d + w, v + d + h),
		'east': (u, v + d, u + d, v + d + h),
		'south': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
		'west': (u + d + w, v + d, u + 2 * d + w, v + d + h),
		'up': (u + d + w, v + d, u + d, v),
		'down': (u + d + 2 * w, v, u + d + w, v + d),
	}


# Where the front (-z) end of each lengthwise face sits in its rectangle: which coordinate (0 = x,
# 1 = y) runs along the neck, and whether the front is at the rectangle's first (x1/y1) corner.
# Verified by rendering the split neck in Blockbench against the uncut model: identical apart from the
# seam lines.
ALONG = {'east': (0, False), 'west': (0, True), 'up': (1, True), 'down': (1, False)}


def slice_face(rect, axis, front_first, f0, f1):
	"""Sub-rectangle covering fraction [f0, f1] of the face's length measured from the front."""
	a0, a1 = (rect[0], rect[2]) if axis == 0 else (rect[1], rect[3])
	if not front_first:
		a0, a1 = a1, a0
	b0, b1 = a0 + (a1 - a0) * f0, a0 + (a1 - a0) * f1
	if not front_first:
		b0, b1 = b1, b0
	r = list(rect)
	if axis == 0:
		r[0], r[2] = b0, b1
	else:
		r[1], r[3] = b0, b1
	return r


def to_bedrock(rect, face):
	x1, y1, x2, y2 = rect
	if face in ('up', 'down'):
		# Bedrock stores up/down rectangles from the opposite corner (Blockbench flips them on import)
		return {'uv': [round(x2, 4), round(y2, 4)], 'uv_size': [round(x1 - x2, 4), round(y1 - y2, 4)]}
	return {'uv': [round(x1, 4), round(y1, 4)], 'uv_size': [round(x2 - x1, 4), round(y2 - y1, 4)]}


def split_cube(cube, z_front, z_back, overlap_front, overlap_back):
	"""The part of `cube` between z_front and z_back (extended by the overlaps), with per-face UV."""
	oz, d = cube['origin'][2], cube['size'][2]
	zf, zb = max(oz, z_front - overlap_front), min(oz + d, z_back + overlap_back)
	f0, f1 = (zf - oz) / d, (zb - oz) / d
	faces = box_faces(cube)
	uv = {}
	for name, (axis, front_first) in ALONG.items():
		uv[name] = to_bedrock(slice_face(faces[name], axis, front_first, f0, f1), name)
	uv['north'] = to_bedrock(faces['north'], 'north')
	uv['south'] = to_bedrock(faces['south'], 'south')
	out = {k: v for k, v in cube.items() if k not in ('uv', 'origin', 'size')}
	out['origin'] = [cube['origin'][0], cube['origin'][1], zf]
	out['size'] = [cube['size'][0], cube['size'][1], round(zb - zf, 4)]
	out['uv'] = uv
	return out


def split_chains(bones):
	"""Replaces neck_rot1/2 and tail_rot1/2/3 in `bones` (a Bedrock bone list) with their segments."""
	by = {b['name']: b for b in bones}
	replaced = {}
	for chain, entries in CHAINS.items():
		flat = [(orig, seg) for orig, segs in entries for seg in segs]
		parent = by[entries[0][0]]['parent']
		out = []
		for k, (orig, (name, pivot, (z_lo, z_hi))) in enumerate(flat):
			# overlap only into neighbours inside the chain: the outer ends meet the body and the head/tip
			toward_tip = OVERLAP if k < len(flat) - 1 else 0.0
			toward_root = OVERLAP if k > 0 else 0.0
			if DIRECTION[chain] < 0:
				ov_front, ov_back = toward_tip, toward_root
			else:
				ov_front, ov_back = toward_root, toward_tip
			cubes = [split_cube(c, z_lo, z_hi, ov_front, ov_back) for c in by[orig].get('cubes', [])]
			out.append({'name': name, 'parent': parent, 'pivot': pivot, 'cubes': cubes})
			parent = name
		for orig, segs in entries:
			replaced[orig] = (out, segs[-1][0])
	result, done = [], set()
	for b in bones:
		if b['name'] in replaced:
			segs, _ = replaced[b['name']]
			if id(segs) not in done:
				result.extend(segs)
				done.add(id(segs))
			continue
		result.append(b)
	# whatever hung from an original bone now hangs from its last segment (the head from neck_4)
	for b in result:
		if b.get('parent') in replaced:
			b['parent'] = replaced[b['parent']][1]
	return result


def expand(pose):
	"""A pose naming original chain bones, rewritten onto the segments. A rotation (or offset) of an
	original bone is shared equally between its segments and added to anything already set on them."""
	if not any(k in SEGMENTS for k in pose):
		return pose
	out = {k: v for k, v in pose.items() if k not in SEGMENTS}
	for orig, segs in SEGMENTS.items():
		if orig not in pose:
			continue
		n = len(segs)
		for seg in segs:
			cur = {k: list(v) for k, v in out.get(seg, {}).items()}
			for key, vec in pose[orig].items():
				share = [c / n for c in vec] if key == 'r' else (list(vec) if seg == segs[0] else [0, 0, 0])
				base = cur.get(key, [0.0, 0.0, 0.0])
				cur[key] = [base[i] + share[i] for i in range(3)]
			out[seg] = cur
	return out
