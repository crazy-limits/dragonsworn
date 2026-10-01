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

import parts
import stand
import walk
from chain import NECK, TAIL, expand
from fan import fingers, wing_fan

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
		jaw=-1.5 - 1.5 * S(2 * w))


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


def spline(points, u):
	"""Periodic Catmull-Rom through [(u, value), ...] over u in [0, 1): smooth, passes every point."""
	n = len(points)
	u %= 1.0
	for i in range(n):
		u0, v0 = points[i]
		u1, v1 = points[(i + 1) % n]
		if u1 <= u0:
			u1 += 1.0
		uu = u if u >= u0 else u + 1.0
		if u0 <= uu < u1:
			vp = points[i - 1][1]
			vn = points[(i + 2) % n][1]
			s = (uu - u0) / (u1 - u0)
			return 0.5 * ((2 * v0) + (-vp + v1) * s + (2 * vp - 5 * v0 + 4 * v1 - vn) * s * s
						  + (-vp + 3 * v0 - 3 * v1 + vn) * s * s * s)
	return points[0][1]


# One wingbeat, u in [0, 1): u = 0 is wings level at the start of the upstroke.
# Upstroke (slow): the arms rise only ~30 degrees -- no higher, or the wings cross over the back -- while
# the hands hang down, so each wing is an upside-down U (an arch). The fan half-shuts (less area).
# Downstroke (fast, powerful): the arms sweep forward and down until the wings point down, the hands
# trailing up, so each wing is a U (a cup) scooping air; the push lifts the body and throws the chest up.
# Signs are for the left wing (right mirrors).
BEAT = {
	'shoulder': [(0.0, 2), (0.3, 29), (0.45, 22), (0.63, -22), (0.8, -62), (0.92, -30)],         # + = up
	'elbow':    [(0.0, -6), (0.2, -30), (0.38, -40), (0.52, -12), (0.66, 26), (0.8, 14), (0.92, 4)],  # + = tip up
	'sweep':    [(0.0, 4), (0.3, 14), (0.5, 4), (0.7, -14), (0.88, -8)],                         # + = back
	'pleat':    [(0.0, 18), (0.25, 48), (0.42, 42), (0.58, 6), (0.85, 3)],                       # fan fold
	'lift':     [(0.0, -3), (0.3, -14), (0.5, -15), (0.68, 8), (0.82, 32), (0.93, 15)],         # body px
	'pitch':    [(0.0, 0), (0.3, -10), (0.5, -12), (0.7, 11), (0.84, 22), (0.95, 8)],           # chest up
	'surge':    [(0.0, 0), (0.3, 3), (0.55, 2), (0.75, -4), (0.88, -5), (0.97, -1)],            # body px, - = forward
}
PUSH_END = 0.8      # beat phase where the downstroke bottoms out: wings pointing down
PUSH_SETTLE = 0.5   # seconds a single push takes to settle from wings-down into the glide
FLAP = 1.6   # seconds per wingbeat


def beat(u):
	"""Pose parameters of one wingbeat at phase u."""
	return {k: spline(v, u) for k, v in BEAT.items()}


def glide_params(t, L=3.0):
	w = PI2 * t / L
	return {'shoulder': 8 + 3 * S(w + 0.5), 'elbow': -4 - 3 * S(w), 'sweep': 0.0, 'pleat': 4 + 2 * S(w),
			'lift': 6 + 1.5 * S(w) - 6, 'pitch': 0.0, 'roll': 4 * S(w), 'yaw': 0.0, 'surge': 0.0}


def _lagged(q, key, k, default):
	"""Per-segment value `k` of a list parameter, or `default` when the pose has none."""
	v = q.get(key)
	return v[k] if v else default


def flight_pose(q, t):
	"""Pose from flight parameters. The hand fan pleats ripple from the leading finger to the trailing one.

	The body answers every beat: it heaves (lift), throws the chest (pitch) and surges forward on the
	downstroke. The neck counters the chest so the head holds its gaze and floats, each segment a little
	later than the one below it. (The tail's answering wave is procedural: body/TailMotion.java.)"""
	pitch, lift = q['pitch'], q['lift']
	p = {
		'body': {'p': [0, 6 + lift, q.get('surge', 0.0)], 'r': [pitch, q.get('yaw', 0), q.get('roll', 0)]},
		'left_wing': {'r': [0, q['sweep'], -q['shoulder']]}, 'left_wing_tip': {'r': [0, 0, -q['elbow']]},
		'right_wing': {'r': [0, -q['sweep'], q['shoulder']]}, 'right_wing_tip': {'r': [0, 0, q['elbow']]},
	}
	pl = q['pleat']
	ripple = q.get('ripple', 0.0)
	p.update(wing_fan([max(0.0, pl + ripple * (g - 1)) for g in range(3)]))
	# neck: a gentle upward curve (3 deg at the base) minus most of the chest throw, felt later up the neck
	neck_base = [2.0, 1.0, 0.0, 0.0]
	for k, seg in enumerate(NECK):
		lagged = _lagged(q, 'neck_pitch', k, pitch)
		p[seg] = {'r': [neck_base[k] - 0.2 * lagged, _lagged(q, 'neck_yaw', k, 0.0), 0]}
	head_pitch = _lagged(q, 'neck_pitch', 3, pitch)
	p['head_group'] = {'r': [-5 - 0.25 * head_pitch, q.get('head_yaw', 0.0), 0]}
	p['jaw_group'] = {'r': [-1 - 0.5 * max(0, _lagged(q, 'neck_pitch', 0, pitch)) / 6, 0, 0]}
	pl2 = _lagged(q, 'tail_pitch', 2, pitch)
	pl3 = _lagged(q, 'tail_pitch', 5, pitch)
	p.update({
		'upperleg_left': {'r': [-50 - 0.5 * pl2, 0, 0]}, 'lowerleg_left': {'r': [-20 + 0.4 * pl3, 0, 0]}, 'foot_left': {'r': [-50 - 0.6 * pl3, 0, 0]},
		'upperleg_right': {'r': [-50 - 0.5 * pl2, 0, 0]}, 'lowerleg_right': {'r': [-20 + 0.4 * pl3, 0, 0]}, 'foot_right': {'r': [-50 - 0.6 * pl3, 0, 0]},
	})
	return p


def beat_params(u):
	q = beat(u)
	q['ripple'] = 10 * S(PI2 * u - 1.2)
	q['roll'] = 0.0
	# the body's follow-through, felt later up the neck and down the tail (a wave along each chain)
	q['neck_pitch'] = [spline(BEAT['pitch'], u - 0.04 - 0.03 * k) for k in range(len(NECK))]
	q['tail_pitch'] = [spline(BEAT['pitch'], u - 0.06 - 0.03 * k) for k in range(len(TAIL))]
	return q


def fly(t, L):
	"""Continuous flapping: climbing, hovering, taking off."""
	return flight_pose(beat_params(t / L), t)


def flap(t, L):
	"""A single push: from the glide, up (arch) and down (cup) until the wings point down, then from
	that bottom position straight into the glide."""
	g = glide_params(0.0)
	beat_time = PUSH_END * FLAP
	if t <= beat_time:
		u = t / FLAP
		b = beat_params(u)
		env = ease(u / 0.12)   # ease out of the glide at the start of the upstroke
	else:
		b = beat_params(PUSH_END)
		env = 1 - ease((t - beat_time) / PUSH_SETTLE)
	q = {}
	for k in set(g) | set(b):
		gv, bv = g.get(k, 0.0), b.get(k, g.get(k, 0.0))
		if isinstance(bv, list):
			q[k] = [v * env for v in bv]
		else:
			q[k] = gv + (bv - gv) * env
	return flight_pose(q, t)


def glide(t, L):
	q = glide_params(t, L)
	w = PI2 * t / L
	q['neck_pitch'] = [0.0] * len(NECK)
	q['tail_pitch'] = [0.0] * len(TAIL)
	# a slow lazy sway: the neck steers a little against the roll
	q['neck_yaw'] = [1.5 * S(w), 1.5 * S(w), 0.0, 0.0]
	q['head_yaw'] = 4 * S(w + 0.5)
	return flight_pose(q, t)


# Hovering: the body stands up in the air (chest up like the perched stance), the tail hangs down and
# the head is held up and level; the wings beat in a near-horizontal stroke plane and every downstroke
# heaves the body up (the game moves the dragon up and down with each beat too).
HOVER_PITCH = 38.0


def hover_params(u):
	q = beat_params(u)
	beat_pitch = q['pitch']
	q['pitch'] = HOVER_PITCH + 0.5 * beat_pitch
	q['lift'] = 0.8 * q['lift']
	q['surge'] = 0.0
	# the neck curls forward out of the raised chest so the head stays level and looks ahead
	q['neck_pitch'] = [HOVER_PITCH * 1.05 + 0.5 * v for v in q['neck_pitch']]
	# the tail hangs (TailMotion.java); the legs follow its wave: a small droop plus the beat's wave
	q['tail_pitch'] = [6.0 + 0.6 * v for v in q['tail_pitch']]
	return q


def hover_pose(q, t):
	p = flight_pose(q, t)
	p['head_group']['r'][0] = -2 - 0.2 * (q['pitch'] - HOVER_PITCH)
	# legs dangle under the raised body instead of tucking back
	sway = 0.4 * (q['pitch'] - HOVER_PITCH)
	for side in ('left', 'right'):
		p[f'upperleg_{side}'] = {'r': [-28 + sway, 0, 0]}
		p[f'lowerleg_{side}'] = {'r': [18 - 0.5 * sway, 0, 0]}
		p[f'foot_{side}'] = {'r': [26 + sway, 0, 0]}
	return p


def hover(t, L):
	return hover_pose(hover_params(t / L), t)


def blend(a, b, k):
	"""Per-bone linear blend of two full poses (`full_pose`), k = 0 -> a, 1 -> b."""
	out = {}
	for bone in set(a) | set(b):
		va, vb = a.get(bone, {}), b.get(bone, {})
		out[bone] = {key: [x + (y - x) * k for x, y in zip(va.get(key, [0, 0, 0]), vb.get(key, [0, 0, 0]))]
					 for key in set(va) | set(vb)}
	return out


def _wings_of(q):
	"""Only the wing bones (arms and hand fans) of a flight pose."""
	return {b: v for b, v in flight_pose(q, 0).items() if '_wing' in b}


TAKEOFF_JUMP = 0.55   # seconds into the takeoff when the legs and the downstroke push together
_takeoff_air = {}


def takeoff(t, L):
	# 0-0.35 crouch: the body sinks onto the hind legs, the wings rise to the top of the upstroke.
	# 0.35-0.6 the push: the legs straighten while the wings sweep down -- both at once; the game
	# throws the dragon up at TAKEOFF_JUMP. 0.6-1.4 airborne: legs fold to dangle, the body stands up
	# and the beat carries on into the hover (the next animation), wings only.
	ground_end = 0.6
	if t <= ground_end:
		crouch = ease(t / 0.35) * (1 - ease((t - 0.35) / 0.25))
		push = ease((t - 0.35) / 0.25)
		u = 0.32 * ease(t / 0.35) + 0.48 * push          # up to the top, then the downstroke to its end
		q = beat_params(u)
		sw = stand.SWAN
		return standing('takeoff').pose(
			body_pitch=-6 * crouch + 8 * push, body_lift=-9 * crouch + 9 * push,
			swan=[sw[0] - 6 * crouch, sw[1], sw[2] + 8 * push, sw[3] + 6 * push],
			head=stand.HEAD + 6 * push, jaw=-1.5 - 8 * push, fan=10 + (stand.FAN - 10) * (1 - push),
			wings=_wings_of(q))
	if 'lift' not in _takeoff_air:
		_takeoff_air['lift'] = full_pose(takeoff(ground_end, L))
		_takeoff_air['hover'] = full_pose(hover(0.0, FLAP))
	k = ease((t - ground_end) / (L - ground_end))
	# the beat goes on from the bottom of the downstroke (0.8) into the hover's next beat (1.0 = 0)
	beat_u = 0.8 + 0.2 * k
	air = full_pose(hover_pose(hover_params(beat_u % 1.0), t))
	return blend(blend(_takeoff_air['lift'], air, ease(k * 1.6)), _takeoff_air['hover'], ease((k - 0.6) / 0.4))


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


BREATH_WINDUP, BREATH_STREAM, BREATH_RECOVER = 1.0, 3.0, 0.8   # seconds; BreathAttack.java mirrors these


def breath(t, L):
	# The stream breath (BreathAttack.java). 0-1.0 inhale: the chest swells, the neck coils back and up
	# and the jaw starts to part (the telegraph). 1.0-4.0 the stream: the neck reaches forward and down,
	# the head aims at the ground ahead with the jaw wide open and trembles while the flames pour out
	# (the game spawns them in the mouth). 4.0-4.8 the jaw closes and the stance returns. The feet stay
	# planted; the game turns the whole dragon to sweep the stream.
	end = BREATH_WINDUP + BREATH_STREAM
	inhale = ease(t / BREATH_WINDUP) * (1 - ease((t - BREATH_WINDUP) / 0.35))
	pour = ease((t - BREATH_WINDUP + 0.1) / 0.3) * (1 - ease((t - end) / BREATH_RECOVER))
	jaw = 14 * ease(t / BREATH_WINDUP) * (1 - pour) + 38 * pour
	shake = 1.2 * S(t * 47) * pour
	sway = 3 * S(math.pi * (t - BREATH_WINDUP)) * pour
	sw = stand.SWAN
	return standing('breath').pose(
		body_pitch=7 * inhale - 6 * pour, body_lift=3 * inhale - 2 * pour, body_z=4 * inhale - 6 * pour,
		swan=[sw[0] + 8 * inhale - 22 * pour, sw[1] + 10 * inhale - 6 * pour,
			  sw[2] - 10 * inhale + 22 * pour, sw[3] - 4 * inhale + 18 * pour],
		neck_yaw=(0, 0, sway, sway), head=stand.HEAD + 16 * inhale - 4 * pour + shake, head_yaw=shake,
		jaw=-1.5 - jaw, fan=stand.FAN - 1 - 4 * inhale)


def death(t, L):
	fall, slump, wings = ease(t / 1.2), ease((t - 0.8) / 1.2), ease((t - 0.3) / 1.4)
	p = {
		'body': {'p': [0, -26 * fall, 0], 'r': [-3 * fall, 0, 0]},
		'upperleg_left': {'r': [-70 * fall, 0, -10 * fall]}, 'lowerleg_left': {'r': [40 * fall, 0, 0]}, 'foot_left': {'r': [30 * fall, 0, 0]},
		'upperleg_right': {'r': [-70 * fall, 0, 10 * fall]}, 'lowerleg_right': {'r': [40 * fall, 0, 0]}, 'foot_right': {'r': [30 * fall, 0, 0]},
		'neck_rot1': {'r': [-14 * slump, 12 * slump, 0]},
		'neck_rot2': {'r': [-8 * slump, 10 * slump, 0]},
		'head_group': {'r': [6 * slump, 8 * slump, -15 * slump]},
		'jaw_group': {'r': [-14 * slump, 0, 0]},
		'left_wing': {'r': [0, 0, FOLD['lw'] * (1 - wings) + 5 * wings]}, 'left_wing_tip': {'r': [0, 0, FOLD['lwt'] * (1 - wings) + 3 * wings]},
		'right_wing': {'r': [0, 0, FOLD['rw'] * (1 - wings) - 5 * wings]}, 'right_wing_tip': {'r': [0, 0, FOLD['rwt'] * (1 - wings) - 4 * wings]},
	}
	p.update(fingers(walk.FAN * (1 - wings) + 6 * wings))
	return p


# name: (pose, length s, loop, keyframe step s)
ANIMATIONS = {
	'idle': (idle, 4.0, True, 0.05),
	'walk': (walk_pose, walk.LENGTH, True, 0.05),
	'fly': (fly, FLAP, True, 0.04),
	'flap': (flap, PUSH_END * FLAP + PUSH_SETTLE, False, 0.04),
	'glide': (glide, 3.0, True, 0.05),
	'hover': (hover, FLAP, True, 0.04),
	'takeoff': (takeoff, 1.4, False, 0.04),
	'roar': (roar, 3.0, False, 0.025),
	'attack': (attack, 1.3, False, 0.05),
	'tail_sweep': (tail_sweep, 1.8, False, 0.025),
	'breath': (breath, BREATH_WINDUP + BREATH_STREAM + BREATH_RECOVER, False, 0.025),
	'death': (death, 2.5, 'hold_on_last_frame', 0.05),
}

PREFIX = 'animation.ender_dragon.'

# The bones the renderer turns on top of the keyframes (head leads a turn, the tail trails it, the
# shoulders turn against the body's pitch). Every animation keys them on every frame, so a procedural
# turn is always added to this frame's pose and never accumulates on a bone an animation left alone.
# The tail is keyed straight (zero) everywhere: all of its motion is procedural (body/TailMotion.java,
# laid on the ground and kept out of blocks by body/Tail.java).
PROCEDURAL = NECK + ['head_group'] + TAIL + ['left_wing', 'right_wing']


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
	track = {}
	for name, (fn, length, loop, step) in ANIMATIONS.items():
		bones = {}
		frames = track.setdefault(name, [])
		steps = round(length / step)
		for i in range(steps + 1):
			t = round(i * step, 4)
			# a looping animation reuses frame 0 at its end so the loop is seamless
			pose = full_pose(fn(0.0 if loop is True and i == steps else t, length))
			frames.append(pose)
			for bone, v in pose.items():
				ch = bones.setdefault(bone, {})
				if 'r' in v:
					rx, ry, rz = v['r']
					ch.setdefault('rotation', {})[f'{t:g}'] = {'vector': r3([-rx, -ry, rz])}
				if 'p' in v:
					px, py, pz = v['p']
					ch.setdefault('position', {})[f'{t:g}'] = {'vector': r3([-px, py, pz])}
		out['animations'][PREFIX + name] = {'loop': loop, 'animation_length': length, 'bones': bones}
		print(f'{name:7s} {length:4.2f}s {len(bones)} bones')
	os.makedirs(os.path.join(HERE, 'out'), exist_ok=True)
	with open(os.path.join(HERE, 'out', 'ender_dragon.animation.json'), 'w') as f:
		json.dump(out, f, separators=(',', ':'))
	# the hitbox anchors of every keyframe, for the game (see parts.py)
	parts.export(walk.rig, track, ANIMATIONS)


if __name__ == '__main__':
	build()
