"""The wing-walk: the dragon walks on its hind feet and on the wrists of its folded wings.

A lateral-sequence walk (LH -> LF -> RH -> RF, one limb in the air at a time, so three are always
down), solved by inverse kinematics on the real rig every frame:

* each limb has a home point on the ground (y = 0 of the model). In stance its foot slides straight
  back at the body's walking speed, so in the world it stays planted; in swing it travels forward on a
  lifted arc;
* the hind legs are a two-bone chain (thigh, shin) solved for the ankle, with the foot counter-rotated so
  the sole stays flat;
* the front limbs are the wings. The wrist claw is the foot: it is what stands on the ground, and every
  other part of the folded hand stays above it. The arm solves shoulder (3 axes) + elbow (Z only): the
  upper arm reaches out from the shoulder, the forearm comes down to the claw like a front leg, and the
  folded hand lies back along the flank. The hand fan stays closed (see fan.py), and only
  the shoulder (which moves the whole wing rigidly) and the elbow hinge (which lies on the seam between
  the inner membrane and the sail) move, so the gap-free fold is untouched.

Every residual converges to ~0, so a planted foot is still to within a hundredth of a pixel.
"""
import math

from fan import fingers, wrist_twist
from ik import solve
from rig import apply, default_rig

LENGTH = 2.4            # seconds per full cycle (each limb steps once)
DUTY = 0.75             # fraction of the cycle a foot is on the ground
STRIDE = 36.0           # px a foot travels relative to the body during stance
SPEED = STRIDE / (DUTY * LENGTH)   # px per second; the body must move this fast for zero slip
SWING_START = {'lh': 0.0, 'lf': 0.25, 'rh': 0.5, 'rf': 0.75}
LIFT = {'lh': 9.0, 'rh': 9.0, 'lf': 16.0, 'rf': 16.0}
FAN = 18                # every finger gap closed by 18 of 30 degrees: 12-degree gaps, folded but fanned
SHOULDER_PITCH = -33.0  # front-down pitch of the whole wing: the forearm stands on the claw, the hand lies back
BODY_PITCH = 6.0        # chest up: the arms are much longer than the legs
BODY_DROP = -3.0        # lower the body so the hind legs keep a bend at full stride

LEG_REF = [-20.0, 40.0]   # thigh x, shin x of a crouched hind leg
ARM_REF = [SHOULDER_PITCH, -14.0, -25.0, 92.0]   # shoulder x, y, z and elbow z of the reference stance

# home points (editor space, model y = 0 is the ground)
HOME = {'lh': [-16.0, 3.0, 26.0], 'rh': [16.0, 3.0, 26.0], 'lf': [-90.0, 0.0, -60.0], 'rf': [90.0, 0.0, -60.0]}

rig = default_rig()
SIDE = {'l': 'left', 'r': 'right'}


def softmin(values, k=0.3):
	"""Smooth minimum (always <= the true minimum, within k*ln(n)), so the solver sees no kinks when
	the lowest point switches from one corner to another."""
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


# The front foot is the wrist claw (cube 2 of the trailing finger, as LimbAnimator.java reads it): its
# middle is held at the target's x/z and its lowest corner at the target's y, while every other part of
# the folded hand stays above it -- the claw stands on the ground, not the hand.
CLAW_CUBE = 2


def _box(cube):
	lo, hi = cube['min'], cube['max']
	return [[x, y, z] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]


def _claw_point(side):
	cube = rig.bones[f'{side}_wing_tip6']['cubes'][CLAW_CUBE]
	lo, hi = cube['min'], cube['max']
	return [(lo[0] + hi[0]) / 2, lo[1], (lo[2] + hi[2]) / 2]


CLAW = {s: _claw_point(s) for s in ('left', 'right')}
CLAW_BOX = {s: _box(rig.bones[f'{s}_wing_tip6']['cubes'][CLAW_CUBE]) for s in ('left', 'right')}
HAND = {s: rig.subtree(f'{s}_wing_tip2') for s in ('left', 'right')}
# the folded hand without its claw: (bone, corners in the bone's rest space)
HAND_REST = {s: [(b, q) for b in HAND[s] for i, c in enumerate(rig.bones[b]['cubes'])
				 if not (b == f'{s}_wing_tip6' and i == CLAW_CUBE) for q in _box(c)] for s in ('left', 'right')}
CLAW_CLEAR = 0.5        # px every other part of the hand keeps above the claw's sole
# A laid hand (`hand_laid`) lies on the ground along its leading finger, from the wrist (the claw and the
# finger's root) to its tip: the bottom corners at each end of tip2 (cube 0; its outer end is -x on the left)
FINGER_ROOTS, FINGER_ENDS = ({s: [(f'{s}_wing_tip2', [c['max' if (s == 'left') == root else 'min'][0], c['min'][1], z])
								  for c in [rig.bones[f'{s}_wing_tip2']['cubes'][0]]
								  for z in (c['min'][2], c['max'][2])] for s in ('left', 'right')} for root in (True, False))
APEX = {s: rig.bones[f'{s}_wing_tip3']['pivot'] for s in ('left', 'right')}
ANKLE = {s: rig.bones[f'foot_{s}']['pivot'] for s in ('left', 'right')}


def solve_limbs(pose, targets, sol, arm_ref=None, shoulder_pitch=None, hand_laid=False):
	"""Solves every limb named in `targets` (lh, rh, lf, rf -> root-frame target) into `pose`.

	Hind limbs: the ankle reaches its target and the foot counter-rotates so the sole stays level.
	Front limbs (the wings): the wrist claw stands on the target (its middle at x/z, its sole at y) with
	the rest of the folded hand above it. With `hand_laid` the hand lies on the ground along its leading
	finger instead (the claw's middle still on the target's x/z, the wrist's lowest point at its y, the
	finger's tip level with it, the rest of the hand above), the hand free to turn by the wrist twist
	(`fan.wrist_twist`, a fifth unknown; `arm_ref` then has five values, the twist pulled toward its own).
	`sol` carries each limb's last solution to seed the next frame."""
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

		def arm(p, x, side=side, sgn=sgn):
			p[f'{side}_wing'] = {'r': [x[0], sgn * x[1], sgn * x[2]]}
			p[f'{side}_wing_tip'] = {'r': [0, 0, sgn * x[3]]}
			if hand_laid:
				p[f'{side}_wing_tip2'] = pose[f'{side}_wing_tip2']   # the fan, untwisted
				wrist_twist(p, x[4], side)

		def res(x, side=side, target=target):
			p = dict(pose)
			arm(p, x)
			m = rig.matrices(p)
			claw = [apply(m[f'{side}_wing_tip6'], q) for q in CLAW_BOX[side]]
			# The arm has one more degree of freedom than the claw needs. Soft pulls toward a reference
			# stance make the solution unique, so it moves smoothly from frame to frame.
			if hand_laid:
				# laid: the wrist on the ground, the leading finger along it to its tip, the rest of the hand
				# fanned up above it (toward the forearm, which arches back up to the body)
				wrist = softmin([q[1] for q in claw] + [apply(m[b], q)[1] for b, q in FINGER_ROOTS[side]])
				tip = softmin([apply(m[b], q)[1] for b, q in FINGER_ENDS[side]])
				rest = softmin([apply(m[b], q)[1] for b, q in HAND_REST[side] if b != f'{side}_wing_tip2'])
				fit = [wrist - target[1], tip - wrist, 2.0 * min(0.0, rest - wrist - CLAW_CLEAR)]
			else:
				sole = softmin([q[1] for q in claw])
				hand = softmin([apply(m[b], q)[1] for b, q in HAND_REST[side]])
				fit = [sole - target[1], 2.0 * min(0.0, hand - sole - CLAW_CLEAR)]
			return [sum(q[0] for q in claw) / 8 - target[0], sum(q[2] for q in claw) / 8 - target[2]] + fit + \
				[0.05 * (x[0] - shoulder_pitch), 0.01 * (x[1] - arm_ref[1]), 0.01 * (x[2] - arm_ref[2])] + \
				([0.02 * (x[4] - arm_ref[4])] if hand_laid else [])

		# Seeded from the last frame, and from the reference stance if that misses (a fast lunge can
		# throw the arm into another basin): of the solutions that stand on the mark, the one nearest the
		# last frame wins, so the arm never jumps branches.
		hard = 5 if hand_laid else 4

		def miss(x):   # the claw's distance from its mark, squared (the soft pulls left out)
			return sum(v * v for v in res(x)[:hard])

		last = sol.get(limb, arm_ref)
		found = [solve(res, last, iterations=200)[0]]
		if miss(found[0]) > 1e-4 and limb in sol:
			found.append(solve(res, arm_ref, iterations=200)[0])
		reach = [f for f in found if miss(f) <= 1e-4]
		x = min(reach, key=lambda f: sum((f[i] - last[i]) ** 2 for i in range(len(f)))) if reach else min(found, key=miss)
		sol[limb] = x
		arm(pose, x)
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
	pose['jaw_group'] = {'r': [-1.5, 0, 0]}
	return pose, sol


def contacts(pose):
	"""World (root-frame) positions of the four contact points for a solved pose."""
	m = rig.matrices(pose)
	return {
		'lh': apply(m['lowerleg_left'], ANKLE['left']), 'rh': apply(m['lowerleg_right'], ANKLE['right']),
		'lf': apply(m['left_wing_tip6'], CLAW['left']), 'rf': apply(m['right_wing_tip6'], CLAW['right']),
	}
