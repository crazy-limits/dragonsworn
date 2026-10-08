package crazylimits.dragonsworn.flight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HoverLiftTest {
	private static final int BEAT = (int) Math.round(FlightModel.BEAT_TICKS);

	/** Heights over {@code ticks}, starting at phase {@code phase} with speed {@code vy}, wanting {@code aim}. */
	private static double[] fly(double vy, double phase, double aim, int ticks) {
		HoverLift lift = new HoverLift();
		double y = 0;
		double[] out = new double[ticks];
		for (int k = 0; k < ticks; k++) {
			vy = lift.step(vy, phase + (double) k / BEAT, aim - y);
			y += vy;
			out[k] = y;
		}
		return out;
	}

	@Test
	void holdingItsHeightItBobsOnEveryBeat() {
		double[] y = fly(0.0, Wingbeat.DOWNSTROKE_START, 0.0, 10 * BEAT);
		double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
		for (int k = 6 * BEAT; k < 10 * BEAT; k++) {
			lo = Math.min(lo, y[k]);
			hi = Math.max(hi, y[k]);
		}
		assertTrue(hi - lo > 1.0 && hi - lo < 3.0, "a clear bob, not a jitter nor a bounce: " + (hi - lo));
		assertTrue(Math.abs((hi + lo) / 2) < 1.5, "round the wanted height: " + (hi + lo) / 2);
	}

	@Test
	void theUpstrokeIsAlmostAFall() {
		HoverLift lift = new HoverLift();
		// in the upstroke no air is pushed: only gravity and drag
		double vy = lift.step(0.0, 0.9, 0.0);
		assertEquals(-HoverLift.GRAVITY, vy, 1e-9);
	}

	@Test
	void theTakeoffLeapsFallsBackALittleAndClimbsOnItsBeats() {
		double[] y = fly(0.5, 0.35, 14.0, 6 * BEAT);
		double peak = 0;
		int peakAt = 0;
		for (int k = 0; k < BEAT; k++) {
			if (y[k] > peak) {
				peak = y[k];
				peakAt = k;
			}
		}
		assertTrue(peak > 4.0, "the leap and the first stroke throw it well up: " + peak);
		double sag = peak;
		for (int k = peakAt; k < peakAt + BEAT / 2; k++) sag = Math.min(sag, y[k]);
		assertTrue(peak - sag > 0.2, "through the upstroke it sinks back: " + (peak - sag));
		assertTrue(y[6 * BEAT - 1] > 11.0, "within a few beats it is up: " + y[6 * BEAT - 1]);
	}

	@Test
	void comingDownTheStrokesOnlyCatchItsFall() {
		double[] y = fly(0.0, Wingbeat.DOWNSTROKE_START, -14.0, 8 * BEAT);
		assertTrue(y[4 * BEAT - 1] < -6.0, "it sinks beat by beat: " + y[4 * BEAT - 1]);
		assertTrue(y[8 * BEAT - 1] > -17.0, "and stops by the aim: " + y[8 * BEAT - 1]);
	}
}
