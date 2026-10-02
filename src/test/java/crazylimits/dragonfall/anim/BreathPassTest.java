package crazylimits.dragonfall.anim;

import org.junit.jupiter.api.Test;

import static crazylimits.dragonfall.anim.BreathPass.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreathPassTest {
	private static final double EPS = 1e-9;

	@Test
	void theStreamFollowsAShortInhaleAndTheHeatPlaysOverIt() {
		assertFalse(streaming(WINDUP_TICKS - 1));
		assertTrue(streaming(WINDUP_TICKS));
		assertFalse(streaming(WINDUP_TICKS + STREAM_TICKS));
		assertTrue(glowing(WINDUP_TICKS - 1));
		assertEquals(88, TOTAL_TICKS);
		assertEquals(1.0, heat(WINDUP_TICKS), EPS);
		assertEquals(1.0, heatBrightness(WINDUP_TICKS - 1), EPS);
		assertEquals(0.0, heatBrightness(TOTAL_TICKS), EPS);
		// the pose's frame the neck is aimed on is in the middle of the stream
		assertTrue(streaming((int) Math.round(AIM_SECONDS * 20)));
	}

	@Test
	void itStartsOnlyLinedUpOnPreyAhead() {
		// yaw 0 faces -z
		assertTrue(linedUp(0.0F, 0.0, -30.0));
		assertTrue(linedUp(0.0F, 8.0, -30.0));
		assertFalse(linedUp(0.0F, 20.0, -30.0), "too far off its line");
		assertFalse(linedUp(0.0F, 0.0, -60.0), "too far ahead");
		assertFalse(linedUp(0.0F, 0.0, -5.0), "already over it: no time to inhale");
		assertFalse(linedUp(0.0F, 0.0, 30.0), "behind");
	}

	@Test
	void theAimStaysInTheConeAheadAndBelow() {
		// straight ahead and down 45 degrees
		assertArrayEquals(new double[] {0.0, 45.0}, angles(0.0F, 0.0, -10.0, -10.0), 1e-6);
		// off to the right (+x), and too shallow: clamped to the cone
		double[] a = angles(0.0F, 30.0, -1.0, -10.0);
		assertEquals(YAW_ARC, a[0], 1e-6);
		assertEquals(PITCH_MIN, a[1], 1e-6);
		// right under the neck or behind it: the steepest it pours, straight on
		assertArrayEquals(new double[] {0.0, PITCH_MAX}, angles(0.0F, 0.0, -10.0, 4.0), 1e-6);
		// a direction from the angles points back at the point
		double[] d = direction(90.0F, angles(90.0F, 10.0, -10.0, 0.0));
		assertArrayEquals(new double[] {Math.sqrt(0.5), -Math.sqrt(0.5), 0.0}, d, 1e-6);
	}

	@Test
	void theAimSwingsAfterThePreyNoFasterThanItsTurn() {
		double[] at = {0.0, PITCH_REST};
		double[] next = chase(at, new double[] {30.0, 20.0}, STREAM_TURN);
		assertEquals(STREAM_TURN, next[0], EPS);
		assertEquals(PITCH_REST - STREAM_TURN, next[1], EPS);
		assertArrayEquals(new double[] {1.0, PITCH_REST}, chase(at, new double[] {1.0, PITCH_REST}, STREAM_TURN), EPS);
	}

	@Test
	void flyingOverThePreyTheStreamRakesThroughItAndOnAhead() {
		// a dragon flying north (yaw 0, -z) at the pass's speed and height, over prey 30 blocks ahead
		double z = 30.0, y = HEIGHT;
		double[] aim = {0.0, PITCH_REST};
		double closest = Double.MAX_VALUE;
		for (int tick = 0; tick < WINDUP_TICKS + STREAM_TICKS; tick++) {
			double[] base = neckBase(0.0F);
			double bx = base[0], by = y + base[1], bz = z + base[2];
			double[] want = angles(0.0F, -bx, 0.5 - by, -bz);
			aim = chase(aim, want, tick < WINDUP_TICKS ? WINDUP_TURN : STREAM_TURN);
			double[] d = direction(0.0F, aim);
			// where the stream meets the ground (y = 0): the prey is at the origin
			double t = by / -d[1];
			if (streaming(tick)) {
				assertTrue(t > 0 && t < RANGE, "the stream reaches the ground");
				closest = Math.min(closest, Math.hypot(bx + d[0] * t, bz + d[2] * t));
			}
			z -= MAX_SPEED * 0.9;
		}
		assertTrue(closest < 1.5, "the flames pass over the prey: " + closest);
	}
}
