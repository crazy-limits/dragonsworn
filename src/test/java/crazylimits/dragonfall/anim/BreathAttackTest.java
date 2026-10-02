package crazylimits.dragonfall.anim;

import org.junit.jupiter.api.Test;

import static crazylimits.dragonfall.anim.BreathAttack.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreathAttackTest {
	private static final double EPS = 1e-9;

	@Test
	void streamFollowsTheInhaleAndStopsBeforeTheRecovery() {
		assertFalse(streaming(0));
		assertFalse(streaming(WINDUP_TICKS - 1));
		assertTrue(streaming(WINDUP_TICKS));
		assertTrue(streaming(WINDUP_TICKS + STREAM_TICKS - 1));
		assertFalse(streaming(WINDUP_TICKS + STREAM_TICKS));
		assertFalse(glowing(0));
		assertTrue(glowing(WINDUP_TICKS - 1));
		assertEquals(116, TOTAL_TICKS);
	}

	@Test
	void heatClimbsOverTheInhaleAndCoolsAfterTheStream() {
		assertEquals(0.0, heat(0), EPS);
		assertEquals(0.5, heat(WINDUP_TICKS / 2.0), EPS);
		assertEquals(1.0, heat(WINDUP_TICKS), EPS);
		assertEquals(1.0, heat(TOTAL_TICKS), EPS);
		assertEquals(1.0, heatBrightness(WINDUP_TICKS - 1), EPS);
		for (int t = WINDUP_TICKS; t < WINDUP_TICKS + STREAM_TICKS; t++) {
			assertTrue(heatBrightness(t) > 0.75 && heatBrightness(t) <= 1.0);
		}
		assertEquals(0.0, heatBrightness(TOTAL_TICKS), EPS);
	}

	@Test
	void facingMatchesTheVanillaHead() {
		// Vanilla puts the head part at (sin yaw, -cos yaw) * 6.5: yaw 0 faces north (-z), 90 faces east (+x).
		assertArrayEquals(new double[] {0, 0, -1}, facing(0), EPS);
		assertArrayEquals(new double[] {1, 0, 0}, facing(90), EPS);
		assertEquals(0.0F, yawToward(0, -1), 1e-4);
		assertEquals(90.0F, yawToward(1, 0), 1e-4);
	}

	@Test
	void streamPointsDownAndSweepsOutward() {
		double[] dir = direction(0, WINDUP_TICKS);
		assertEquals(1.0, Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]), EPS);
		assertTrue(dir[1] < 0 && dir[2] < 0);
		// From the mouth, the axis meets the ground (y = 0) ~13.5 blocks ahead at first, ~19 at the end,
		// and within reach the whole time.
		assertEquals(13.5, groundAhead(WINDUP_TICKS), 0.2);
		assertEquals(18.9, groundAhead(WINDUP_TICKS + STREAM_TICKS - 1), 0.2);
		assertTrue(MOUTH_UP / Math.sin(Math.toRadians(PITCH_END)) < RANGE);
	}

	private static double groundAhead(int tick) {
		double[] m = mouth(0), dir = direction(0, tick);
		return -(m[2] + dir[2] * (m[1] / -dir[1]));
	}

	@Test
	void bodiesInTheStreamBurnAndBodiesBesideItDoNot() {
		double[] origin = {0, 5, 0}, dir = {0, 0, -1};
		assertTrue(inStream(origin, dir, 12, new double[] {0, 5, -6}, 0.3));
		// the stream widens: 1.5 blocks off the axis is out of it near the mouth, in it further along
		assertFalse(inStream(origin, dir, 12, new double[] {1.5, 5, -2}, 0.3));
		assertTrue(inStream(origin, dir, 12, new double[] {1.5, 5, -10}, 0.3));
		// behind the mouth, and past where the stream stopped
		assertFalse(inStream(origin, dir, 12, new double[] {0, 5, 2}, 0.3));
		assertFalse(inStream(origin, dir, 12, new double[] {0, 5, -14}, 0.3));
	}

	@Test
	void turnsTheShortWayAndNoFasterThanTheStep() {
		assertEquals(3.0F, turnToward(0, 1, 0, 3), 1e-4);
		assertEquals(-3.0F, turnToward(0, -1, 0, 3), 1e-4);
		// from 170 toward -170 (= 190): across the seam, not the long way
		double[] west = facing(-170);
		assertEquals(173.0F, turnToward(170, west[0], west[2], 3), 1e-3);
		// close enough: lands exactly on the target
		double[] t = facing(1);
		assertEquals(1.0F, turnToward(0, t[0], t[2], 3), 1e-3);
	}

	@Test
	void streamIsPickedByChanceAndOnlyAFewTimesPerLanding() {
		assertTrue(chooseStream(0.0, 0));
		assertFalse(chooseStream(0.99, 0));
		assertFalse(chooseStream(0.0, MAX_STREAMS));
	}

	@Test
	void bodyTurnsOnlyWhenTheNeckCannotReach() {
		assertFalse(bodyTurns(NECK_ARC - 1.0, false), "the neck covers it: the body stays");
		assertFalse(bodyTurns(-(NECK_ARC - 1.0), false));
		assertTrue(bodyTurns(NECK_ARC + 1.0, false), "out of the neck's reach: the body turns");
		assertTrue(bodyTurns(-(NECK_ARC + 1.0), false));
		assertTrue(bodyTurns(SETTLED_ARC + 1.0, true), "once turning it keeps on until nearly faced");
		assertFalse(bodyTurns(SETTLED_ARC - 1.0, true));
		assertEquals(90.0F, offFacing(0, 1, 0), 1e-4);
		assertEquals(-90.0F, offFacing(0, -1, 0), 1e-4);
	}
}
