package crazylimits.dragonsworn.body;

import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.math.Maths;

/**
 * What every animation does with the tail, made procedural: the animations key it straight and this
 * gives each segment its bend over time, the way the keyframes used to (the pose functions in
 * {@code tools/anims.py}, {@code walk.py} and {@code stand.py}, ported).
 *
 * <ul>
 *   <li><b>Standing</b> (idle, roar, bite, tail strike, breath, the takeoff's crouch, sat up): the tail curls
 *       down toward its tip and lies on the ground: {@link Pose#rest} asks {@link Tail} to lower it from
 *       the root until it rests on what is under it. On top: the idle's slow sway, a wave to the tip;
 *       the roar's shake; the tail strike's lift and rattling tip (the telegraph).</li>
 *   <li><b>Walking</b>: it swings against the hips, the swing lagging down its length, and rises and
 *       falls with each hind footfall.</li>
 *   <li><b>Flying</b> (fly, flap, glide, hover, takeoff, land, and clinging to a perch): made with the animation ({@link TailTrack}):
 *       a rope hung from the body, the body's every heave and pitch running down it as a wave; it drops on
 *       each downstroke, hangs when the body stands up or brakes, sways with the glide, and lies down
 *       where the animation stands (the takeoff's crouch, the landing after the touch).</li>
 *   <li><b>Dying</b>: wrapped in its wings, it tucks the tail forward between its legs ({@link #TUCK}).</li>
 * </ul>
 * One-shots carry on as their animation chain does ({@code ReplacedEnderDragon}, {@link DragonAnim#then}): a
 * roar, bite, strike, breath or landing into the idle, the takeoff into the hover, a push into the glide,
 * a bite sat up or clinging back into that.
 *
 * <p>Bends in {@link TailChain}'s convention (degrees, +X lowers the far end, +Y swings it right).
 */
public final class TailMotion {
	/** The three original tail bones the keyframes were made on, as segment ranges [from, to). */
	private static final int[][] BONES = {{0, 2}, {2, 5}, {5, 9}};
	/** The standing tail's curl toward its tip, on the second and third original bones. */
	static final double[] CURL = {6.0, 9.0};
	private static final double WALK_CYCLE = 2.4, WALK_BODY_PITCH = 6.0;
	/**
	 * The dying cocoon's tail, per segment (root to tip): bent hard down at the root, it runs forward under
	 * the belly between the drawn-up legs, the tip turning up toward the chest.
	 */
	static final double[] TUCK = {50.0, 50.0, 40.0, 25.0, 15.0, 5.0, 0.0, -5.0, -5.0};

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
			// what the chain plays next (the death holds its last frame)
			if (anim != DragonAnim.DEATH) {
				sample(anim.then(), t - length, out);
				return;
			}
			t = length;
		}
		t = Math.max(0.0, t);
		if (TailTrack.has(anim)) {
			TailTrack.sample(anim, t, out);
			return;
		}
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
			case FLY, FLAP, GLIDE, HOVER, TAKEOFF, LAND, CLING, CLING_BITE, GLIDE_BREATH, GLIDE_BITE, HOVER_BITE, HOVER_BREATH -> throw new IllegalStateException("No tail track for " + anim);
			case ROAR -> {
				double snap = Maths.smoothstep((t - 0.6 + 0.08) / 0.15), fade = 1 - Maths.smoothstep((t - 1.6) / 0.5);
				double shake = 2 * Math.sin(t * 40) * snap * fade;
				standing(out, 0.0);
				bone(out, 2, 0.0, shake * 2);
			}
			case ATTACK, BREATH, UPRIGHT_BITE -> standing(out, 0.0);
			case UPRIGHT -> {
				// laid behind as a prop, swaying against the wings' teeter
				double w = 2 * Math.PI * t / length;
				standing(out, 0.0);
				bone(out, 1, 0.0, -6 * Math.sin(w - 0.4));
				bone(out, 2, 0.0, -9 * Math.sin(w - 1.0));
			}
			case TAIL_SWEEP -> {
				standing(out, 26 * Maths.smoothstep(t / 0.6) * (1 - Maths.smoothstep((t - 1.0) / 0.8)));
				// the tip rattles
				if (t > 0.35 && t < 0.65) for (int k = 6; k < out.y.length; k++) out.y[k] = 3.5 * Math.sin(t * 75 + k);
			}
			case DEATH -> {
				// tucked as the wings close round it
				double tuck = Maths.smoothstep(t / DragonAnim.DEATH_WRAP_SECONDS);
				for (int k = 0; k < out.x.length; k++) out.x[k] = TUCK[k] * tuck;
			}
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
}
