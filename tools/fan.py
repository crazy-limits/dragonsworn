"""The accordion wing-hand, exactly.

Each hand has 4 fingers radiating from one apex, 30 degrees apart at rest (tip2, tip3, tip5, tip6).
Each gap holds two 15-degree wedge slices, one hinged on each finger. Tilting both slices of a gap by
theta (mirrored) about their fingers moves their free edges to the in-plane angle
atan(tan 15 * cos theta) from each finger. Setting the gap to exactly twice that angle puts both free
edges in the gap's bisector plane, where they are mirror images and coincide from apex to tip:

	gap(theta) = 2 * atan(tan 15deg * cos theta)   <=>   cos theta = tan(gap / 2) / tan 15deg

Fingers and slices are always driven together from theta, so slices never cross and never open a gap.
The fan closes toward its trailing finger (tip6), which borders the sail and must not move: the leading
finger swings back by the total closure and every later finger rotates back by its own gap's closure,
so tip6's net rotation is exactly zero.

All angles here are Blockbench editor values in degrees.
"""
import math

D = math.pi / 180
TAN15 = math.tan(15 * D)
FINGERS = ('tip3', 'tip5', 'tip6')
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
	"""Bone rotations for pleat angle `theta`: one number, or one per gap [g0, g1, g2]."""
	t = theta if isinstance(theta, (list, tuple)) else [theta] * 3
	out, total = {}, 0.0
	for g in range(3):
		close = 30 - gap_from_pleat(t[g])
		total += close
		if side != 'right':
			out[f'left_wing_{FINGERS[g]}'] = {'r': [0, -close, 0]}
			out[f'left_wing_web{2 * g}'] = {'r': [-PLEAT * t[g], 0, 0]}
			out[f'left_wing_web{2 * g + 1}'] = {'r': [PLEAT * t[g], 0, 0]}
		if side != 'left':
			out[f'right_wing_{FINGERS[g]}'] = {'r': [0, close, 0]}
			out[f'right_wing_web{2 * g}'] = {'r': [PLEAT * t[g], 0, 0]}
			out[f'right_wing_web{2 * g + 1}'] = {'r': [-PLEAT * t[g], 0, 0]}
	if side != 'right':
		out['left_wing_tip2'] = {'r': [0, total, 0]}
	if side != 'left':
		out['right_wing_tip2'] = {'r': [0, -total, 0]}
	return out


def fingers(fan, side='both'):
	"""Close every finger gap by `fan` degrees; the pleat follows exactly."""
	return wing_fan(pleat_from_fan(fan), side)
