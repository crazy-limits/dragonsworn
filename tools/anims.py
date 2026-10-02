"""Generates out/ender_dragon.animation.json (GeckoLib format) from pose functions.

Conventions (editor values, verified on this rig):
  +X pitches a bone's front (-Z end) up: neck/head up, tail end down, legs swing forward; -X opens the jaw.
  left wing/tip: -Z raises; right wing/tip: +Z raises. Wingtip +Y (left) / -Y (right) folds it back.
The file stores rotations with X and Y negated (Blockbench's Bedrock export does the same flip).

Every wing pose keeps the gap-free fold rule: the shoulder may rotate freely (it moves the whole wing
rigidly), the elbow only about Z (its hinge lies on the inner-membrane/sail seam), and the hand only
through fan.wing_fan.
"""
import json
import math
import os

import flight
import parts
import stand
import walk
from chain import NECK, TAIL, expand
from fan import fingers, wing_fan
from rig import apply

HERE = os.path.dirname(os.path.abspath(__file__))
S, C, PI2 = math.sin, math.cos, 2 * math.pi

# folded wing pose used on the ground: arms raised in an arch, hands draped down the flank, fan closed
FOLD = {'lw': -70, 'lwt': 150, 'rw': 70, 'rwt': -150}


def folded(breathe=0.0, fan=walk.FAN):
	out = {
		'left_wing': {'r': [0, 0, FOLD['lw'] - breathe]}, 'left_wing_tip': {'r': [0, 0, FOLD['lwt']]},
		'right_wing': {'r': [0, 0, FOLD['rw'] + breathe]}, 'right_wing_tip': {'r': [0, 0, FOLD['rwt']]},
	}
	out.update(fingers(fan))
	return out


def ease(x):
	x = max(0.0, min(1.0, x))
	return x * x * (3 - 2 * x)


_stands = {}


def standing(key):
	"""One Stand solver per animation, so its frames are solved in order (each seeds the next)."""
	return _stands.setdefault(key, stand.Stand())


def idle(t, L):
	# Perched: breathing lifts the chest, the swan neck sways and the head looks around.
	w = PI2 * t / L
	sw = stand.SWAN
	return standing('idle').pose(
		body_pitch=1.5 * S(w), body_lift=0.8 * S(w), fan=stand.FAN - 1 + S(w),
		swan=[sw[0] + 2 * S(w + 0.3), sw[1] + 1.5 * S(w + 0.6), sw[2] - 2 * S(w + 0.9), sw[3] - 2 * S(w + 1.2)],
		neck_yaw=(3 * S(w / 2 * 2), 4 * S(w + 0.4), 5 * S(w + 0.8), 6 * S(w + 1.2)), head_yaw=8 * S(w + 1.6),
		jaw=-1.5)


_walk_cache = {}


def walk_pose(t, L):
	# Solved by IK (walk.py); frames are solved in order so each seeds the next.
	key = round(t, 4)
	if key not in _walk_cache:
		prev = _walk_cache.get('_prev')
		pose, sol = walk.solve_frame(t, prev)
		_walk_cache[key] = pose
		_walk_cache['_prev'] = sol
	return _walk_cache[key]


# ---------------------------------------------------------------- flight
# The wingbeat and the body's answer to it are in flight.py. Every flying pose goes through
# flight_pose: wings, body, the head held still by the neck, the legs, and hints for the tail (the
# game's: tail_track below turns them into TailMotion's flight tails).

FLAP = flight.FLAP
GLIDE_WING = {'shoulder': 8.0, 'elbow': -6.0, 'sweep': 3.0, 'twist': 0.0, 'pleat': 3.0}
FLY_HEIGHT = 6.0                # body raised (px) in flight: the feet hang below the model's origin
NECK_FLY, HEAD_FLY = [2.0, 1.0, 0.0, 0.0], -5.0   # a gentle upward curve, the head looking ahead
# the neck's share of a bend that carries the head (base to head): the base does most of it
NECK_SHARE = [0.45, 0.3, 0.17, 0.08]
# thigh, shin, foot: tucked back under the tail like an eagle's; hanging under an upright body
LEGS_TUCKED = (-50.0, -20.0, -50.0)
LEGS_DANGLE = (-28.0, 18.0, 26.0)
# landing gear: thighs swung forward, shins reaching down and forward, toes pointing ahead
LEGS_FORWARD = (38.0, -26.0, 46.0)
HEAD_PIVOT = walk.rig.bones['head_group']['pivot']


def qblend(a, b, k):
	"""Blend two flight parameter sets (numbers, lists and tuples blend per element)."""
	out = {}
	for key in set(a) | set(b):
		x, y = a.get(key, b.get(key)), b.get(key, a.get(key))
		if isinstance(x, (list, tuple)):
			out[key] = type(x)(p + (r - p) * k for p, r in zip(x, y))
		else:
			out[key] = x + (y - x) * k
	return out


def _head(pose):
	"""Height of the head's pivot and the head's pitch in the model (only the body-neck-head chain)."""
	mats = walk.rig.matrices({b: pose[b] for b in ['body'] + NECK + ['head_group'] if b in pose})
	y = apply(mats['head_group'], HEAD_PIVOT)[1]
	pitch = pose['body']['r'][0] + sum(pose[n]['r'][0] for n in NECK) + pose['head_group']['r'][0]
	return y, pitch


def _neck(pose, base, bend):
	for k, seg in enumerate(NECK):
		pose[seg] = {'r': [base[k] + bend * NECK_SHARE[k], pose.get(seg, {'r': [0, 0, 0]})['r'][1], 0]}


def flight_pose(q):
	"""A flying pose from parameters:
	wings: shoulder, elbow, sweep, twist, pleat or pleats (one per finger gap; flight.wings);
	body: heave (px), pitch (deg, chest up), surge (px forward), roll, yaw;
	neck/head: `neck` (base curve), `head` (base pitch), `neck_yaw`, `head_yaw`, `still` (the body pitch
	the base curve is made for), `lag` = (heave, pitch) a moment ago and `follow` = (share of the heave,
	share of the pitch) the head takes from it: the neck is solved so the head stays where the base
	curve holds it on a still body, plus that share;
	legs: `legs` (thigh, shin, foot) and `leg_swing` (deg, + swings the feet down and forward); `jaw`;
	tail hints (`push`, `droop`, `sway`, `stand`, `lift`), kept in the pose under '_tail'."""
	pitch, heave = q['pitch'], q['heave']
	p = {
		'body': {'p': [0, FLY_HEIGHT + heave, -q.get('surge', 0.0)], 'r': [pitch, q.get('yaw', 0.0), q.get('roll', 0.0)]},
		'left_wing': {'r': [q['twist'], q['sweep'], -q['shoulder']]}, 'left_wing_tip': {'r': [0, 0, -q['elbow']]},
		'right_wing': {'r': [q['twist'], -q['sweep'], q['shoulder']]}, 'right_wing_tip': {'r': [0, 0, q['elbow']]},
	}
	# the pleats close from the leading finger to the trailing one: each gap a little behind the last
	p.update(wing_fan([max(0.0, v) for v in q.get('pleats', [q['pleat']] * 3)]))

	# the head: held where the neck's base curve puts it on a still body, plus a share of the motion
	base, head = q.get('neck', NECK_FLY), q.get('head', HEAD_FLY)
	neck_yaw = q.get('neck_yaw', (0.0,) * 4)
	for k, seg in enumerate(NECK):
		p[seg] = {'r': [base[k], neck_yaw[k], 0]}
	p['head_group'] = {'r': [head, q.get('head_yaw', 0.0), 0]}
	still = q.get('still', 0.0)
	ref = dict(p, body={'p': [0, FLY_HEIGHT, 0], 'r': [still, 0, 0]})
	y_still, pitch_still = _head(ref)
	lag_heave, lag_pitch = q.get('lag', (heave, pitch))
	fy, fp = q.get('follow', (0.3, 0.25))
	rigid = dict(p, body={'p': [0, FLY_HEIGHT + lag_heave, 0], 'r': [lag_pitch, 0, 0]})
	y_target = y_still + fy * (_head(rigid)[0] - y_still)
	# secant on the neck's bend
	b0, b1 = 0.0, -6.0
	_neck(p, base, b0)
	f0 = _head(p)[0] - y_target
	for _ in range(8):
		_neck(p, base, b1)
		f1 = _head(p)[0] - y_target
		if abs(f1) < 0.01 or f1 == f0:
			break
		b0, b1, f0 = b1, b1 - f1 * (b1 - b0) / (f1 - f0), f1
	b1 = max(-40.0, min(40.0, b1))
	_neck(p, base, b1)
	# the head's pitch: still, plus its share of the body's
	p['head_group']['r'][0] = head - (pitch - still) - b1 + fp * (lag_pitch - still)

	thigh, shin, foot = q.get('legs', LEGS_TUCKED)
	sw = q.get('leg_swing', 0.0)
	for side in ('left', 'right'):
		p[f'upperleg_{side}'] = {'r': [thigh + sw, 0, 0]}
		p[f'lowerleg_{side}'] = {'r': [shin - 0.4 * sw, 0, 0]}
		p[f'foot_{side}'] = {'r': [foot + 0.7 * sw, 0, 0]}
	p['jaw_group'] = {'r': [q.get('jaw', -1.5), 0, 0]}
	p['_tail'] = {k: q.get(k, 0.0) for k in ('push', 'droop', 'sway', 'stand', 'lift')}
	return p


def beat_q(style, u, gain=1.0):
	"""Flight parameters of a beat at phase u (gain scales the stroke and the body's answer)."""
	q = dict(flight.wings(style, u, gain))
	heave, pitch, surge = flight.body(style, u, gain)
	q.update(heave=heave, pitch=pitch, surge=surge, roll=0.0, yaw=0.0)
	# the head answers the body late, and only partly
	lh, lp, _ = flight.body(style, u - 0.06, gain)
	q['lag'] = (lh, lp)
	# the legs hang on by inertia: the body heaving up leaves them behind (swung down)
	q['leg_swing'] = 12.0 * gain * (flight.HEAVE(u) - flight.HEAVE(u - 0.1))
	q['push'] = gain * flight.downstroke(u)
	return q


def glide_q(t, L=3.0):
	"""The glide: wings still in a shallow V, the body rocking a little from wing to wing (a vulture's
	teeter), the neck steering against the roll, the tail swaying lazily."""
	w = PI2 * t / L
	q = dict(GLIDE_WING)
	q.update(shoulder=8 + 2.5 * S(w + 0.5), elbow=-6 - 2 * S(w), pleats=[3 + 2 * S(w - 0.3 * g) for g in range(3)], twist=1.5 * S(w + 1.0),
			 heave=1.2 * S(w), pitch=0.8 * S(w + 0.7), surge=0.0, roll=4 * S(w), yaw=0.0,
			 neck_yaw=(1.5 * S(w), 1.5 * S(w), 0.0, 0.0), head_yaw=4 * S(w + 0.5), jaw=-1.5, sway=S(w))
	q['lag'] = (1.2 * S(w - 0.4), 0.8 * S(w + 0.3))
	return q


def fly(t, L):
	"""Continuous flapping: climbing, slow flight, charges. Deep strokes; the body heaves on each."""
	return flight_pose(beat_q(flight.FLY, t / L))


def flap(t, L):
	"""One push out of a glide: the wings rise from the glide, sweep down hard, rise again and settle
	back into the glide, all in one beat."""
	u = t / FLAP
	env = ease(u / 0.2) * (1 - ease((u - 0.8) / 0.2))
	q = qblend(glide_q(0.0), beat_q(flight.PUSH, u), env)
	q['sway'] = 0.0
	return flight_pose(q)


def glide(t, L):
	return flight_pose(glide_q(t, L))


# Hovering: the body stands up in the air (chest up like the perched stance) and the stroke plane turns
# near horizontal: the wings sweep forward and back, every downstroke heaving the body (the game moves
# the dragon with it). The neck curls forward out of the raised chest so the head stays level.
HOVER_PITCH = 38.0
NECK_HOVER, HEAD_HOVER = [-6.0, -7.0, -8.0, -8.0], -2.0


def hover_q(u, gain=1.0):
	q = beat_q(flight.HOVER, u, gain)
	q.update(pitch=HOVER_PITCH + q['pitch'], surge=0.0, neck=NECK_HOVER, head=HEAD_HOVER, still=HOVER_PITCH,
			 legs=LEGS_DANGLE, droop=1.0)
	q['lag'] = (q['lag'][0], HOVER_PITCH + q['lag'][1])
	q['leg_swing'] *= 1.8   # dangling, they swing more
	return q


def hover(t, L):
	return flight_pose(hover_q(t / L))


def blend(a, b, k):
	"""Per-bone linear blend of two poses, k = 0 -> a, 1 -> b (tail hints included)."""
	out = {}
	for bone in set(a) | set(b):
		va, vb = a.get(bone, {}), b.get(bone, {})
		if bone == '_tail':
			out[bone] = {key: va.get(key, 0.0) + (vb.get(key, 0.0) - va.get(key, 0.0)) * k for key in set(va) | set(vb)}
			continue
		out[bone] = {key: [x + (y - x) * k for x, y in zip(va.get(key, [0, 0, 0]), vb.get(key, [0, 0, 0]))]
					 for key in set(va) | set(vb)}
	return out


def _wings(pose):
	"""Only the wing bones (arms and hand fans) of a pose."""
	return {b: v for b, v in pose.items() if '_wing' in b}


def _standing_tail(lift=0.0):
	return {'push': 0.0, 'droop': 0.0, 'sway': 0.0, 'stand': 1.0, 'lift': lift}


# The takeoff, a four-legged launch (pterosaurs and bats vault off their arms as well as their legs).
# 0-0.35 crouch: the body sinks, all four feet on the ground, the folded wings braced on their claws.
# 0.35-0.55 the push: hind legs and arms straighten together and throw the body up (the game throws the
# dragon at TAKEOFF_JUMP); the head is thrown up and forward. Nothing leaves the ground before the jump.
# 0.55-0.8 the wings sweep up off the ground to the top of the stroke while it rises on the leap; from
# 0.8 the first power stroke at the beat's own pace, the tail swinging against it, and the body stands
# up into the hover. It ends on the hover's beat boundary (u = 1), so the hover carries on in step (the
# game starts its beat at TAKEOFF_PHASE at the jump).
TAKEOFF_JUMP = 0.55
TAKEOFF_TOP = 0.8       # the wings at the top: the power stroke starts
TAKEOFF_LENGTH = round(TAKEOFF_TOP + (1 - flight.U_TOP) * FLAP, 2)
# where the game's hover beat is at the jump (DragonAnim.TAKEOFF_PHASE)
TAKEOFF_PHASE = flight.U_TOP - (TAKEOFF_TOP - TAKEOFF_JUMP) / FLAP
POWER = flight.Style(mid=10.0, amp=58.0, elbow_amp=28.0, sweep=2.0, sweep_amp=20.0, sweep8=4.0, heave=0.0,
					 pitch=0.0, surge=0.0, twist_amp=14.0, pleat_shut=36.0)


def _takeoff_u(t):
	"""Beat phase of the wings: from the ground up to the top by TAKEOFF_TOP, then the beat's own pace."""
	if t < TAKEOFF_TOP:
		return flight.U_TOP * ease((t - TAKEOFF_JUMP) / (TAKEOFF_TOP - TAKEOFF_JUMP))
	return flight.U_TOP + (t - TAKEOFF_TOP) / FLAP


_takeoff = {}


def takeoff(t, L):
	if t <= TAKEOFF_JUMP:
		crouch = ease(t / 0.35) * (1 - ease((t - 0.35) / 0.2))
		push = ease((t - 0.35) / 0.2)
		sw = stand.SWAN
		pose = standing('takeoff').pose(
			body_pitch=-7 * crouch + 9 * push, body_lift=-10 * crouch + 8 * push,
			swan=[sw[0] - 8 * crouch + 4 * push, sw[1] - 2 * crouch, sw[2] + 12 * push, sw[3] + 8 * push],
			head=stand.HEAD - 4 * crouch + 10 * push, jaw=-1.5)
		pose['_tail'] = _standing_tail(14 * push)
		return pose
	if 'lift' not in _takeoff:
		_takeoff['lift'] = takeoff(TAKEOFF_JUMP, L)
	lift = _takeoff['lift']
	u = _takeoff_u(t)
	# airborne: the power stroke, then the hover's beat; the body stands up into the hover
	q = hover_q(u % 1.0)
	q.update(qblend(flight.wings(POWER, u), flight.wings(flight.HOVER, u), ease((t - TAKEOFF_TOP - 0.5) / 0.4)))
	air = flight_pose(q)
	body = blend(lift, air, ease((t - TAKEOFF_JUMP) / (L - TAKEOFF_JUMP) * 1.6))
	# the wings come off the ground on their own, quicker than the body
	wings = blend(_wings(lift), _wings(air), ease((t - TAKEOFF_JUMP) / (TAKEOFF_TOP - TAKEOFF_JUMP)))
	body.update(wings)
	return body


# The landing at speed, an eagle's: it glides in and lands running instead of hovering down.
# 0-0.5 gear down: the legs swing forward (feet reaching ahead, toes pointing forward), the wings cup.
# 0.5-1.5 the flare: the body pitches up past 45 degrees, the stroke plane turns horizontal and one deep
# braking stroke sweeps forward and down, the tail drops (an air brake). 1.5-1.9 the wings rise, the
# legs reach for the ground, and over the last LAND_PLANT the wings sweep down onto their claws.
# 1.9 (LAND_TOUCH) all four feet strike together, the hind feet ahead of the body; the game puts the
# dragon on the ground then and skids it out to a stop (DragonAnim.LAND_TOUCH_SECONDS). 1.9-3.3 on
# all fours: the legs take the blow, the body rides forward over the planted feet and settles into the
# stance, the neck comes down into its swan curve.
LAND_FLARE, LAND_BRAKE, LAND_TOUCH, LAND_LENGTH = 0.5, 1.5, 1.9, 3.3
LAND_PLANT = 0.35       # the wings come down onto their claws over the last of the air
LAND_PITCH = 48.0
# where the hind feet strike, and where the body has carried them back to once stopped (stand.FEET)
TOUCH_FEET = {'lh': [-16.0, 3.0, -2.0], 'rh': [16.0, 3.0, -2.0]}
_land = {}


def _land_u(t):
	"""Beat phase of the braking stroke: one beat over the flare, then the wings rise to the top and stay
	there through the touch (an eagle strikes with its wings raised)."""
	if t < LAND_FLARE:
		return 0.0
	if t < LAND_BRAKE:
		return (t - LAND_FLARE) / (LAND_BRAKE - LAND_FLARE)
	return 1.0 + flight.U_TOP * ease((t - LAND_BRAKE) / 0.5)


def _land_air(t):
	"""The airborne landing at time t (also continued past the touch for the wings)."""
	gear = ease(t / 0.9)
	flare = ease((t - LAND_FLARE + 0.1) / 0.8)
	u = _land_u(t)
	# the braking stroke: a hover beat, starting from the glide
	beat = hover_q(u if u < 1.0 else u - 1.0, gain=ease((t - LAND_FLARE) / 0.25))
	approach = glide_q(0.0)
	approach.update(shoulder=12.0, elbow=-12.0, sweep=-4.0, twist=4.0, pleats=[2.0] * 3, sway=0.0,
					pitch=12.0 * gear, neck=NECK_FLY, head=HEAD_FLY)
	q = qblend(approach, beat, flare)
	q['pitch'] = 12.0 * gear + (LAND_PITCH - 12.0) * flare + (q['pitch'] - HOVER_PITCH) * flare * 0.5
	q['still'] = 12.0 * gear + (HOVER_PITCH - 12.0) * flare
	q['heave'] = beat['heave'] * flare
	q['surge'] = 0.0
	q['legs'] = tuple(a + (b - a) * gear for a, b in zip(LEGS_TUCKED, LEGS_FORWARD))
	q['leg_swing'] = 0.0
	# the tail drops and spreads as an air brake (TailMotion: droop), the push of the braking stroke
	q['droop'] = 2.5 * flare
	q['push'] = flight.downstroke(u) if u < 1.0 else 0.0
	q['jaw'] = -1.5
	return flight_pose(q)


def _reach(pose, t):
	"""The legs reach for the ground over the last 0.5 s before the touch: from where the gear holds the
	feet down to the touch marks, solved by IK so they strike exactly there."""
	k = ease((t - (LAND_TOUCH - 0.5)) / 0.5)
	if k <= 0:
		return pose
	m = walk.rig.matrices(pose)
	targets = {}
	for limb, side in (('lh', 'left'), ('rh', 'right')):
		hung = apply(m[f'lowerleg_{side}'], walk.ANKLE[side])
		targets[limb] = [h + (g - h) * k for h, g in zip(hung, TOUCH_FEET[limb])]
	solved = dict(pose)
	walk.solve_limbs(solved, targets, _land.setdefault('reach', {}))
	w = ease((t - (LAND_TOUCH - 0.5)) / 0.15)
	out = dict(pose)
	for side in ('left', 'right'):
		for b in (f'upperleg_{side}', f'lowerleg_{side}', f'foot_{side}'):
			out[b] = {'r': [x + (y - x) * w for x, y in zip(pose[b]['r'], solved[b]['r'])]}
	return out


def _land_kw(s):
	"""The standing solve's settings s seconds after the touch: the body carried on from the flare,
	riding forward over the planted feet, the legs taking the blow."""
	touch = _land_touch()
	pitch0 = touch['body']['r'][0] - stand.PITCH
	lift0 = touch['body']['p'][1] - stand.LIFT
	ride = ease(s / 0.6)            # the body rides over the feet
	absorb = math.sin(math.pi * min(1.0, s / 0.5)) * (1 - ease(s / 0.5) * 0.3)
	settle = ease((s - 0.3) / 1.0)
	feet = {k: [v + (stand.FEET[k][i] - v) * ride for i, v in enumerate(TOUCH_FEET[k])] for k in TOUCH_FEET}
	return dict(body_pitch=pitch0 * (1 - settle) - 6 * absorb, body_lift=lift0 * (1 - ease(s / 0.4)) - 7 * absorb,
				body_z=0.0, jaw=-1.5, feet=feet)


def _land_touch():
	if 'touch' not in _land:
		_land['touch'] = _reach(_land_air(LAND_TOUCH), LAND_TOUCH)
	return _land['touch']


def land(t, L):
	if t < LAND_TOUCH:
		pose = _reach(_land_air(t), t)
		# the wings sweep down so the claws plant together with the hind feet
		plant = ease((t - (LAND_TOUCH - LAND_PLANT)) / LAND_PLANT)
		if plant > 0:
			if 'plant' not in _land:
				_land['plant'] = _wings(standing('land').pose(**_land_kw(0.0)))
			pose.update(blend(_wings(pose), _land['plant'], plant))
		return pose
	touch = _land_touch()
	s = t - LAND_TOUCH
	# all four feet on the ground: the hind feet on their marks, the claws on the stance's
	pose = standing('land').pose(**_land_kw(s))
	# the neck and head come down from the flare into the swan neck
	k = ease(s / 0.9)
	for b in NECK + ['head_group']:
		pose[b] = {'r': [x + (y - x) * k for x, y in zip(touch[b]['r'], pose[b]['r'])]}
	pose['_tail'] = blend({'_tail': touch['_tail']}, {'_tail': _standing_tail(0.0)}, ease(s / 0.5))['_tail']
	return pose


ROAR_AT = 0.6     # the growl starts: DragonAnim.ROAR_SECONDS mirrors it (DragonVoice plays it then)
ROAR_CLOSE = 2.1  # the growl fades (vanilla's growl is loud ~1.6 s, longer at the 0.8 pitch the roar uses)


def roar(t, L):
	# 0-0.6 rear up with the lips barely parted. At 0.6 the growl starts (the client plays it when its
	# animation gets there) and the jaw snaps wide open with it; the head and neck
	# shake while it lasts. 2.1-2.5 the jaw closes as the growl fades, 2.3-3.0 settle into the stance.
	# The neck straightens up out of its S.
	k = ease(t / ROAR_AT) * (1 - ease((t - 2.3) / 0.7))
	snap = ease((t - ROAR_AT + 0.08) / 0.15)
	close = ease((t - ROAR_CLOSE) / 0.4)
	jaw = 4 * ease(t / ROAR_AT) * (1 - snap) + (40 + 2 * S(t * 31)) * snap * (1 - close)
	fade = 1 - ease((t - 1.6) / (ROAR_CLOSE - 1.6))
	shake = 2 * S(t * 40) * snap * fade
	sw = stand.SWAN
	return standing('roar').pose(
		body_pitch=10 * k, body_lift=4 * k, fan=stand.FAN - 12 * k,
		swan=[sw[0] + 6 * k, sw[1] + 6 * k, sw[2] + 32 * k, sw[3] + 30 * k + shake],
		head=stand.HEAD + 14 * k + 6 * snap * (1 - close) + shake, head_yaw=shake, jaw=-1.5 - jaw)


def attack(t, L):
	# The bite. 0-0.45 cock back: the neck coils back and up, the head lifts and the jaw starts to open.
	# 0.45-0.62 strike: the chest drops and the neck uncoils forward and down, the head dashing out with
	# the mouth wide open to a standing player's head height (~2.3 blocks up, ~7 ahead).
	# 0.62-0.7 snap shut. 0.7-1.3 recover into the stance. The feet stay planted throughout.
	coil = ease(t / 0.45) * (1 - ease((t - 0.45) / 0.12))
	strike = ease((t - 0.45) / 0.17) * (1 - ease((t - 0.75) / 0.55))
	if t < 0.45:
		jaw = 18 * ease(t / 0.45)
	elif t < 0.62:
		jaw = 18 + 24 * ease((t - 0.45) / 0.12)
	else:
		jaw = 42 * (1 - ease((t - 0.62) / 0.08))
	sw = stand.SWAN
	return standing('attack').pose(
		body_pitch=6 * coil - 24 * strike, body_lift=2 * coil - 8 * strike, body_z=5 * coil - 9 * strike,
		swan=[sw[0] + 10 * coil - 46 * strike, sw[1] + 12 * coil - 14 * strike,
			  sw[2] - 14 * coil + 32 * strike, sw[3] - 4 * coil + 34 * strike],
		head=stand.HEAD + 18 * coil + 4 * strike, jaw=-1.5 - jaw)


def tail_sweep(t, L):
	# The tail strike. 0-0.6 wind-up; 0.6-0.95 the strike, the body braces; 0.95-1.8 recover. The tail
	# itself is the game's: body/TailMotion.java lifts it and rattles the tip (the telegraph), and
	# body/Strike.java cocks it away from the prey and whips it onto it by IK. The head stays out of it.
	wind = ease(t / 0.6) * (1 - ease((t - 0.6) / 0.3))
	return standing('tail_sweep').pose(body_pitch=-3 * wind, body_lift=-2 * wind)


BREATH_WINDUP, BREATH_STREAM, BREATH_RECOVER = 2.0, 3.0, 0.8   # seconds; BreathAttack.java mirrors these
BREATH_LEAN, BREATH_NECK_DOWN = 10.0, 8.0   # degrees: the chest leans forward, the straight neck points down
BREATH_LUNGE = 0.65   # seconds before the fire the neck starts to lunge out (0.3 s): out before the model's blend lag


def breath(t, L):
	# The stream breath (BreathAttack.java). 0-2.0 inhale: the chest swells, the neck coils back and up
	# and the jaw starts to part (the telegraph); at its end the neck lunges out. 2.0-5.0 the stream: the
	# neck straight out forward and a little down, the head low at the chest's height, the jaw wide open,
	# trembling while the flames pour out (the game spawns them in the mouth). 5.0-5.8 the jaw closes and
	# the stance returns. The feet stay planted; the game turns the dragon only when the neck cannot reach.
	# The model plays this BLEND_TICKS late, so the fire (at 2.0 s of the game's clock) comes when the
	# model is at 1.7 s: the lunge is done by then (BREATH_LUNGE before the fire).
	end = BREATH_WINDUP + BREATH_STREAM
	pour = ease((t - BREATH_WINDUP + BREATH_LUNGE) / 0.3) * (1 - ease((t - end) / BREATH_RECOVER))
	inhale = ease(t / BREATH_WINDUP) * (1 - pour)
	jaw = 14 * ease(t / BREATH_WINDUP) * (1 - pour) + 38 * pour
	shake = 1.2 * S(t * 47) * pour
	sw = stand.SWAN
	# pouring, the chest leans forward and the neck runs out straight from it, a little down, the head
	# low at the chest's height and in line with the neck. No sway: the game turns the straight neck onto
	# the aim and follows the target with it (body/Strike), so any keyed side-to-side would throw it off
	pitch = 7 * inhale - BREATH_LEAN * pour
	coiled = [sw[0] + 8 * inhale, sw[1] + 10 * inhale, sw[2] - 10 * inhale, sw[3] - 4 * inhale]
	straight = [-(stand.PITCH - BREATH_LEAN) - BREATH_NECK_DOWN, 0.0, 0.0, 0.0]
	return standing('breath').pose(
		body_pitch=pitch, body_lift=3 * inhale - 4 * pour, body_z=4 * inhale - 6 * pour,
		swan=[c + (st - c) * pour for c, st in zip(coiled, straight)],
		head=(stand.HEAD + 16 * inhale) * (1 - pour) - 3 * pour + shake, head_yaw=shake,
		jaw=-1.5 - jaw, fan=stand.FAN - 1 - 4 * inhale)


# The breath pass (BreathPass.java mirrors these): the dragon glides over its prey, head down, pouring
# the stream onto the ground under its flight path. Shorter than the perched breath: it is flying on.
PASS_WINDUP, PASS_STREAM, PASS_RECOVER = 1.2, 2.4, 0.8   # seconds
PASS_LUNGE = 0.5        # the neck swings down this long before the fire (out before the model's blend lag)
PASS_PITCH = -6.0       # the body tips nose down a little, a shallow stoop
PASS_NECK, PASS_HEAD = [-40.0, -4.0, 0.0, 0.0], -4.0   # the neck runs straight out ~50 deg below level
PASS_WING = {'shoulder': 5.0, 'elbow': -8.0, 'sweep': -3.0, 'twist': -1.0}   # held a little lower, swept back


def glide_breath(t, L):
	# 0-1.2 inhale on the glide: the chest swells, the neck draws back and up, the jaw parts (the
	# telegraph); 0.5 s before the fire the neck swings down. 1.2-3.6 the stream: the straight neck points
	# down and ahead, the jaw wide open and trembling; the game turns the neck onto its aim on the ground
	# (body/Strike) and spawns the flames in the mouth. 3.6-4.4 the jaw closes and the glide returns: it
	# ends on the glide's first frame, which plays next.
	end = PASS_WINDUP + PASS_STREAM
	pour = ease((t - PASS_WINDUP + PASS_LUNGE) / 0.3) * (1 - ease((t - end) / PASS_RECOVER))
	inhale = ease(t / PASS_WINDUP) * (1 - pour) * (1 - ease((t - end) / PASS_RECOVER))
	jaw = 14 * inhale + 38 * pour
	shake = 1.2 * S(t * 47) * pour
	base = glide_q(0.0)
	q = dict(base)
	for key, v in PASS_WING.items():
		q[key] = base[key] + (v - base[key]) * pour
	q['pitch'] = base['pitch'] + 3.0 * inhale + (PASS_PITCH - base['pitch']) * pour
	# the pitch is meant (no head held against it): the neck's base curve is made for it
	q['still'] = q['pitch'] - base['pitch']
	q['lag'] = (base['lag'][0], base['lag'][1] + q['still'])
	coiled = [NECK_FLY[0] + 6, NECK_FLY[1] + 6, NECK_FLY[2] - 4, NECK_FLY[3] - 2]
	drawn = [n + (c - n) * inhale for n, c in zip(NECK_FLY, coiled)]
	q['neck'] = [d + (s - d) * pour for d, s in zip(drawn, PASS_NECK)]
	q['head'] = (HEAD_FLY + 8 * inhale) * (1 - pour) + (PASS_HEAD + shake) * pour
	q['head_yaw'] = base['head_yaw'] * (1 - pour) + shake * pour
	q['neck_yaw'] = tuple(v * (1 - pour) for v in base['neck_yaw'])
	q['jaw'] = -1.5 - jaw
	q['roll'] = base['roll'] * (1 - pour)
	q['sway'] = 0.0
	return flight_pose(q)


# ---------------------------------------------------------------- attacks in the air
# Where the dragon cannot come down by its prey (on a wall, a pillar, in the air: ai/AirTactics.java) it
# fights from the air. GLIDE_BITE: the fly-by bite, on the glide past (and a little over) the prey: the neck
# draws back and the jaw parts, then the head dashes out and down at it (aimed by body/Strike) and snaps
# shut at DragonAnim.BITE_SECONDS; it ends on the glide's first frame. HOVER_BITE: the same bite standing in
# the air, the hover's beat going on (one whole beat, so it ends where the hover starts again).
# HOVER_BREATH: the breath pass's timing (BreathPass.java) in the hover: inhale, the neck reaching forward
# and the stream poured where body/Strike points the straightened neck, then the jaw closes; three beats.
BITE_PASS_NECK = [-24.0, -6.0, 4.0, 6.0]       # the neck dashes out forward and down
HOVER_STREAM_NECK = [-22.0, -6.0, 0.0, 0.0]    # the neck reaching forward out of the raised chest
HOVER_BREATH_LENGTH = 3 * FLAP


def _bite_neck(pose, coil, strike):
	"""The bite's neck on top of a flying pose (cling_pose's): coiled back, then lunging out and down."""
	for k, (c, s) in enumerate(zip([10, 12, -14, -4], [-58, -24, 30, 44])):
		pose[NECK[k]]['r'][0] += c * coil + s * strike
	pose['head_group']['r'][0] += 18 * coil + 4 * strike


def glide_bite(t, L):
	coil, strike, jaw = _bite(t)
	base = glide_q(0.0)
	q = dict(base)
	# a shallow stoop into the strike, the wings held lower and swept back
	for key, v in PASS_WING.items():
		q[key] = base[key] + (v - base[key]) * strike
	q['pitch'] = base['pitch'] + 3.0 * coil + (PASS_PITCH - base['pitch']) * strike
	q['still'] = q['pitch'] - base['pitch']
	q['lag'] = (base['lag'][0], base['lag'][1] + q['still'])
	coiled = [NECK_FLY[0] + 8, NECK_FLY[1] + 8, NECK_FLY[2] - 6, NECK_FLY[3] - 2]
	drawn = [n + (c - n) * coil for n, c in zip(NECK_FLY, coiled)]
	q['neck'] = [d + (b - d) * strike for d, b in zip(drawn, BITE_PASS_NECK)]
	q['head'] = HEAD_FLY + 12 * coil + 16 * strike
	still = 1 - max(coil, strike)
	q['head_yaw'] = base['head_yaw'] * still
	q['neck_yaw'] = tuple(v * still for v in base['neck_yaw'])
	q['roll'] = base['roll'] * still
	q['jaw'] = -1.5 - jaw
	q['sway'] = 0.0
	return flight_pose(q)


def hover_bite(t, L):
	coil, strike, jaw = _bite(t)
	q = hover_q((t / FLAP) % 1.0)
	q['pitch'] -= 30 * strike - 4 * coil
	q['jaw'] = -1.5 - jaw
	pose = flight_pose(q)
	_bite_neck(pose, coil, strike)
	return pose


def hover_breath(t, L):
	end = PASS_WINDUP + PASS_STREAM
	pour = ease((t - PASS_WINDUP + PASS_LUNGE) / 0.3) * (1 - ease((t - end) / PASS_RECOVER))
	inhale = ease(t / PASS_WINDUP) * (1 - pour) * (1 - ease((t - end) / PASS_RECOVER))
	jaw = 14 * inhale + 38 * pour
	shake = 1.2 * S(t * 47) * pour
	q = hover_q((t / FLAP) % 1.0)
	# the chest swells back as it inhales, then leans into the stream
	q['pitch'] += 4.0 * inhale - 10.0 * pour
	coiled = [NECK_HOVER[0] + 6, NECK_HOVER[1] + 6, NECK_HOVER[2] - 4, NECK_HOVER[3] - 2]
	drawn = [n + (c - n) * inhale for n, c in zip(NECK_HOVER, coiled)]
	q['neck'] = [d + (r - d) * pour for d, r in zip(drawn, HOVER_STREAM_NECK)]
	q['head'] = (HEAD_HOVER + 8 * inhale) * (1 - pour) + (shake - 2.0) * pour
	q['head_yaw'] = shake
	q['jaw'] = -1.5 - jaw
	return flight_pose(q)


# ---------------------------------------------------------------- narrow footholds
# Where there is no room for all four limbs (nav/Foothold.java) the dragon stands on its hind feet alone.
# UPRIGHT: sat up on its haunches, the body steep, the knees crouched, the feet right under the hips (at
# the model's origin, the middle of the site); the tail lies behind as a prop (body/TailMotion lays it),
# and the wings are held out half spread and drooping (a mantling bird) to keep the balance: they teeter
# against each other, and now and then one stroke rights it. Only the body's pitch and height move, so
# the solved feet stay exactly planted. CLING: a perch too small even for that (a pillar's top): the feet
# grip it while the wings keep beating the hover's stroke and carry most of the weight.
UPRIGHT_PITCH, UPRIGHT_LIFT = 55.0, 16.0
UPRIGHT_FEET = {'lh': [-16.0, 3.0, 2.0], 'rh': [16.0, 3.0, 2.0]}
UPRIGHT_SWAN, UPRIGHT_HEAD = [-4.0, 4.0, -28.0, -34.0], -10.0
MANTLE = {'shoulder': 10.0, 'elbow': -25.0, 'sweep': 15.0, 'twist': 0.0, 'pleat': 12.0}
UPRIGHT_LENGTH = 6.0
BALANCE_AT, BALANCE_LENGTH = 3.4, 1.2   # the righting stroke: DragonVoice.UPRIGHT_WING mirrors it
CLING_HEIGHT = -12.0    # the body lowered from the hover (px) so the legs reach down onto the perch, bent
CLING_GAIN = 0.85       # the hover's stroke, a little shallower: the feet take some of the weight
CLING_FEET = {'lh': [-16.0, 3.0, 4.0], 'rh': [16.0, 3.0, 4.0]}


def spread(left, right):
	"""Both wings from flight parameters (shoulder, elbow, sweep, twist, pleat), each side its own."""
	p = {
		'left_wing': {'r': [left['twist'], left['sweep'], -left['shoulder']]}, 'left_wing_tip': {'r': [0, 0, -left['elbow']]},
		'right_wing': {'r': [right['twist'], -right['sweep'], right['shoulder']]}, 'right_wing_tip': {'r': [0, 0, right['elbow']]},
	}
	p.update(wing_fan([max(0.0, left['pleat'])] * 3, 'left'))
	p.update(wing_fan([max(0.0, right['pleat'])] * 3, 'right'))
	return p


def _bump(u):
	"""0 -> 1 -> 0 over u in [0, 1], flat at both ends."""
	return 0.0 if u <= 0 or u >= 1 else 0.5 - 0.5 * C(PI2 * u)


def upright_pose(key, pitch=0.0, lift=0.0, z=0.0, left=None, right=None, swan=None, head=None, **kw):
	"""Sat up on the hind feet, offsets from the upright stance; `left`/`right`: wing offsets from MANTLE."""
	def wing(off):
		return {k: v + (off or {}).get(k, 0.0) for k, v in MANTLE.items()}
	pose = standing(key).pose(body_pitch=UPRIGHT_PITCH - stand.PITCH + pitch, body_lift=UPRIGHT_LIFT - stand.LIFT + lift,
							  body_z=z, feet=UPRIGHT_FEET, wings=spread(wing(left), wing(right)),
							  swan=swan or UPRIGHT_SWAN, head=UPRIGHT_HEAD if head is None else head, **kw)
	return pose


def upright(t, L):
	# Breathing (twice per loop), the wings teetering against each other (once), and at BALANCE_AT one
	# stroke: both wings up and pushed down again, the body rocked back a little by it.
	w = PI2 * t / L
	u = (t - BALANCE_AT) / BALANCE_LENGTH
	stroke = S(PI2 * u) * _bump(u)          # up (+), then the push down (-)
	teeter = 7 * S(w)

	def wing(side):
		return {'shoulder': side * teeter + 45 * stroke, 'elbow': -12 * stroke, 'sweep': -8 * stroke,
				'twist': 6 * stroke, 'pleat': 10 * max(0.0, stroke)}
	sw = UPRIGHT_SWAN
	return upright_pose(
		'upright', pitch=1.5 * S(2 * w) - 2 * stroke, lift=0.8 * S(2 * w), left=wing(1), right=wing(-1),
		swan=[sw[0] + 2 * S(w + 0.3), sw[1] + 1.5 * S(w + 0.6), sw[2] - 2 * S(w + 0.9), sw[3] - 2 * S(w + 1.2)],
		neck_yaw=(3 * S(w), 4 * S(w + 0.4), 5 * S(w + 0.8), 6 * S(w + 1.2)), head_yaw=8 * S(w + 1.6), jaw=-1.5)


def _bite(t):
	"""The bite's rhythm (attack's): coil 0-0.45, strike to 0.62 (DragonAnim.BITE_SECONDS + the snap),
	recover to 1.3; and the jaw's opening (degrees)."""
	coil = ease(t / 0.45) * (1 - ease((t - 0.45) / 0.12))
	strike = ease((t - 0.45) / 0.17) * (1 - ease((t - 0.75) / 0.55))
	if t < 0.45:
		jaw = 18 * ease(t / 0.45)
	elif t < 0.62:
		jaw = 18 + 24 * ease((t - 0.45) / 0.12)
	else:
		jaw = 42 * (1 - ease((t - 0.62) / 0.08))
	return coil, strike, jaw


def upright_bite(t, L):
	# The bite sat up: the body tips forward over the planted feet as the neck uncoils, and the wings
	# sweep back and up against the lunge (a counterweight), spread wide.
	coil, strike, jaw = _bite(t)
	sw = UPRIGHT_SWAN
	wing = {'shoulder': -4 * coil + 26 * strike, 'elbow': 12 * strike, 'sweep': 4 * coil - 28 * strike,
			'twist': 8 * strike, 'pleat': -6 * strike}
	return upright_pose(
		'upright_bite', pitch=6 * coil - 38 * strike, lift=2 * coil - 8 * strike, z=3 * coil - 6 * strike,
		left=wing, right=wing,
		swan=[sw[0] + 10 * coil - 50 * strike, sw[1] + 12 * coil - 20 * strike, sw[2] - 14 * coil + 30 * strike,
			  sw[3] - 4 * coil + 40 * strike],
		head=UPRIGHT_HEAD + 18 * coil + 4 * strike, jaw=-1.5 - jaw)


_cling = {}


def cling_pose(key, u, coil=0.0, strike=0.0, jaw=0.0):
	"""The hover's beat at phase u with the feet planted on CLING_FEET; the bite's lunge on top."""
	q = hover_q(u % 1.0, CLING_GAIN)
	q['pitch'] -= 30 * strike - 4 * coil
	q['jaw'] = -1.5 - jaw
	pose = flight_pose(q)
	pose['body']['p'][1] += CLING_HEIGHT
	# the neck coils back, then lunges out and down (as the bite's), on top of the head held still
	for k, (c, s) in enumerate(zip([10, 12, -14, -4], [-58, -24, 30, 44])):
		pose[NECK[k]]['r'][0] += c * coil + s * strike
	pose['head_group']['r'][0] += 18 * coil + 4 * strike
	walk.solve_limbs(pose, CLING_FEET, _cling.setdefault(key, {}))
	pose['_tail']['stand'] = 0.0
	return pose


def cling(t, L):
	return cling_pose('cling', t / L)


def cling_bite(t, L):
	coil, strike, jaw = _bite(t)
	return cling_pose('cling_bite', t / FLAP, coil, strike, jaw)


# The death (the game: mc/DeathFlight): the dragon has flown over the altar and risen, and now wraps itself
# up in the air as the light bursts out of it (vanilla's 10 s, DragonAnim.DEATH_SECONDS). From the hover:
# one last stroke up (DEATH_RAISE), then the wings sweep down and forward round the body and close over the
# chest, the arms hanging down the flanks and the folded hands meeting edge to edge in front, the fans shut
# (each wing keeps to its own side of the body's middle all through: they never overlap); the body sits up, the
# neck curls the head down onto the chest and the legs draw up. The tail tucks forward between the legs
# (body/TailMotion's DEATH, procedural). Wrapped (from DEATH_WRAP), the cocoon breathes ever more weakly and
# shudders now and then, harder toward the end; it holds its last frame.
COCOON_PITCH = 50.0
COCOON_WING = {'shoulder': -40.0, 'elbow': -90.0, 'sweep': -10.0, 'twist': 20.0, 'pleat': 90.0}
COCOON_NECK, COCOON_HEAD = [-35.0, -40.0, -40.0, -30.0], -40.0
COCOON_LEGS = (70.0, -90.0, 30.0)
DEATH_LENGTH, DEATH_WRAP = 10.0, 2.4
DEATH_RAISE = (0.15, 1.0)       # the last stroke up: from, to (s)
DEATH_RAISE_SHOULDER = 32.0     # how high (deg): the tips must not cross over the back
DEATH_SHUDDERS = ((4.2, 0.5, 3.0), (6.4, 0.6, 4.0), (7.9, 0.6, 5.5), (8.9, 0.5, 7.0), (9.4, 0.45, 8.0))  # at, length, deg


def _wing_q(q, side):
	"""One side's wing parameters out of flight parameters (shoulder, elbow, sweep, twist, pleats)."""
	return {'shoulder': q['shoulder'], 'elbow': q['elbow'], 'sweep': q['sweep'], 'twist': q['twist'],
			'pleats': list(q.get('pleats', [q['pleat']] * 3))}


def death(t, L):
	hq = hover_q((t / FLAP) % 1.0)
	wrap = ease(t / DEATH_WRAP)
	u = (t - DEATH_RAISE[0]) / (DEATH_RAISE[1] - DEATH_RAISE[0])
	raise_ = _bump(u) * (1 - wrap)
	pose = blend(flight_pose(hq), _cocoon(), ease((t - 0.3) / (DEATH_WRAP - 0.3)))
	pose.pop('_tail', None)
	# wrapped: breathing that fades, and the shudders
	calm = ease((t - DEATH_WRAP) / 0.6)
	w = PI2 * t / 2.5
	breath = calm * 1.2 * S(w) * max(0.0, 1 - t / L)
	shudder = sum(a * S(t * 55 + 1.3 * k) * _bump((t - at) / n) for k, (at, n, a) in enumerate(DEATH_SHUDDERS))
	# the wrap only ever opens from its closed pose (breathing in, trembling): closing further, the hands would cross
	opening = calm * 2.0 * (1 - C(w)) / 2 * max(0.0, 1 - t / L) + abs(shudder)
	pose['body']['r'][0] += 2 * breath + 0.3 * shudder
	for k, seg in enumerate(NECK):
		pose[seg]['r'][0] -= 1.5 * breath
	wings = {}
	for side, coc in (('left', COCOON_WING), ('right', COCOON_WING)):
		a = _wing_q(hq, side)
		q = {key: a[key] + (coc[key] - a[key]) * wrap for key in ('shoulder', 'elbow', 'sweep', 'twist')}
		q['pleats'] = [p + (coc['pleat'] - p) * wrap for p in a['pleats']]
		# the last stroke: the wings thrown up high before they come down round the body
		q['shoulder'] += DEATH_RAISE_SHOULDER * raise_
		q['elbow'] += 25 * raise_
		q['sweep'] += 15 * raise_
		q['elbow'] += opening
		wings[side] = q
	for b in [b for b in pose if '_wing' in b and 'root_web' not in b]:
		del pose[b]
	l, r = wings['left'], wings['right']
	pose.update({
		'left_wing': {'r': [l['twist'], l['sweep'], -l['shoulder']]}, 'left_wing_tip': {'r': [0, 0, -l['elbow']]},
		'right_wing': {'r': [r['twist'], -r['sweep'], r['shoulder']]}, 'right_wing_tip': {'r': [0, 0, r['elbow']]},
	})
	pose.update(wing_fan([max(0.0, v) for v in l['pleats']], 'left'))
	pose.update(wing_fan([max(0.0, v) for v in r['pleats']], 'right'))
	return pose


def assert_wings_apart(frames, margin=1.0):
	"""Every frame keeps each forearm and hand (with its webs) on its own side of the body's middle (x = 0;
	the right wing is the left's mirror, so the left's corners suffice): the wrapped wings never overlap."""
	rig = walk.rig
	for i, pose in enumerate(frames):
		mats = rig.matrices(pose)
		x = max(p[0] for b in rig.order if b.startswith(('left_wing_tip', 'left_wing_web')) for p in rig.corners(mats, b))
		assert x <= -margin, f'death frame {i}: the left wing reaches {x:.2f} px past the middle (the wings overlap)'


def _cocoon():
	"""The wrapped pose, without the wings (death() blends them by their parameters)."""
	p = {'body': {'p': [0, FLY_HEIGHT, 0], 'r': [COCOON_PITCH, 0, 0]}}
	for k, seg in enumerate(NECK):
		p[seg] = {'r': [COCOON_NECK[k], 0, 0]}
	p['head_group'] = {'r': [COCOON_HEAD, 0, 0]}
	p['jaw_group'] = {'r': [-1.5, 0, 0]}
	thigh, shin, foot = COCOON_LEGS
	for side in ('left', 'right'):
		p[f'upperleg_{side}'] = {'r': [thigh, 0, 0]}
		p[f'lowerleg_{side}'] = {'r': [shin, 0, 0]}
		p[f'foot_{side}'] = {'r': [foot, 0, 0]}
	return p


# ---------------------------------------------------------------- the flying tail (for the game)
# The flight animations' tails are made here and run by the game (body/TailMotion.java via
# TailTrack): the tail is a rope hung from the body. Each point along it is where a stiff tail would
# have been a moment ago, later toward the tip (TAIL_DELAY at the tip), so every heave and pitch of the
# body runs down it as a wave. On top: it drops a little on each downstroke (birds lower the tail
# as the wings push), hangs (droop) when the body stands up or brakes, sways with the glide, and
# blends into the standing tail (curled, laid on the ground: TailMotion.standing) on the ground.
TAIL_ANIMS = ('fly', 'flap', 'glide', 'hover', 'takeoff', 'land', 'cling', 'cling_bite', 'glide_breath',
			  'glide_bite', 'hover_bite', 'hover_breath')
TAIL_DELAY = 0.32       # seconds for the body's motion to reach the tip
_TAIL_PIVOTS = [walk.rig.bones[s]['pivot'] for s in TAIL] + [[0, 60, 194]]
TAIL_S = [p[2] - _TAIL_PIVOTS[0][2] for p in _TAIL_PIVOTS]
TAIL_ROOT = _TAIL_PIVOTS[0][2] - walk.rig.bones['body']['pivot'][2]   # px behind the body's pivot
TAIL_PUSH = [2.5, 2.0, 1.5, 1.0, 0.6, 0.3, 0.0, 0.0, 0.0]               # deg per unit of downstroke
TAIL_SHARE = [0.2 + 0.035 * k for k in range(len(TAIL))]                 # of the droop, per segment
TAIL_CURL = [0.0, 0.0, 2.0, 2.0, 2.0, 2.25, 2.25, 2.25, 2.25]          # TailMotion.CURL, spread


def tail_track(frames, hints, length, loop, step):
	"""Per keyframe: 9 bends down (x), 9 bends right (y), rest, lift (TailMotion.Pose)."""
	n = len(frames)

	def hint(t, key):
		t = t % length if loop is True else max(0.0, min(length, t))
		x = t / step
		i = min(n - 2, int(x))
		k = x - i
		return hints[i][key] * (1 - k) + hints[i + 1][key] * k

	def body(t):
		t = t % length if loop is True else max(0.0, min(length, t))
		x = t / step
		i = min(n - 2, int(x))
		k = x - i
		y = frames[i]['body']['p'][1] * (1 - k) + frames[i + 1]['body']['p'][1] * k
		th = frames[i]['body']['r'][0] * (1 - k) + frames[i + 1]['body']['r'][0] * k
		return y, th

	out = []
	for i in range(n):
		t = i * step
		pts = []
		for s in TAIL_S:
			d = TAIL_DELAY * s / TAIL_S[-1]
			y, th = body(t - d)
			r = math.radians(th)
			pts.append(((TAIL_ROOT + s) * math.cos(r), y - (TAIL_ROOT + s) * math.sin(r)))
		th_now = body(t)[1]
		xs, prev = [], th_now
		for k in range(len(TAIL)):
			phi = math.degrees(math.atan2(pts[k][1] - pts[k + 1][1], pts[k + 1][0] - pts[k][0]))
			xs.append(phi - prev)
			prev = phi
		h = hints[i]
		push = [hint(t - TAIL_DELAY * TAIL_S[k] / TAIL_S[-1], 'push') for k in range(len(TAIL))]
		sway = [hint(t - TAIL_DELAY * TAIL_S[k] / TAIL_S[-1], 'sway') for k in range(len(TAIL))]
		stand_k = h['stand']
		x = [(xs[k] + TAIL_PUSH[k] * push[k] + 6.0 * h['droop'] * TAIL_SHARE[k]) * (1 - stand_k) + TAIL_CURL[k] * stand_k
			 for k in range(len(TAIL))]
		y = [-(1.2 + 0.2 * k) * sway[k] * (1 - stand_k) for k in range(len(TAIL))]
		out.append(x + y + [stand_k, h['lift']])
	return out


# name: (pose, length s, loop, keyframe step s)
ANIMATIONS = {
	'idle': (idle, 4.0, True, 0.05),
	'walk': (walk_pose, walk.LENGTH, True, 0.05),
	'fly': (fly, FLAP, True, 0.04),
	'flap': (flap, FLAP, False, 0.04),
	'glide': (glide, 3.0, True, 0.05),
	'hover': (hover, FLAP, True, 0.04),
	'takeoff': (takeoff, TAKEOFF_LENGTH, False, 0.04),
	'land': (land, LAND_LENGTH, False, 0.04),
	'roar': (roar, 3.0, False, 0.025),
	'attack': (attack, 1.3, False, 0.05),
	'tail_sweep': (tail_sweep, 1.8, False, 0.025),
	'breath': (breath, BREATH_WINDUP + BREATH_STREAM + BREATH_RECOVER, False, 0.025),
	'death': (death, DEATH_LENGTH, 'hold_on_last_frame', 0.05),
	'upright': (upright, UPRIGHT_LENGTH, True, 0.05),
	'upright_bite': (upright_bite, 1.3, False, 0.05),
	'cling': (cling, FLAP, True, 0.04),
	'cling_bite': (cling_bite, 1.3, False, 0.04),
	'glide_breath': (glide_breath, PASS_WINDUP + PASS_STREAM + PASS_RECOVER, False, 0.04),
	'glide_bite': (glide_bite, 1.3, False, 0.04),
	'hover_bite': (hover_bite, FLAP, False, 0.04),
	'hover_breath': (hover_breath, HOVER_BREATH_LENGTH, False, 0.04),
}

PREFIX = 'animation.ender_dragon.'

# The bones the renderer turns on top of the keyframes (head leads a turn, the tail trails it, the
# shoulders turn against the body's pitch, the wings' root webs keep pointing at the body). Every animation keys them on every frame, so a procedural
# turn is always added to this frame's pose and never accumulates on a bone an animation left alone.
# The tail is keyed straight (zero) everywhere: all of its motion is procedural (body/TailMotion.java,
# laid on the ground and kept out of blocks by body/Tail.java).
PROCEDURAL = NECK + ['head_group'] + TAIL + ['left_wing', 'right_wing', 'left_wing_root_web', 'right_wing_root_web']


def full_pose(pose):
	"""The pose on the segment bones, with every procedural bone keyed."""
	out = expand(pose)
	for bone in PROCEDURAL:
		out.setdefault(bone, {'r': [0.0, 0.0, 0.0]})
	return out


def r3(v):
	return [round(x, 3) + 0.0 for x in v]


def build():
	out = {'format_version': '1.8.0', 'animations': {}, 'geckolib_format_version': 2}
	track, tails = {}, {}
	for name, (fn, length, loop, step) in ANIMATIONS.items():
		bones = {}
		frames = track.setdefault(name, [])
		hints = []
		steps = round(length / step)
		for i in range(steps + 1):
			t = round(i * step, 4)
			# a looping animation reuses frame 0 at its end so the loop is seamless
			pose = full_pose(fn(0.0 if loop is True and i == steps else t, length))
			hints.append(pose.pop('_tail', None))
			frames.append(pose)
			for bone, v in pose.items():
				ch = bones.setdefault(bone, {})
				if 'r' in v:
					rx, ry, rz = v['r']
					ch.setdefault('rotation', {})[f'{t:g}'] = {'vector': r3([-rx, -ry, rz])}
				if 'p' in v:
					px, py, pz = v['p']
					ch.setdefault('position', {})[f'{t:g}'] = {'vector': r3([-px, py, pz])}
		if name in TAIL_ANIMS:
			tails[name] = tail_track(frames, hints, length, loop, step)
		if name == 'death':
			assert_wings_apart(frames)
		out['animations'][PREFIX + name] = {'loop': loop, 'animation_length': length, 'bones': bones}
		print(f'{name:7s} {length:4.2f}s {len(bones)} bones')
	os.makedirs(os.path.join(HERE, 'out'), exist_ok=True)
	with open(os.path.join(HERE, 'out', 'ender_dragon.animation.json'), 'w') as f:
		json.dump(out, f, separators=(',', ':'))
	# the hitbox anchors of every keyframe, for the game (see parts.py)
	parts.export(walk.rig, track, ANIMATIONS, tails)


if __name__ == '__main__':
	build()
