package crazylimits.dragonfall.anim;

/**
 * When the dragon's sounds play, so each comes out with the pose that makes it: a swing on every
 * downstroke, a step as each foot plants, the roar as the jaw snaps open. Cues are timed on what the
 * model shows, i.e. the animation's own time plus the blend into it ({@link DragonAnim#BLEND_TICKS});
 * the client checks every tick, so a dragon off screen (whose model is not animated) is still heard.
 *
 * <p>The roar is a voice that lasts: {@link Roar} tracks one from the jaw opening to its end, fades it
 * out smoothly when the dragon attacks instead, and (in flight, where no animation roars) opens the jaw
 * along with it.
 */
public final class DragonVoice {
	public enum Cue {
		ROAR,
		/** One swing of the wings: played as a downstroke begins to push air. */
		WING,
		/** A hind foot planting (the heavy one) and a folded hand planting (the wing-walk's front foot). */
		STEP_HIND, STEP_FRONT
	}

	/** Seconds after an animation is chosen that the model reaches the roar. */
	public static final double ROAR_AT = DragonAnim.ROAR_SECONDS + DragonAnim.BLEND_TICKS / 20.0;
	/** Where in a beat the swing sounds: a little into the downstroke, so its peak meets the fastest sweep. */
	public static final double WING_PHASE = 0.3;
	/** {@link DragonAnim#UPRIGHT}: the righting stroke's push (its downstroke, past the middle of it). */
	static final double UPRIGHT_WING = DragonAnim.BALANCE_SECONDS + 0.6 * DragonAnim.BALANCE_LENGTH;
	/** {@link DragonAnim#TAKEOFF}: all four push off (the step), the first power stroke goes down (the swing). */
	static final double TAKEOFF_STEP = 0.5, TAKEOFF_WING = DragonAnim.TAKEOFF_TOP_SECONDS + 0.12;
	/**
	 * {@link DragonAnim#LAND}: the braking stroke (the swing), the hind feet striking the ground and the
	 * claws a moment after (they strike together; one tick apart, two sounds).
	 */
	static final double LAND_WING = 0.8, LAND_STEP = DragonAnim.LAND_TOUCH_SECONDS, LAND_HANDS = LAND_STEP + 0.1;
	/**
	 * {@link DragonAnim#WALK} (2.4 s, see {@code tools/walk.py}): each limb plants when its swing ends,
	 * a quarter cycle after its swing starts (left hind 0, left front 0.25, right hind 0.5, right front 0.75).
	 */
	private static final double WALK_CYCLE = 2.4;
	private static final double[] WALK_PLANTS = {0.25 * WALK_CYCLE, 0.5 * WALK_CYCLE, 0.75 * WALK_CYCLE, WALK_CYCLE};
	private static final Cue[] WALK_FEET = {Cue.STEP_HIND, Cue.STEP_FRONT, Cue.STEP_HIND, Cue.STEP_FRONT};

	/** The roar sound plays at about this pitch; {@code tools/sounds.py} cuts the clips for it. */
	public static final double ROAR_PITCH = 0.9;
	/** Ticks the jaw stays wide open (the clip is loud for as long), then closes as the roar fades. */
	public static final int ROAR_OPEN_TICKS = 30, ROAR_CLOSE_TICKS = 8;
	/** Ticks an interrupted roar takes to fade out (and the jaw to shut). */
	public static final int ROAR_FADE_TICKS = 8;
	/** How wide a flying roar opens the jaw, degrees (the roar animation's own, see {@code tools/anims.py}). */
	public static final double ROAR_JAW = 40.0;
	/** A flying dragon roars every 15-40 s (vanilla growled every 10-20 s, and again as its ambient sound). */
	public static final int AIR_ROAR_MIN_TICKS = 300, AIR_ROAR_SPREAD_TICKS = 500;
	/** Ticks the jaw takes to snap open. */
	private static final int ROAR_SNAP_TICKS = 3;

	private DragonVoice() {}

	/**
	 * The cue that falls between two consecutive samples of the same animation's clock (seconds since it
	 * was chosen): after {@code before}, up to and including {@code after}. A repeating animation whose
	 * clock wraps (a push, {@link DragonAnim#FLAP}) is handled. Null when none does.
	 */
	public static Cue due(DragonAnim anim, double before, double after) {
		double blend = DragonAnim.BLEND_TICKS / 20.0;
		return switch (anim) {
			case ROAR -> once(DragonAnim.ROAR_SECONDS + blend, before, after) ? Cue.ROAR : null;
			case FLY, HOVER, CLING, CLING_BITE, HOVER_BITE, HOVER_BREATH -> every(DragonAnim.FLAP_SECONDS, WING_PHASE * DragonAnim.FLAP_SECONDS + blend, before, after) ? Cue.WING : null;
			case FLAP -> every(DragonAnim.PUSH_SECONDS, WING_PHASE * DragonAnim.FLAP_SECONDS + blend, before, after) ? Cue.WING : null;
			case TAKEOFF -> once(TAKEOFF_WING + blend, before, after) ? Cue.WING
					: once(TAKEOFF_STEP + blend, before, after) ? Cue.STEP_HIND : null;
			case LAND -> once(LAND_WING + blend, before, after) ? Cue.WING
					: once(LAND_STEP + blend, before, after) ? Cue.STEP_HIND
					: once(LAND_HANDS + blend, before, after) ? Cue.STEP_FRONT : null;
			case UPRIGHT -> every(DragonAnim.UPRIGHT_SECONDS, UPRIGHT_WING + blend, before, after) ? Cue.WING : null;
			case WALK -> {
				for (int i = 0; i < WALK_PLANTS.length; i++) {
					if (every(WALK_CYCLE, WALK_PLANTS[i] % WALK_CYCLE + blend, before, after)) yield WALK_FEET[i];
				}
				yield null;
			}
			default -> null;
		};
	}

	/** An attack: a roar going on fades out, and the dragon does not start one. */
	public static boolean attacking(DragonAnim anim) {
		return anim != null && (anim.bites() || anim == DragonAnim.TAIL_SWEEP || anim == DragonAnim.BREATH || anim.breathesInFlight());
	}

	private static boolean once(double at, double before, double after) {
		return before < at && at <= after;
	}

	/** {@code at}, {@code at + period}, ... between the samples; the clock may have wrapped once. */
	private static boolean every(double period, double at, double before, double after) {
		if (after < before) after += period;
		return Math.floor((after - at) / period) > Math.floor((before - at) / period) && after >= at;
	}

	/**
	 * One dragon's roar on the client, on its own tick count: started as the jaw opens, it lasts as long
	 * as the clip, unless an attack cuts it short with a smooth fade. The sound's volume and (for a
	 * roar with no roar animation, in flight) the jaw's opening follow it.
	 */
	public static final class Roar {
		private long start = Long.MIN_VALUE, fadeFrom = Long.MAX_VALUE;
		private boolean jaw;
		private int sequence;

		/**
		 * Starts a roar at {@code tick}; {@code openJaw}: the model is not roaring by itself (in flight),
		 * so the jaw opens with the sound. Returns its sequence number: a playing sound whose number is
		 * no longer current has been replaced.
		 */
		public int start(long tick, boolean openJaw) {
			start = tick;
			fadeFrom = Long.MAX_VALUE;
			jaw = openJaw;
			return ++sequence;
		}

		public int sequence() {
			return sequence;
		}

		/** Starts fading the roar out (an attack): no-op when none is loud. */
		public void fade(long tick) {
			if (volume(tick) > 0.0 && fadeFrom == Long.MAX_VALUE) fadeFrom = tick;
		}

		/** Sound volume, 0..1: full from the start, ramping down once faded; 0 when over. */
		public double volume(double tick) {
			if (tick < start) return 0.0;
			double end = start + ROAR_OPEN_TICKS + ROAR_CLOSE_TICKS;
			if (tick >= end) return 0.0;
			if (tick <= fadeFrom) return 1.0;
			return Math.max(0.0, 1.0 - (tick - fadeFrom) / ROAR_FADE_TICKS);
		}

		/**
		 * Degrees the jaw opens on top of the animation: only for a roar that opens it, snapping open,
		 * trembling while it lasts, closing with the fade or the end of the clip.
		 */
		public double jaw(double tick) {
			if (!jaw || tick < start) return 0.0;
			double t = tick - start;
			double open = smooth(t / ROAR_SNAP_TICKS);
			double close = smooth((t - ROAR_OPEN_TICKS) / ROAR_CLOSE_TICKS);
			if (fadeFrom != Long.MAX_VALUE) close = Math.max(close, smooth((tick - fadeFrom) / ROAR_FADE_TICKS));
			return (ROAR_JAW + 2.0 * Math.sin(t * 31.0 / 20.0)) * open * (1.0 - close);
		}

		private static double smooth(double u) {
			u = Math.max(0.0, Math.min(1.0, u));
			return u * u * (3.0 - 2.0 * u);
		}
	}
}
