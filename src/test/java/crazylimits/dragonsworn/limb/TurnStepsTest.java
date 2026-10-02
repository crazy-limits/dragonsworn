package crazylimits.dragonsworn.limb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TurnStepsTest {
	/** The animation's feet of a dragon at the origin turned to {@code yaw} degrees: hinds behind, wrists ahead. */
	private static double[][] feet(double yaw) {
		double[][] model = {{-1.5, 1.5}, {1.5, 1.5}, {-2.0, -3.0}, {2.0, -3.0}};
		double c = Math.cos(Math.toRadians(yaw)), s = Math.sin(Math.toRadians(yaw));
		double[][] out = new double[4][2];
		for (int i = 0; i < 4; i++) {
			out[i][0] = model[i][0] * c - model[i][1] * s;
			out[i][1] = model[i][0] * s + model[i][1] * c;
		}
		return out;
	}

	@Test
	void standingStillTheFeetFollowTheAnimation() {
		TurnSteps steps = new TurnSteps();
		for (int t = 0; t < 40; t++) steps.update(1.0, feet(0), true, null);
		for (int i = 0; i < 4; i++) {
			assertEquals(0.0, steps.offsetX(i), 1e-9);
			assertFalse(steps.stepping(i));
		}
	}

	@Test
	void turningOnTheSpotStepsRoundInDiagonalPairs() {
		TurnSteps steps = new TurnSteps();
		int[] plants = new int[4];
		double[][] last = new double[4][2];
		for (int t = 0; t < 120; t++) {
			double[][] places = feet(3.0 * t);     // the ground fight's turn speed
			steps.update(1.0, places, true, foot -> plants[foot]++);
			int up = 0;
			boolean pair0 = false, pair1 = false;
			for (int i = 0; i < 4; i++) {
				double x = places[i][0] + steps.offsetX(i), z = places[i][1] + steps.offsetZ(i);
				if (steps.stepping(i)) {
					up++;
					if (TurnSteps.PAIR[i] == 0) pair0 = true;
					else pair1 = true;
				} else if (t > 20 && !steps.stepping(i) && last[i] != null) {
					// a planted foot does not slide
					assertEquals(last[i][0], x, 1e-6, "foot " + i + " slid at " + t);
					assertEquals(last[i][1], z, 1e-6);
				}
				// never left far from where the animation would have it (further only while the first steps
				// get into their rhythm: both front feet fall behind at once and one pair must wait)
				assertTrue(Math.hypot(steps.offsetX(i), steps.offsetZ(i)) < (t > 30 ? 1.0 : 1.4), "foot " + i + " lags " + t);
				last[i] = steps.stepping(i) ? null : new double[]{x, z};
			}
			assertTrue(up <= 2);
			assertFalse(pair0 && pair1, "both diagonal pairs off the ground at " + t);
		}
		for (int i = 0; i < 4; i++) assertTrue(plants[i] >= 3, "foot " + i + " stepped " + plants[i] + " times");
	}

	@Test
	void walkingHandsTheFeetBackToTheAnimation() {
		TurnSteps steps = new TurnSteps();
		for (int t = 0; t < 10; t++) steps.update(1.0, feet(3.0 * t), true, null);
		for (int t = 0; t < 20; t++) steps.update(1.0, feet(30), false, null);
		for (int i = 0; i < 4; i++) assertEquals(0.0, Math.hypot(steps.offsetX(i), steps.offsetZ(i)), 1e-9);
	}
}
