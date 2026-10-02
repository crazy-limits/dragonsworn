package crazylimits.dragonsworn.flight;

/**
 * The wingbeat's timing, shared by the animations ({@code tools/flight.py} keys them on it), the flight
 * model's thrust and lift, and the sounds: one beat of {@code FLY}, {@code HOVER} and {@code FLAP}, and where
 * in it the wings push air.
 */
public final class Wingbeat {
	private Wingbeat() {
	}

	/** One wingbeat, in seconds. */
	public static final double FLAP_SECONDS = 1.6;
	/**
	 * One {@code FLAP} push, in seconds: a whole beat out of the glide and back into it (see
	 * {@code tools/anims.py} flap).
	 */
	public static final double PUSH_SECONDS = FLAP_SECONDS;
	/**
	 * Where in a beat (0..1) the downstroke runs: the wings push air between these phases (55 % of the
	 * beat, as big birds do). Beat phase 0 is wings level and rising; they are at the top at the start
	 * of the downstroke and at the bottom at its end ({@code tools/flight.py}).
	 */
	public static final double DOWNSTROKE_START = 0.225, DOWNSTROKE_END = 0.775;

	/** Strength of a downstroke at beat phase {@code u} (0..1, wraps): 0 outside it, peaking at 1. */
	public static double downstroke(double u) {
		u -= Math.floor(u);
		if (u < DOWNSTROKE_START || u > DOWNSTROKE_END) return 0.0;
		return Math.sin(Math.PI * (u - DOWNSTROKE_START) / (DOWNSTROKE_END - DOWNSTROKE_START));
	}
}
