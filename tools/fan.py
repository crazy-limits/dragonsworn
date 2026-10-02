"""The accordion wing-hand, exactly.

Each hand has 4 fingers radiating from one apex, 30 degrees apart at rest (tip2, tip3, tip5, tip6).
Each gap holds two 15-degree wedge slices, one hinged on each finger. Tilting both slices of a gap by
theta (mirrored) about their fingers moves their free edges to the in-plane angle
atan(tan 15 * cos theta) from each finger. Setting the gap to exactly twice that angle puts both free
edges in the gap's bisector plane, where they are mirror images and coincide from apex to tip:

	gap(theta) = 2 * atan(tan 15deg * cos theta)   <=>   cos theta = tan(gap / 2) / tan 15deg

Fingers and slices are always driven together from theta, so slices never cross and never open a gap.
The fan closes toward the sail (the arm membrane on wing_tip), which must not move: the leading finger
swings back by the total closure and every later finger rotates back by its own gap's closure, so the
last one's net rotation is exactly zero. That last "finger" is tip7, a crease in the sail 30 degrees past
tip6 (no cube): the gap between tip6 and it (slices web6 on tip6, web7 on tip7) folds the sail into the
hand, with SAIL x the pleat of its neighbour gap, so the pleats fade out into the flat sail.

All angles here are Blockbench editor values in degrees.
"""
import math

D = math.pi / 180
TAN15 = math.tan(15 * D)
FINGERS = ('tip3', 'tip5', 'tip6', 'tip7')
SAIL = 0.5      # the sail gap's pleat, as a share of the hand's last gap's
# Which side of the membrane the pleats rise to: +1 folds the web up (above the fingers), -1 down.
# The crease math is mirror-symmetric about the membrane plane, so both are exact.
PLEAT = 1


def gap_from_pleat(theta):
	return 2 * math.atan(TAN15 * math.cos(theta * D)) / D


def pleat_from_fan(fan):
	"""Pleat angle that closes every finger gap by `fan` degrees (0 = spread, 30 = shut)."""
	if fan <= 0:
		return 0.0
	return math.acos(min(1.0, math.tan((30 - fan) / 2 * D) / TAN15)) / D


def wing_fan(theta, side='both'):
	"""Bone rotations for pleat angle `theta`: one number, or one per hand gap [g0, g1, g2] (the sail gap
	g3 follows g2 by SAIL)."""
	t = list(theta) if isinstance(theta, (list, tuple)) else [theta] * 3
	t.append(SAIL * t[2])
	out, total = {}, 0.0
	for g in range(4):
		close = 30 - gap_from_pleat(t[g])
		total += close
		if side != 'right':
			out[f'left_wing_{FINGERS[g]}'] = {'r': [0, -close, 0]}
			out[f'left_wing_web{2 * g}'] = {'r': [-PLEAT * t[g], 0, 0]}
			out[f'left_wing_web{2 * g + 1}'] = {'r': [PLEAT * t[g], 0, 0]}
		if side != 'left':
			out[f'right_wing_{FINGERS[g]}'] = {'r': [0, close, 0]}
			out[f'right_wing_web{2 * g}'] = {'r': [-PLEAT * t[g], 0, 0]}      # the right wing is the left's mirror:
			out[f'right_wing_web{2 * g + 1}'] = {'r': [PLEAT * t[g], 0, 0]}   # turns (x, -y, -z)
	if side != 'right':
		out['left_wing_tip2'] = {'r': [0, total, 0]}
	if side != 'left':
		out['right_wing_tip2'] = {'r': [0, -total, 0]}
	return out


def fingers(fan, side='both'):
	"""Close every finger gap by `fan` degrees; the pleat follows exactly."""
	return wing_fan(pleat_from_fan(fan), side)


# The wrist twist: the hand turned about the sail's crease (tip7, 120 degrees round from tip2), the one line
# the sail and the hand share. Every hand bone is a child of tip2, and tip7's net turn is zero whatever the
# fan does, so the crease is a fixed line through the apex in tip2's parent frame: turning tip2 about it
# moves the whole hand and leaves that line (the seam to the sail) where it is. The seam folds, it never opens.
_C120, _S120 = math.cos(120 * D), math.sin(120 * D)
CREASE = (-_C120, 0.0, _S120)   # Ry(120) applied to the fingers' direction (-x), left wing, editor space


def _axis_angle(axis, deg):
	x, y, z = axis
	c, s = math.cos(deg * D), math.sin(deg * D)
	k = 1 - c
	return [[c + x * x * k, x * y * k - z * s, x * z * k + y * s],
			[y * x * k + z * s, c + y * y * k, y * z * k - x * s],
			[z * x * k - y * s, z * y * k + x * s, c + z * z * k]]


def wrist_twist(pose, deg, side='both'):
	"""Turns the hand `deg` degrees about the sail's crease, on top of the fan already in `pose` (tip2's Y)."""
	sides = ('left', 'right') if side == 'both' else (side,)
	for s in sides:
		sgn = 1 if s == 'left' else -1
		fan_y = sgn * pose[f'{s}_wing_tip2']['r'][1]
		t = _axis_angle(CREASE, deg)
		c, n = math.cos(fan_y * D), math.sin(fan_y * D)
		m = [[t[i][0] * c - t[i][2] * n, t[i][1], t[i][0] * n + t[i][2] * c] for i in range(3)]   # T . Ry(fan)
		# Euler ZYX (the rig's order) of m
		ry = math.asin(max(-1.0, min(1.0, -m[2][0])))
		rx, rz = math.atan2(m[2][1], m[2][2]), math.atan2(m[1][0], m[0][0])
		pose[f'{s}_wing_tip2'] = {'r': [rx / D, sgn * ry / D, sgn * rz / D]}   # the right wing: (x, -y, -z)
	return pose
