package crazylimits.dragonsworn.flight;

/**
 * Up and down while hovering, as a big flier holds itself in the air: it is not carried up smoothly, every
 * downstroke throws it up and through every upstroke (the wings recovering, pushing no air) it falls almost
 * freely, so it bobs once a beat, highest early in the upstroke, lowest as the next downstroke bites.
 * Climbing, the downstrokes throw it higher than it falls; coming down, they only catch its fall.
 *
 * <p>How hard the wings push is set once a stroke, as it starts ({@link #step}): the beat ahead is planned so
 * that by the next downstroke the dragon is where it wants to be, at most {@link #CLIMB_PER_BEAT} higher or
 * {@link #DROP_PER_BEAT} lower. The motion is linear in the push, so two runs of the beat ahead give it exactly.
 */
public final class HoverLift {
	/** Gravity while hovering (blocks/tick^2): the upstroke is close to a free fall. */
	public static final double GRAVITY = 0.022;
	/** Vertical air drag, per tick. */
	public static final double DRAG = 0.98;
	/** Lift of a full downstroke that holds the height over a beat. */
	static final double LIFT = GRAVITY / FlightModel.MEAN_STROKE;
	/** Most it climbs or sinks in one beat (blocks). */
	public static final double CLIMB_PER_BEAT = 4.0, DROP_PER_BEAT = 3.0;
	/** The push's range, in units of the one that holds the height. */
	static final double MIN_GAIN = 0.3, MAX_GAIN = 3.0;

	private double gain = 1.0;
	private boolean stroking, planned;

	/** A new hover, or its beat moved (a takeoff's jump): the stroke under way is planned again. */
	public void restart() {
		planned = false;
	}

	/** How hard the stroke under way pushes (1 holds the height). */
	public double gain() {
		return gain;
	}

	/**
	 * Vertical speed after this tick.
	 *
	 * @param vy    vertical speed now (blocks/tick)
	 * @param phase beat phase now (0..1)
	 * @param error how far the wanted height is above the dragon (blocks)
	 */
	public double step(double vy, double phase, double error) {
		double stroke = Wingbeat.downstroke(phase);
		if (!planned || stroke > 0 && !stroking) plan(vy, phase, error);
		stroking = stroke > 0;
		return next(vy, stroke, gain);
	}

	private void plan(double vy, double phase, double error) {
		planned = true;
		double want = Math.max(-DROP_PER_BEAT, Math.min(CLIMB_PER_BEAT, error));
		double still = ahead(vy, phase, 0.0), per = ahead(vy, phase, 1.0) - still;
		gain = Math.max(MIN_GAIN, Math.min(MAX_GAIN, (want - still) / per));
	}

	/** How far it moves from {@code phase} to the start of the next downstroke at push {@code g}. */
	static double ahead(double vy, double phase, double g) {
		double beat = FlightModel.BEAT_TICKS;
		double left = Wingbeat.DOWNSTROKE_START - phase;
		left -= Math.floor(left);
		int ticks = (int) Math.round((left == 0 ? 1.0 : left) * beat);
		double y = 0;
		for (int k = 0; k < ticks; k++) {
			vy = next(vy, Wingbeat.downstroke(phase + k / beat), g);
			y += vy;
		}
		return y;
	}

	private static double next(double vy, double stroke, double g) {
		return vy * DRAG + stroke * LIFT * g - GRAVITY;
	}
}
