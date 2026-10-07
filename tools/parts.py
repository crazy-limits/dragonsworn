"""Hitbox anchors for every animation frame, exported to the game as generated Java.

The dragon's hitboxes (vanilla `EnderDragonPart`s) are placed on the model: each one hangs from a bone
at a point on that bone's geometry. This module runs the same forward kinematics the poses were solved
with (`rig.py`) over every keyframe and records, in model space:

* every part's anchor point,
* the pivots of the neck and tail segments (and the head), so the game can add the same procedural
  bends the renderer adds (head leads a turn, the tail trails it) before placing the parts, and
* each shoulder's pivot and a point one block along its X axis, plus the body's pivot, a point one
  block ahead of it and one block above it: the game counter-rotates the wings about their X axis
  against the body's pitch (keyframed + procedural) the way the renderer does, and hangs the tail
  (keyed straight, all procedural) from the body's frame,
* and once, the tail's rest geometry: each segment's pivot and the capsule (axis and radius) that
  wraps its main cube, for the tail's ground and block collisions (body/TailChain.java).

Model space is the editor's, in blocks: y up, the head toward -z, x to the dragon's right. Values are
stored as int16 (blocks * 256) in base64, one string per animation, into
src/main/java/crazylimits/dragonsworn/body/PoseData.java. Never edit that file by hand.

Licence: the source model and texture (tools/source/dragon*.png, dragon_raw.geo.json) are from the "Ender
Dragon Reborn" resource pack by Parrie43, All Rights Reserved, and so is everything built from them. This
script is LGPL; its output is not: see LICENSE-ASSETS.md.
"""
import base64
import os
import struct

from chain import NECK, TAIL, expand
from rig import to_editor

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
OUT = os.path.join(ROOT, 'src', 'main', 'java', 'crazylimits', 'dragonsworn', 'body', 'PoseData.java')

# Wing fan apexes, from the geometry (see build_wings.py). Filled in by `_fan` from the rig on first use.
_APEX = {}

# (name, bone, point in file coordinates (px) or a callable(rig) returning one, width, height (blocks)).
# The first eight are the vanilla parts in vanilla order (head, neck, body, tail x3, wing x2): vanilla
# code addresses them by field. The rest are added after them.
PARTS = [
	('head', 'jaw_upper', [0, 55, -105], 1.6, 1.4),
	('neck', 'neck_4', [0, 54, -78], 1.4, 1.4),
	('body', 'body', [0, 48, -14], 2.6, 2.4),
	('tail', 'tail_2', [0, 53, 64], 1.6, 1.6),
	('tail', 'tail_5', [0, 57, 110], 1.0, 1.0),
	('tail', 'tail_8', [0, 60, 165], 0.7, 0.7),
	('wing', 'left_wing', [40, 64, 4], 2.2, 1.0),
	('wing', 'right_wing', [-40, 64, 4], 2.2, 1.0),
	('neck', 'neck_2', [0, 52, -50], 1.6, 1.6),
	('body', 'body', [0, 48, 26], 2.6, 2.4),
	('tail', 'tail_3', [0, 57, 85], 1.2, 1.2),
	('tail', 'tail_6', [0, 60, 132], 0.9, 0.9),
	('tail', 'tail_9', [0, 60, 188], 0.6, 0.6),
	('wing', 'left_wing_tip', [101, 65, -16], 2.4, 1.0),
	('wing', 'left_wing_web2', lambda rig: _fan(rig, 'left', 45, 16), 3.0, 1.0),
	('wing', 'left_wing_web2', lambda rig: _fan(rig, 'left', 105, 22), 3.0, 1.0),
	('wing', 'right_wing_tip', [-101, 65, -16], 2.4, 1.0),
	('wing', 'right_wing_web2', lambda rig: _fan(rig, 'right', 45, 16), 3.0, 1.0),
	('wing', 'right_wing_web2', lambda rig: _fan(rig, 'right', 105, 22), 3.0, 1.0),
	('leg', 'lowerleg_left', [16, 20, 34], 1.4, 2.0),
	('leg', 'lowerleg_right', [-16, 20, 34], 1.4, 2.0),
]
NECK_PIVOTS = NECK + ['head_group']
PIVOTS = NECK_PIVOTS + TAIL
WINGS = ['left_wing', 'right_wing']


def _fan(rig, side, along, across):
	"""A point on the folded-open hand: `along` px from the apex along the fan's middle, `across` px
	into the membrane (file coordinates, like the other anchors)."""
	if side not in _APEX:
		ex, ey, ez = rig.bones[f'{side}_wing_tip3']['pivot']
		_APEX[side] = (-ex, ey, ez)                          # editor -> file
	# the left hand is built along +x from its apex, its membrane opening toward +z; the right is its mirror
	ax, ay, az = _APEX[side]
	return [ax + (along if side == 'left' else -along), ay, az + across]


def _chain_of(rig, bone):
	"""(chain, depth): which chain a bone hangs from and how many of its segment rotations reach it.
	Chain 0 = none (body, legs), 1 = neck (head_group counts as depth 5), 2 = tail, 3 = left wing,
	4 = right wing (depth 1)."""
	b = bone
	while b:
		if b in WINGS:
			return 3 + WINGS.index(b), 1
		if b == 'head_group':
			return 1, 5
		if b in NECK:
			return 1, NECK.index(b) + 1
		if b in TAIL:
			return 2, TAIL.index(b) + 1
		b = rig.bones[b]['parent']
	return 0, 0


def _tail_geometry(rig):
	"""Rest pivots and capsules of the tail segments (blocks). A segment's capsule runs along its main
	cube (the first: the second is the flat dorsal fin) from its pivot to the next segment's (the last
	one to the cube's end), through the cube's middle, as thick as the cube's larger half-side."""
	rest, capsules = [], []
	for k, seg in enumerate(TAIL):
		b = rig.bones[seg]
		rest += [v / 16.0 for v in b['pivot']]
		lo, hi = b['cubes'][0]['min'], b['cubes'][0]['max']
		cx, cy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
		z0 = b['pivot'][2]
		z1 = rig.bones[TAIL[k + 1]]['pivot'][2] if k + 1 < len(TAIL) else hi[2]
		r = max(hi[0] - lo[0], hi[1] - lo[1]) / 2
		capsules += [v / 16.0 for v in (cx, cy, z0, cx, cy, z1, r)]
	return rest, capsules


def _java_string(s, chunk=60000):
	"""A Java expression for a long string: javac caps one string constant at 65535 bytes, and folds
	constants joined by +, so a long one is joined at run time."""
	if len(s) <= chunk:
		return f'"{s}"'
	return 'String.join("", ' + ', '.join(f'"{s[i:i + chunk]}"' for i in range(0, len(s), chunk)) + ')'


def export(rig, track, animations, tails):
	"""`track` = {anim name: [full pose per keyframe]}, `animations` = anims.ANIMATIONS, `tails` = {anim name:
	[per keyframe: 9 tail bends down, 9 right, rest, lift]} (anims.tail_track)."""
	anchors = []
	for name, bone, point, w, h in PARTS:
		p = point(rig) if callable(point) else point
		anchors.append((bone, to_editor(p)))
	pivots = [(bone, rig.bones[bone]['pivot']) for bone in PIVOTS]
	for bone in WINGS:
		x, y, z = rig.bones[bone]['pivot']
		pivots += [(bone, [x, y, z]), (bone, [x + 16, y, z])]
	x, y, z = rig.bones['body']['pivot']
	pivots += [('body', [x, y, z]), ('body', [x, y, z - 16]), ('body', [x, y + 16, z])]
	# the neck's keyed roll (degrees, neck_1..neck_4, head_group; the wall poses twist it), packed as two
	# points: the game reads them back as rolls (PoseTrack.NECK_ROLL), not as places
	rolls = [(None, [0, 1, 2]), (None, [3, 4, -1])]

	data, meta = [], []
	for name, (fn, length, loop, step) in animations.items():
		raw = bytearray()
		frames = track[name]
		for pose in frames:
			mats = rig.matrices(pose)
			for bone, p in anchors + pivots:
				x, y, z = rig.point(pose, bone, p, mats)
				for v in (x, y, z):
					raw += struct.pack('<h', max(-32768, min(32767, round(v / 16.0 * 256))))
			full = expand(pose)
			for _, slots in rolls:
				for k in slots:
					v = full.get(NECK_PIVOTS[k], {}).get('r', [0, 0, 0])[2] if k >= 0 else 0.0
					raw += struct.pack('<h', max(-32768, min(32767, round(v / 16.0 * 256))))
		data.append(base64.b64encode(bytes(raw)).decode('ascii'))
		meta.append((name, length, loop is True, step, len(frames)))

	tail_names, tail_data = [], []
	for name, rows in tails.items():
		raw = bytearray()
		for row in rows:
			for v in row:
				raw += struct.pack('<h', max(-32768, min(32767, round(v * 64))))
		tail_names.append(name)
		tail_data.append(base64.b64encode(bytes(raw)).decode('ascii'))

	chains = [_chain_of(rig, bone) for _, bone, _, _, _ in PARTS]
	tail_rest, tail_capsules = _tail_geometry(rig)
	q = lambda s: '"' + s + '"'
	fl = lambda v: f'{v:g}f'
	lines = [
		'package crazylimits.dragonsworn.body;',
		'',
		'/**',
		' * GENERATED by {@code tools/parts.py} from the rig and every animation keyframe. Do not edit: run',
		' * {@code python3 tools/build_assets.py}. See {@link PoseTrack} for the layout.',
		' *',
		' * <p>Assets: this data comes from the model, which derives from the "Ender Dragon Reborn" resource pack by',
		' * Parrie43, All Rights Reserved. The LGPL does not cover it: see LICENSE-ASSETS.md.',
		' */',
		'final class PoseData {',
		f'\tstatic final String[] PART_NAMES = {{{", ".join(q(p[0]) for p in PARTS)}}};',
		f'\tstatic final float[] PART_WIDTH = {{{", ".join(fl(p[3]) for p in PARTS)}}};',
		f'\tstatic final float[] PART_HEIGHT = {{{", ".join(fl(p[4]) for p in PARTS)}}};',
		f'\tstatic final int[] PART_CHAIN = {{{", ".join(str(c[0]) for c in chains)}}};',
		f'\tstatic final int[] PART_DEPTH = {{{", ".join(str(c[1]) for c in chains)}}};',
		f'\tstatic final int NECK_PIVOTS = {len(NECK_PIVOTS)}, TAIL_PIVOTS = {len(TAIL)}, FRAME_POINTS = {2 * len(WINGS) + 3 + 2};',
		'\t/** Rest pivot of every tail segment, editor space, blocks: x, y, z. */',
		f'\tstatic final double[] TAIL_REST = {{{", ".join(f"{v:g}" for v in tail_rest)}}};',
		'\t/** Per tail segment, the capsule round its main cube at rest: axis start, axis end, radius (blocks). */',
		f'\tstatic final double[] TAIL_CAPSULE = {{{", ".join(f"{v:g}" for v in tail_capsules)}}};',
		f'\tstatic final String[] ANIMS = {{{", ".join(q(m[0]) for m in meta)}}};',
		f'\tstatic final float[] LENGTH = {{{", ".join(fl(m[1]) for m in meta)}}};',
		f'\tstatic final boolean[] LOOP = {{{", ".join("true" if m[2] else "false" for m in meta)}}};',
		f'\tstatic final float[] STEP = {{{", ".join(fl(m[3]) for m in meta)}}};',
		f'\tstatic final int[] FRAMES = {{{", ".join(str(m[4]) for m in meta)}}};',
		'\tstatic final String[] DATA = {',
	]
	lines += [f'\t\t{_java_string(d)},' for d in data]
	lines += ['\t};',
			  '\t/** Flight tails (anims.tail_track): per keyframe of the animation, 9 bends down, 9 right, rest, lift; int16 / 64. */',
			  f'\tstatic final String[] TAIL_ANIMS = {{{", ".join(q(n) for n in tail_names)}}};',
			  '\tstatic final String[] TAIL_DATA = {']
	lines += [f'\t\t{_java_string(d)},' for d in tail_data]
	lines += ['\t};', '', '\tprivate PoseData() {}', '}', '']
	os.makedirs(os.path.dirname(OUT), exist_ok=True)
	with open(OUT, 'w') as f:
		f.write('\n'.join(lines))
	print(f'pose track: {len(PARTS)} parts, {len(PIVOTS)} pivots, {sum(m[4] for m in meta)} frames -> '
		  f'{os.path.relpath(OUT, ROOT)}')
