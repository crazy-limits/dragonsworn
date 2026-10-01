package crazylimits.dragonfall.body;

import crazylimits.dragonfall.anim.DragonAnim;

/**
 * What every animation does with the tail, made procedural: the animations key it straight and this
 * gives each segment its bend over time, the way the keyframes used to (the pose functions in
 * {@code tools/anims.py}, {@code walk.py} and {@code stand.py}, ported).
 *
 * <ul>
 *   <li><b>Standing</b> (idle, roar, bite, tail strike, breath, the takeoff's crouch): the tail curls
 *       down toward its tip and lies on the ground: {@link Pose#rest} asks {@link Tail} to lower it from
 *       the root until it rests on what is under it. On top: the idle's slow sway, a wave to the tip;
 *       the roar's shake; the tail strike's lift and rattling tip (the telegraph).</li>
 *   <li><b>Walking</b>: it swings against the hips, the swing lagging down its length, and rises and
 *       falls with each hind footfall.</li>
 *   <li><b>Flying</b> (fly, flap, hover): each segment takes a growing share of the chest's throw on the
 *       downstroke, later than the one before: a wave down the tail, a counterweight. Hovering, it hangs.</li>
 *   <li><b>Gliding</b>: a slow lazy sway, a wave to its tip.</li>
 *   <li><b>Dying</b>: it slumps and curls to one side.</li>
 * </ul>
 * One-shots carry on as their animation chain does ({@code ReplacedEnderDragon}): a roar, bite, strike
 * or breath into the idle, the takeoff into the hover.
 *
 * <p>Bends in {@link TailChain}'s convention (degrees, +X lowers the far end, +Y swings it right).
 */
public final class TailMotion {
	/** The three original tail bones the keyframes were made on, as segment ranges [from, to). */
	private static final int[][] BONES = {{0, 2}, {2, 5}, {5, 9}};
	/** The standing tail's curl toward its tip, on the second and third original bones. */
	static final double[] CURL = {6.0, 9.0};
	/** {@code anims.py} BEAT['pitch']: the chest's throw over one wingbeat (phase, degrees). */
	private static final double[][] BEAT_PITCH = {{0.0, 0}, {0.3, -10}, {0.5, -12}, {0.7, 11}, {0.84, 22}, {0.95, 8}};
	private static final double FLAP = DragonAnim.FLAP_SECONDS, PUSH_END = 0.8, PUSH_SETTLE = 0.5;
	private static final double WALK_CYCLE = 2.4, WALK_BODY_PITCH = 6.0;
	private static final double TAKEOFF_GROUND = 0.6;

	/** A tail pose: per-segment bends, how much it lies on the ground and how far it is lifted from there. */
	public static final class Pose {
		public final double[] x = new double[TailChain.SEGMENTS], y = new double[TailChain.SEGMENTS];
		/** 0..1: how much the tail lies on the ground (lowered from the root until it rests there). */
		public double rest;
		/** Degrees the root lifts the laid tail (a raised tail is a telegraph). */
		public double lift;

		void clear() {
			java.util.Arrays.fill(x, 0.0);
			java.util.Arrays.fill(y, 0.0);
			rest = lift = 0.0;
		}

		/** this = this + (o - this) k */
		void toward(Pose o, double k) {
			for (int i = 0; i < x.length; i++) {
				x[i] += (o.x[i] - x[i]) * k;
				y[i] += (o.y[i] - y[i]) * k;
			}
			rest += (o.rest - rest) * k;
			lift += (o.lift - lift) * k;
		}
	}

	private TailMotion() {}

	/**
	 * The tail of {@code anim} at {@code seconds}, blending in from {@code from} at {@code fromSeconds}
	 * ({@code blend} 0: all {@code from}, 1: all {@code anim}), the way the model blends a new animation in.
	 */
	public static void sample(DragonAnim anim, double seconds, DragonAnim from, double fromSeconds, double blend, Pose out) {
		sample(anim, seconds, out);
		if (from == null || blend >= 1.0) return;
		Pose old = new Pose();
		sample(from, fromSeconds, old);
		old.toward(out, Math.max(0.0, blend));
		copy(old, out);
	}

	/** The tail of {@code anim} at {@code seconds}. */
	public static void sample(DragonAnim anim, double t, Pose out) {
		out.clear();
		double length = PoseTrack.length(anim);
		if (anim.loops()) {
			t -= Math.floor(t / length) * length;
		} else if (t > length) {
			// what the chain plays next
			switch (anim) {
				case ROAR, ATTACK, TAIL_SWEEP, BREATH -> {
					sample(DragonAnim.IDLE, t - length, out);
					return;
				}
				case TAKEOFF -> {
					sample(DragonAnim.HOVER, t - length, out);
					return;
				}
				case FLAP -> {
					sample(DragonAnim.GLIDE, t - length, out);
					return;
				}
				default -> t = length;
			}
		}
		t = Math.max(0.0, t);
		switch (anim) {
			case IDLE -> {
				double w = 2 * Math.PI * t / length;
				standing(out, 0.0);
				bone(out, 0, 0.0, 5 * Math.sin(w));
				bone(out, 1, 0.0, 7 * Math.sin(w - 0.6));
				bone(out, 2, 0.0, 10 * Math.sin(w - 1.2));
			}
			case WALK -> {
				double w = 2 * Math.PI * t / WALK_CYCLE;
				bone(out, 0, -WALK_BODY_PITCH * 0.5, -7 * Math.sin(w - 0.4));
				bone(out, 1, 2 * Math.cos(2 * w), -9 * Math.sin(w - 1.0));
				bone(out, 2, 3 * Math.cos(2 * w - 0.5), -12 * Math.sin(w - 1.6));
			}
			case FLY -> beat(out, t / FLAP, 1.0, 0.0);
			case FLAP -> {
				double beatTime = PUSH_END * FLAP;
				if (t <= beatTime) beat(out, t / FLAP, ease(t / FLAP / 0.12), 0.0);
				else beat(out, PUSH_END, 1 - ease((t - beatTime) / PUSH_SETTLE), 0.0);
			}
			case GLIDE -> {
				double w = 2 * Math.PI * t / length;
				for (int k = 0; k < out.y.length; k++) out.y[k] = -(1.2 + 0.2 * k) * Math.sin(w - 0.5 - 0.15 * k);
			}
			case HOVER -> hover(out, t / FLAP);
			case TAKEOFF -> takeoff(out, t, length);
			case ROAR -> {
				double snap = ease((t - 0.6 + 0.08) / 0.15), fade = 1 - ease((t - 1.6) / 0.5);
				double shake = 2 * Math.sin(t * 40) * snap * fade;
				standing(out, 0.0);
				bone(out, 2, 0.0, shake * 2);
			}
			case ATTACK, BREATH -> standing(out, 0.0);
			case TAIL_SWEEP -> {
				standing(out, 26 * ease(t / 0.6) * (1 - ease((t - 1.0) / 0.8)));
				// the tip rattles
				if (t > 0.35 && t < 0.65) for (int k = 6; k < out.y.length; k++) out.y[k] = 3.5 * Math.sin(t * 75 + k);
			}
			case DEATH -> {
				double slump = ease((t - 0.8) / 1.2);
				bone(out, 0, 4 * slump, -8 * slump);
				bone(out, 1, 4 * slump, -12 * slump);
				bone(out, 2, 2 * slump, -16 * slump);
			}
		}
	}

	private static void takeoff(Pose out, double t, double length) {
		if (t <= TAKEOFF_GROUND) {
			standing(out, 10 * ease((t - 0.35) / 0.25));
			return;
		}
		// airborne: from the laid tail at the jump into the hover's beat, then the hover's first frame
		double k = ease((t - TAKEOFF_GROUND) / (length - TAKEOFF_GROUND));
		standing(out, 10.0);
		Pose air = new Pose(), hover = new Pose();
		hover(air, (0.8 + 0.2 * k) % 1.0);
		hover(hover, 0.0);
		out.toward(air, ease(k * 1.6));
		out.toward(hover, ease((k - 0.6) / 0.4));
	}

	/** The hover: the tail hangs, a small droop with the beat's wave on top. */
	private static void hover(Pose out, double u) {
		beat(out, u, 0.6, 6.0);
	}

	/**
	 * The wingbeat's wave: segment k takes {@code 0.2 + 0.035 k} of the chest's throw at beat phase
	 * {@code u}, {@code 0.06 + 0.03 k} of a beat later, scaled by {@code wave}, plus {@code droop}.
	 */
	private static void beat(Pose out, double u, double wave, double droop) {
		for (int k = 0; k < out.x.length; k++) {
			double share = 0.2 + 0.035 * k;
			out.x[k] = share * (droop + wave * spline(BEAT_PITCH, u - 0.06 - 0.03 * k));
		}
	}

	/** On its feet: the tail curls toward the tip and lies on the ground, lifted {@code lift} degrees. */
	private static void standing(Pose out, double lift) {
		bone(out, 1, CURL[0], 0.0);
		bone(out, 2, CURL[1], 0.0);
		out.rest = 1.0;
		out.lift = lift;
	}

	/** A turn of original bone {@code b}, shared equally between its segments (as {@code chain.expand}). */
	private static void bone(Pose out, int b, double x, double y) {
		int from = BONES[b][0], to = BONES[b][1], n = to - from;
		for (int k = from; k < to; k++) {
			out.x[k] += x / n;
			out.y[k] += y / n;
		}
	}

	private static void copy(Pose from, Pose to) {
		System.arraycopy(from.x, 0, to.x, 0, from.x.length);
		System.arraycopy(from.y, 0, to.y, 0, from.y.length);
		to.rest = from.rest;
		to.lift = from.lift;
	}

	/** Periodic Catmull-Rom through (phase, value) points over [0, 1), as {@code anims.spline}. */
	static double spline(double[][] points, double u) {
		int n = points.length;
		u -= Math.floor(u);
		for (int i = 0; i < n; i++) {
			double u0 = points[i][0], v0 = points[i][1];
			double u1 = points[(i + 1) % n][0], v1 = points[(i + 1) % n][1];
			if (u1 <= u0) u1 += 1.0;
			double uu = u >= u0 ? u : u + 1.0;
			if (u0 <= uu && uu < u1) {
				double vp = points[(i - 1 + n) % n][1], vn = points[(i + 2) % n][1];
				double s = (uu - u0) / (u1 - u0);
				return 0.5 * ((2 * v0) + (-vp + v1) * s + (2 * vp - 5 * v0 + 4 * v1 - vn) * s * s
						+ (-vp + 3 * v0 - 3 * v1 + vn) * s * s * s);
			}
		}
		return points[0][1];
	}

	static double ease(double x) {
		x = Math.max(0.0, Math.min(1.0, x));
		return x * x * (3.0 - 2.0 * x);
	}
}
