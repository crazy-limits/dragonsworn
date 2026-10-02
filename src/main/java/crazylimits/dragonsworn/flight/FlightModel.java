package crazylimits.dragonsworn.flight;

import java.util.random.RandomGenerator;

/**
 * How a big dragon flies, decided on the server (the wings are what move it) and sent to clients so the
 * model beats exactly when the dragon is pushed.
 *
 * <ul>
 *   <li><b>{@link Mode#FLY}</b> (continuous beats): climbing, turning hard, charging, and whenever it is
 *       slow: below {@link #SLOW} the air no longer carries it ({@link #stall}), so it beats to stay up,
 *       each beat lifting it more the slower it goes.</li>
 *   <li><b>{@link Mode#GLIDE}</b>: descending (it speeds up, like an elytra going down), and level flight
 *       while fast enough. Wings still, the body banks through turns.</li>
 *   <li><b>{@link Mode#PUSH}</b>: in level flight, once the glide has bled off speed, one push
 *       (sometimes two), then back to gliding.</li>
 *   <li><b>{@link Mode#HOVER}</b>: standing in the air, beats holding the height.</li>
 * </ul>
 * Speed is dynamic: thrust comes only from downstrokes ({@link #thrust}), so a dragon that stops
 * beating slows, one that dives accelerates, and turning bleeds speed ({@link #turnDrag}). A stroke that has started always finishes: a beating
 * mode changes only at the end of a beat, a push only when its strokes are done.
 */
public final class FlightModel {
	public enum Mode { GLIDE, FLY, PUSH, HOVER }

	/** What the current phase insists on; {@link #NONE} lets the model choose. */
	public enum Force { NONE, FLY, GLIDE, HOVER }

	/** The animation plan the client plays. {@code sequence} changes whenever a beat pattern restarts. */
	public record Plan(Mode mode, int flaps, int sequence) {
		public int encode() {
			return mode.ordinal() | (flaps & 3) << 2 | (sequence & 0xFFFFF) << 4;
		}

		public static Plan decode(int bits) {
			Mode[] modes = Mode.values();
			return new Plan(modes[Math.min(modes.length - 1, bits & 3)], bits >> 2 & 3, bits >>> 4);
		}
	}

	/** Rising faster than this (blocks/tick) needs beats. */
	public static final double CLIMB = 0.06;
	/** Sinking faster than this (blocks/tick) is a glide down. */
	public static final double SINK = 0.05;
	/** Turning faster than this (degrees/tick) needs beats. */
	public static final double SHARP_TURN = 3.0;
	/** In level flight, a push when slower than this (blocks/tick); continuous beats when slower than SLOW. */
	public static final double PUSH_BELOW = 0.95, SLOW = 0.7;
	/** Shortest glide between pushes, ticks. */
	static final int MIN_GLIDE = 30;
	static final double DOUBLE_CHANCE = 0.25;
	public static final double BEAT_TICKS = Wingbeat.FLAP_SECONDS * 20.0;
	public static final double PUSH_TICKS = Wingbeat.PUSH_SECONDS * 20.0;
	/** Peak forward acceleration of a downstroke (blocks/tick^2). */
	static final double FLY_THRUST = 0.10, PUSH_THRUST = 0.075;
	/** Gliding: what the airflow gives back (it settles near GLIDE_ACCEL / drag), and diving. */
	public static final double GLIDE_ACCEL = 0.035, DIVE_GAIN = 0.3, CLIMB_COST = 0.2;
	/** A turn of TURN_FULL degrees/tick or more takes this much extra speed off every tick. */
	public static final double TURN_BRAKE = 0.04, TURN_FULL = 6.0;
	/** Fully stalled (hanging still), it sinks this fast between beats (blocks/tick^2). */
	public static final double STALL_SINK = 0.02;
	/** Mean of the downstroke strength over a beat (a half sine over 0.4 of it). */
	static final double MEAN_STROKE = (Wingbeat.DOWNSTROKE_END - Wingbeat.DOWNSTROKE_START) * 2.0 / Math.PI;
	/** Lift per unit of downstroke in fast flight (a small heave); slow, it rises to hold STALL_SINK. */
	static final double CRUISE_LIFT = 0.006, STALL_LIFT = STALL_SINK / MEAN_STROKE;

	private Plan plan = new Plan(Mode.GLIDE, 0, 0);
	private long start;
	private long nextPushAt;

	public Plan plan() {
		return plan;
	}

	/**
	 * @param tick     server tick
	 * @param vy       blocks/tick, up positive
	 * @param yawRate  degrees/tick
	 * @param speed    horizontal blocks/tick
	 */
	public Plan update(long tick, double vy, double yawRate, double speed, Force force, RandomGenerator random) {
		Mode want = choose(vy, yawRate, speed, force, tick);
		Mode now = plan.mode;
		if (want == now) return plan;
		if (!canChange(tick)) return plan;
		int flaps = 0;
		if (want == Mode.PUSH) {
			flaps = random.nextDouble() < DOUBLE_CHANCE ? 2 : 1;
			nextPushAt = tick + Math.round(flaps * PUSH_TICKS) + MIN_GLIDE;
		}
		start = tick;
		plan = new Plan(want, flaps, plan.sequence + 1);
		return plan;
	}

	private Mode choose(double vy, double yawRate, double speed, Force force, long tick) {
		switch (force) {
			case HOVER: return Mode.HOVER;
			case FLY: return Mode.FLY;
			case GLIDE: return Mode.GLIDE;
			default: break;
		}
		if (vy > CLIMB || Math.abs(yawRate) > SHARP_TURN) return Mode.FLY;
		if (vy < -SINK) return Mode.GLIDE;
		if (speed < SLOW) return Mode.FLY;
		if (speed < PUSH_BELOW && tick >= nextPushAt) return Mode.PUSH;
		return Mode.GLIDE;
	}

	/** Beats end on a beat boundary, pushes when their strokes are done; a glide can change any time. */
	private boolean canChange(long tick) {
		long elapsed = tick - start;
		return switch (plan.mode) {
			case GLIDE -> true;
			case FLY, HOVER -> elapsed % Math.round(BEAT_TICKS) == 0;
			case PUSH -> elapsed >= Math.round(plan.flaps * PUSH_TICKS);
		};
	}

	/** Beat phase (0..1) at {@code tick}, or -1 when the wings are still. */
	public double beatPhase(long tick) {
		long elapsed = tick - start;
		return switch (plan.mode) {
			case GLIDE -> -1.0;
			case FLY, HOVER -> (elapsed % BEAT_TICKS) / BEAT_TICKS;
			// each push is one whole beat out of the glide and back into it; no thrust once gliding
			case PUSH -> elapsed >= plan.flaps * PUSH_TICKS ? -1.0 : (elapsed % PUSH_TICKS) / BEAT_TICKS;
		};
	}

	/**
	 * Puts the beat that just started at phase {@code u} instead of its start (the takeoff's hover picks up
	 * the beat its power stroke is already in: {@code DragonAnim.TAKEOFF_PHASE}).
	 */
	public void startAtPhase(long tick, double u) {
		start = tick - Math.round(u * BEAT_TICKS);
	}

	/** Downstroke strength now, 0..1. */
	public double stroke(long tick) {
		double u = beatPhase(tick);
		return u < 0 ? 0.0 : Wingbeat.downstroke(u);
	}

	/** Forward acceleration from the wings this tick (blocks/tick^2), before gliding and diving. */
	public double thrust(long tick) {
		double s = stroke(tick);
		return switch (plan.mode) {
			case FLY -> FLY_THRUST * s;
			case PUSH -> PUSH_THRUST * s;
			default -> 0.0;
		};
	}

	/** Multiplier on horizontal speed this tick for a turn of {@code yawRate} degrees/tick. */
	public static double turnDrag(double yawRate) {
		return 1.0 - TURN_BRAKE * Math.min(1.0, Math.abs(yawRate) / TURN_FULL);
	}

	/** How stalled the dragon is at {@code speed}: 0 at SLOW or faster, 1 hanging still. */
	public static double stall(double speed) {
		return Math.max(0.0, Math.min(1.0, 1.0 - speed / SLOW));
	}

	/**
	 * Vertical acceleration this tick from the air (blocks/tick^2): the downstroke's lift minus the
	 * stall's sink. Beating continuously, slow flight holds its height on average, rising on every
	 * downstroke and sinking between them.
	 */
	public double lift(long tick, double speed) {
		double stall = stall(speed);
		return stroke(tick) * (CRUISE_LIFT + STALL_LIFT * stall) - STALL_SINK * stall;
	}

	/** Seconds since the current plan started (for the animation clock). */
	public double seconds(long tick) {
		return (tick - start) / 20.0;
	}
}
