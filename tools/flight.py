"""The wingbeat, built the way a big bird (eagle, vulture, swan) beats its wings, and the body's answer.

What it follows (measured on birds; see the sources in the docstrings below):

* **One phase drives everything.** The beat is one angle theta: 0 = wings at the top, pi = at the bottom.
  It runs through u (the beat, 0..1) smoothly warped (every derivative continuous, never stalling) so
  the downstroke takes DOWN of the beat (birds: 50-55 %). Every joint is a couple of harmonics of
  theta, so there is no key to snap through: speed is continuous over the whole loop and across its seam.
* **Joints break one after another, root to tip.** The shoulder turns first, the elbow (the hand's hinge)
  ~0.08 of a beat later, the fan's pleats later again and the fingers ripple from the leading one to
  the trailing one. The hand is dragged by the air: it trails up behind the arm on the downstroke (a cup
  scooping air) and hangs down while the arm rises (an arch), flexed most a little after mid-upstroke. The
  tip so traces an ellipse seen from the side, a figure eight when slow.
* **Pronation and supination.** The whole wing twists leading edge down on the downstroke (it bites the
  air) and up on the upstroke (it slices through), and sweeps forward as it pushes, back as it recovers.
  The fan spreads into one surface on the downstroke and pleats half shut on the upstroke (less area).
* **The body is pushed, not keyed.** The downstroke's strength is exactly the curve the game uses for
  thrust and lift (DragonAnim.downstroke). The body's heave, pitch and surge are the steady periodic
  response of a damped mass to that force, so it sinks through the upstroke, is lowest as the wings start
  down, is driven up through the downstroke and peaks early in the upstroke (birds do the same).
* **The head holds still** in space while the body bobs (geese and swans: almost perfectly; here ~70 %):
  the neck is solved every frame to carry the head where it should be.
* **Legs and tail trail.** They hang on the body by inertia, so they swing late against its heave; the
  tail's wave (the game's, body/TailMotion.java) is a rope following the body down its length.

Signs are editor values for the left wing (the right mirrors): shoulder + raises, elbow + raises the
hand, sweep + swings the wing back, twist + raises the leading edge, pleat closes the fan (fan.py).
"""
import cmath
import math

PI2 = 2 * math.pi
FLAP = 1.6          # seconds per beat (DragonAnim.FLAP_SECONDS)
DOWN = 0.55         # share of the beat the downstroke takes
U_TOP = 0.225       # where the wings are at the top: u = 0 is wings level, rising (half the upstroke before)
DOWN_START, DOWN_END = U_TOP, U_TOP + DOWN   # DragonAnim.DOWNSTROKE_START/END
N = 240             # samples per beat for the body's response
HARMONICS = 10


def downstroke(u):
	"""DragonAnim.downstroke: strength of the push at beat phase u, 0 outside the downstroke, 1 at its peak."""
	u %= 1.0
	if u < DOWN_START or u > DOWN_END:
		return 0.0
	return math.sin(math.pi * (u - DOWN_START) / DOWN)


# theta(s) = 2 pi s + a (1 - cos 2 pi s), s = u - U_TOP: theta(0) = 0, theta(DOWN) = pi, theta(1) = 2 pi;
# theta' = 2 pi (1 + a sin 2 pi s) > 0 for |a| < 1. Smooth everywhere, the seam included.
_WARP = (math.pi - PI2 * DOWN) / (1 - math.cos(PI2 * DOWN))


def theta(u):
	s = (u - U_TOP) % 1.0
	return PI2 * s + _WARP * (1 - math.cos(PI2 * s))


class Style:
	"""How hard and how wide a beat is: the wings' strokes and the body's answer (px, degrees)."""

	def __init__(self, **kw):
		# shoulder: mid + amp cos(theta); elbow (hand hinge): lagged, mostly against the arm's motion
		self.mid, self.amp = 8.0, 46.0
		self.elbow, self.elbow_amp, self.elbow_lag = -10.0, 24.0, 0.5
		# sweep: forward on the downstroke (an ellipse) plus a figure-eight harmonic
		self.sweep, self.sweep_amp, self.sweep8 = 4.0, 12.0, 3.0
		self.twist, self.twist_amp = 0.0, 9.0
		# pleat: spread on the downstroke, half shut on the upstroke; ripple: each finger gap this much of
		# a beat behind the one before it (leading to trailing)
		self.pleat_open, self.pleat_shut, self.ripple = 2.0, 40.0, 0.035
		# the body: heave (px), pitch (deg, chest up), surge (px, + = forward)
		self.heave, self.pitch, self.surge = 8.0, 8.0, 3.0
		self.__dict__.update(kw)


# Continuous flapping (climbing, slow flight, charges): deep powerful strokes.
FLY = Style()
# Hovering: the body stands up, the stroke plane is near horizontal; wider fore-aft sweep.
HOVER = Style(mid=10.0, amp=52.0, sweep=2.0, sweep_amp=18.0, sweep8=5.0, heave=7.0, pitch=6.0, surge=0.0,
			  twist_amp=12.0)
# One push out of a glide: a little shallower.
PUSH = Style(amp=40.0, elbow_amp=20.0, heave=6.0, pitch=6.0, surge=3.5)


def wings(style, u, gain=1.0):
	"""Wing parameters at beat phase u. `gain` scales the stroke (0 = the glide's neutral wing)."""
	th = theta(u)
	lag = style.elbow_lag
	# the hand: dragged by the air (trails up while the arm goes down), flexed most a little after
	# mid-upstroke, and snapping straight just before the top (the tip leaves last)
	hand = math.sin(th - lag) + 0.18 * math.sin(2 * (th - lag))

	def pleat(v):
		k = 0.5 - 0.5 * math.sin(theta(v) - 0.25) * (1 + 0.2 * math.cos(theta(v)))
		return style.pleat_open + gain * (style.pleat_shut - style.pleat_open) * max(0.0, k)
	return {
		'shoulder': style.mid + gain * style.amp * math.cos(th),
		'elbow': style.elbow + gain * (style.elbow_amp * hand - style.elbow),
		'sweep': style.sweep + gain * (-style.sweep_amp * math.sin(th) + style.sweep8 * math.sin(2 * th)),
		'twist': style.twist - gain * style.twist_amp * math.sin(th + 0.3),
		'pleat': pleat(u),
		'pleats': [pleat(u - style.ripple * g) for g in range(3)],
	}


def _dft(samples):
	n = len(samples)
	return [sum(samples[k] * cmath.exp(-1j * PI2 * h * k / n) for k in range(n)) / n for h in range(HARMONICS + 1)]


def _response(force, damping, delay=0.0):
	"""Steady periodic displacement of a unit mass driven by `force` (samples over one beat) against
	`damping` (in units of the beat's angular frequency): x'' + c x' = F - mean(F). Normalized so the
	swing is +-1. `delay` (beats) shifts it later."""
	coeffs = _dft(force)
	resp = [0j] + [coeffs[h] / (-(h * PI2) ** 2 + 1j * damping * PI2 * h * PI2) for h in range(1, HARMONICS + 1)]
	out = []
	for k in range(N):
		u = k / N - delay
		out.append(sum(2 * (resp[h] * cmath.exp(1j * PI2 * h * u)).real for h in range(1, HARMONICS + 1)))
	lo, hi = min(out), max(out)
	mid, half = (hi + lo) / 2, (hi - lo) / 2 or 1.0
	return [(v - mid) / half for v in out], resp


def _sampler(values):
	"""Periodic linear interpolation over N uniform samples (dense enough to be smooth)."""
	def at(u):
		x = (u % 1.0) * N
		i = int(x)
		k = x - i
		return values[i % N] * (1 - k) + values[(i + 1) % N] * k
	return at


# The push: the same downstroke the game moves the dragon with.
_FORCE = [downstroke(k / N) for k in range(N)]
# Heave: lightly damped, so it keeps rising after the push ends and tops out early in the upstroke.
HEAVE = _sampler(_response(_FORCE, damping=1.2)[0])
# Pitch: the push lands ahead of the hips and throws the chest up; the tail damps it more.
PITCH = _sampler(_response(_FORCE, damping=1.6, delay=0.05)[0])
# Surge: the thrust peaks late in the downstroke as the wing sweeps forward and down.
SURGE = _sampler(_response(_FORCE, damping=2.5, delay=0.04)[0])


def body(style, u, gain=1.0):
	"""The body's answer at beat phase u: heave (px up), pitch (deg chest up), surge (px forward)."""
	return gain * style.heave * HEAVE(u), gain * style.pitch * PITCH(u), gain * style.surge * SURGE(u)


def peak(curve):
	"""Phase of a sampled curve's maximum (for checks)."""
	return max(range(N), key=lambda k: curve(k / N)) / N
