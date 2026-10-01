package crazylimits.dragonfall.limb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyFrameTest {
	@Test
	void theFrameMapsBothWays() {
		BodyFrame f = new BodyFrame().set(10, 64, -3, 37, 12, -8);
		double[] p = {-40, 12, 30};
		double[] back = f.toModel(f.toWorld(p, new double[3]), new double[3]);
		assertArrayEquals(p, back, 1e-9);
		// yaw 0, level: model -z (the nose) is world -z, model -x (the left side) is world -x
		BodyFrame level = new BodyFrame().set(0, 0, 0, 0, 0, 0);
		double[] nose = level.toWorld(new double[]{0, 0, -16}, new double[3]);
		assertEquals(-1.0, nose[2], 1e-9);
		double[] leftSide = level.toWorld(new double[]{-16, 0, 0}, new double[3]);
		assertEquals(-1.0, leftSide[0], 1e-9);
	}

	@Test
	void theBodyTiltsWithTheSlopeButLess() {
		GroundFit fit = new GroundFit();
		// front feet a block higher, left side half a block higher
		for (int i = 0; i < 40; i++) fit.tick(true, new double[]{0.5, 0.0, 1.5, 1.0}, 64.0);
		assertTrue(fit.pitch(1) > 4 && fit.pitch(1) < Math.toDegrees(Math.atan2(1.0, 5.0)), "nose up, less than the slope: " + fit.pitch(1));
		assertTrue(fit.roll(1) > 0, "left higher: the right side dips");
		for (int i = 0; i < 40; i++) fit.tick(false, new double[4], 64.0);
		assertEquals(0.0, fit.pitch(1), 0.01, "level again off the ground");
	}

	@Test
	void theBodyDoesNotJumpWhenThePositionStepsUp() {
		GroundFit fit = new GroundFit();
		double[] flat = {0, 0, 0, 0};
		for (int i = 0; i < 40; i++) fit.tick(true, flat, 64.0);
		// the position steps a block up onto a ledge while the feet are still on the old ground
		fit.tick(true, new double[]{-1, -1, -1, -1}, 65.0);
		double drawn = 65.0 + fit.lift(1), before = 64.0 + fit.lift(0);
		assertEquals(64.0, drawn, 0.3, "the body stays where it was in the world");
		assertEquals(drawn, before, 0.3, "and in between ticks too: " + before + " -> " + drawn);
		for (int i = 0; i < 40; i++) fit.tick(true, new double[]{-1, -1, -1, -1}, 65.0);
		assertEquals(64.0, 65.0 + fit.lift(1), 0.01, "it stands on the ground under its feet");
	}
}
