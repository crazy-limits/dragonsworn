"""The wing-walk: the dragon walks on its hind feet and on the wrists of its folded wings.

A lateral-sequence walk (LH -> LF -> RH -> RF, one limb in the air at a time, so three are always
down), solved by inverse kinematics on the real rig every frame:

* each limb has a home point on the ground (y = 0 of the model). In stance its foot slides straight
  back at the body's walking speed, so in the world it stays planted; in swing it travels forward on a
  lifted arc;
* the hind legs are a two-bone chain (thigh, shin) solved for the ankle, with the foot counter-rotated so
  the sole stays flat;
* the front limbs are the wings. The wrist claw is the foot. The arm solves shoulder (3 axes) + elbow
  (Z only), with the shoulder's pitch held near a target so the folded finger bundle rises up and back
  along the body instead of dragging on the ground. The hand fan stays closed (see fan.py), and only
  the shoulder (which moves the whole wing rigidly) and the elbow hinge (which lies on the seam between
  the inner membrane and the sail) move, so the gap-free fold is untouched.

Every residual converges to ~0, so a planted foot is still to within a hundredth of a pixel.
"""
import math

from fan import fingers
from ik import solve
from rig import apply, default_rig

LENGTH = 2.4            # seconds per full cycle (each limb steps once)
DUTY = 0.75             # fraction of the cycle a foot is on the ground
STRIDE = 36.0           # px a foot travels relative to the body during stance
SPEED = STRIDE / (DUTY * LENGTH)   # px per second; the body must move this fast for zero slip
SWING_START = {'lh': 0.0, 'lf': 0.25, 'rh': 0.5, 'rf': 0.75}
LIFT = {'lh': 9.0, 'rh': 9.0, 'lf': 16.0, 'rf': 16.0}
FAN = 18                # every finger gap closed by 18 of 30 degrees: 12-degree gaps, folded but fanned
SHOULDER_PITCH = -25.0  # front-down pitch of the whole wing: tilts the finger bundle up behind the wrist
BODY_PITCH = 6.0        # chest up: the arms are much longer than the legs
BODY_DROP = -3.0        # lower the body so the hind legs keep a bend at full stride

LEG_REF = [-20.0, 40.0]   # thigh x, shin x of a crouched hind leg
ARM_REF = [SHOULDER_PITCH, -12.0, -25.0, 106.0]   # shoulder x, y, z and elbow z of the reference stance

# home points (editor space, model y = 0 is the ground)
HOME = {'lh': [-16.0, 3.0, 26.0], 'rh': [16.0, 3.0, 26.0], 'lf': [-72.0, 0.0, -60.0], 'rf': [72.0, 0.0, -60.0]}

rig = default_rig()
SIDE = {'l': 'left', 'r': 'right'}


def softmin(values, k=0.3):
	"""Smooth minimum (always <= the true minimum, within k*ln(n)), so the solver sees no kinks when
	the lowest point of the hand switches from one corner to another."""
	lo = min(values)
	return lo - k * math.log(sum(math.exp(-(v - lo) / k) for v in values))


def _smooth(u):
	return u * u * (3 - 2 * u)


def foot_target(limb, t):
	"""Where `limb`'s contact point is at time t (root frame), and whether it is planted."""
	phase = (t / LENGTH - SWING_START[limb]) % 1.0
	swing = 1 - DUTY
	hx, hy, hz = HOME[limb]
	if phase < swing:
		u = phase / swing
		s = _smooth(u)
		z = hz + STRIDE / 2 - STRIDE * s          # back -> front (front is -z)
		y = hy + LIFT[limb] * math.sin(math.pi * u)
		return [hx, y, z], False
	u = (phase - swing) / DUTY
	return [hx, hy, hz - STRIDE / 2 + STRIDE * u], True


def body_pose(t):
	w = 2 * math.pi * t / LENGTH
	# Two small dips per cycle (one per hind footfall) and a roll toward the side bearing the weight.
	return {
		'p': [0, BODY_DROP + 1.0 * math.cos(2 * w), 0],
		'r': [BODY_PITCH + 1.0 * math.sin(2 * w + 0.6), 2.5 * math.sin(w + 0.4), 2.0 * math.sin(w - 0.3)],
	}


# The front foot is the wrist: the fan apex is held at the target's x/z, and the lowest point of the
# whole folded hand (claw or pleat edge, whichever is lower in that pose) touches the ground.
def _claw_point(side):
	cube = rig.bones[f'{side}_wing_tip6']['cubes'][2]
	lo, hi = cube['min'], cube['max']
	return [(lo[0] + hi[0]) / 2, lo[1], (lo[2] + hi[2]) / 2]


CLAW = {s: _claw_point(s) for s in ('left', 'right')}
HAND = {s: rig.subtree(f'{s}_wing_tip2') for s in ('left', 'right')}
APEX = {s: rig.bones[f'{s}_wing_tip3']['pivot'] for s in ('left', 'right')}
ANKLE = {s: rig.bones[f'foot_{s}']['pivot'] for s in ('left', 'right')}


def solve_limbs(pose, targets, sol, arm_ref=None, shoulder_pitch=None, elbow=None):
	"""Solves every limb named in `targets` (lh, rh, lf, rf -> root-frame target) into `pose`.

	Hind limbs: the ankle reaches its target and the foot counter-rotates so the sole stays level.
	Front limbs (the wings): the fan apex reaches the target's x/z and the lowest point of the folded
	hand touches its y. `elbow` (degrees) pins the elbow instead of solving it. `sol` carries each limb's
	last solution to seed the next frame."""
	arm_ref = arm_ref or ARM_REF
	shoulder_pitch = SHOULDER_PITCH if shoulder_pitch is None else shoulder_pitch
	for limb in ('lh', 'rh'):
		if limb not in targets:
			continue
		side = SIDE[limb[0]]
		target = targets[limb]

		def res(x, side=side, target=target):
			p = dict(pose)
			p[f'upperleg_{side}'] = {'r': [x[0], 0, 0]}
			p[f'lowerleg_{side}'] = {'r': [x[1], 0, 0]}
			m = rig.matrices(p)
			a = apply(m[f'lowerleg_{side}'], ANKLE[side])
			# A faint pull toward a crouched reference keeps the hock bending backward (the branch the
			# rest pose is on) instead of flipping through to the mirror solution.
			return [a[1] - target[1], a[2] - target[2], 0.002 * (x[0] - LEG_REF[0]), 0.002 * (x[1] - LEG_REF[1])]

		x, _ = solve(res, sol.get(limb, LEG_REF))
		sol[limb] = x
		pitch = pose['body']['r'][0]
		pose[f'upperleg_{side}'] = {'r': [x[0], 0, 0]}
		pose[f'lowerleg_{side}'] = {'r': [x[1], 0, 0]}
		pose[f'foot_{side}'] = {'r': [-(pitch + x[0] + x[1]), 0, 0]}

	for limb in ('lf', 'rf'):
		if limb not in targets:
			continue
		side = SIDE[limb[0]]
		sgn = 1 if side == 'left' else -1
		target = targets[limb]

		def res(x, side=side, sgn=sgn, target=target):
			p = dict(pose)
			p[f'{side}_wing'] = {'r': [x[0], sgn * x[1], sgn * x[2]]}
			p[f'{side}_wing_tip'] = {'r': [0, 0, sgn * x[3]]}
			m = rig.matrices(p)
			c = apply(m[f'{side}_wing_tip3'], APEX[side])
			low = softmin([q[1] for b in HAND[side] for q in rig.corners(m, b)])
			# The arm has one more degree of freedom than the wrist needs. Soft pulls toward a reference
			# stance make the solution unique, so it moves smoothly from frame to frame.
			# With the elbow pinned the wrist fixes all three shoulder angles: the pulls only pick the branch.
			k = 1.0 if elbow is None else 0.01
			r = [c[0] - target[0], low - target[1], c[2] - target[2],
				 0.05 * k * (x[0] - shoulder_pitch), 0.01 * k * (x[1] - arm_ref[1]), 0.01 * k * (x[2] - arm_ref[2])]
			return r if elbow is None else r + [10 * (x[3] - elbow)]

		x, _ = solve(res, sol.get(limb, arm_ref), iterations=200)
		if elbow is not None:
			x[3] = elbow
		sol[limb] = x
		pose[f'{side}_wing'] = {'r': [x[0], sgn * x[1], sgn * x[2]]}
		pose[f'{side}_wing_tip'] = {'r': [0, 0, sgn * x[3]]}
	return pose


def solve_frame(t, prev=None):
	"""Full walk pose at time t. `prev` (the previous frame's solution) seeds the solver for continuity."""
	pose = dict(fingers(FAN))
	pose['body'] = body_pose(t)
	sol = dict(prev or {})
	solve_limbs(pose, {limb: foot_target(limb, t)[0] for limb in ('lh', 'rh', 'lf', 'rf')}, sol)

	w = 2 * math.pi * t / LENGTH
	# Neck and head stay level while the shoulders rock (bird-like head stabilisation). The tail's sway
	# is procedural (body/TailMotion.java).
	pose['neck_rot1'] = {'r': [-BODY_PITCH * 0.6 + 2 * math.cos(2 * w + 0.5), -3 * math.sin(w), 0]}
	pose['neck_rot2'] = {'r': [-1.5 * math.cos(2 * w + 1.0), -2.5 * math.sin(w + 0.4), 0]}
	pose['head_group'] = {'r': [-BODY_PITCH * 0.4 - 1.5 * math.cos(2 * w + 1.5), 3 * math.sin(w + 0.8), 0]}
	pose['jaw_group'] = {'r': [-1 - math.sin(2 * w), 0, 0]}
	return pose, sol


def contacts(pose):
	"""World (root-frame) positions of the four contact points for a solved pose."""
	m = rig.matrices(pose)
	return {
		'lh': apply(m['lowerleg_left'], ANKLE['left']), 'rh': apply(m['lowerleg_right'], ANKLE['right']),
		'lf': apply(m['left_wing_tip3'], APEX['left']), 'rf': apply(m['right_wing_tip3'], APEX['right']),
	}
