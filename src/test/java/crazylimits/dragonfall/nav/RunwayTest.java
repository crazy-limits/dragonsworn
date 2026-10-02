package crazylimits.dragonfall.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RunwayTest {
	private static final int[] SITE = {40, 64, 0};

	@Test
	void openGroundIsARunwayLinedUpWithTheApproach() {
		Runway r = Runway.plan(new NavTest.World(), SITE, -60, 0.5);
		assertNotNull(r);
		assertEquals(1.0, r.dirX, 1e-6);
		// the touch point is the skid's length short of the site, the entry and lead further back and up
		assertEquals(40.5 - Runway.SKID, r.touch[0], 1e-6);
		assertTrue(r.entry[0] < r.touch[0] && r.entry[1] > r.touch[1]);
		assertTrue(r.lead[0] < r.entry[0] && r.lead[1] > r.entry[1]);
		assertEquals(-90.0F, r.yaw(), 1e-4F);    // facing +x
	}

	@Test
	void aWallAcrossTheStripOrTheGlideBlocksIt() {
		NavTest.World wall = new NavTest.World();
		wall.box(39, 64, -3, 39, 66, 3);                       // across the skid
		assertNull(Runway.plan(wall, SITE, -60, 0.5));
		NavTest.World tree = new NavTest.World();
		tree.box(20, 64, -1, 22, 72, 1);                       // under the glide in
		assertNull(Runway.plan(tree, SITE, -60, 0.5));
		NavTest.World step = new NavTest.World();
		step.box(36, 64, -3, 37, 65, 3);                       // a two-block step in the strip
		assertNull(Runway.plan(step, SITE, -60, 0.5));
	}

	@Test
	void thePathStartsWhereTheDragonIsAndSkidsToAStopOnTheSite() {
		Runway r = Runway.plan(new NavTest.World(), SITE, -60, 0.5);
		double d = Runway.startDistance(0.9);
		double[] start = {r.touch[0] - d, r.touch[1] + 8, r.touch[2] + 1}, v = {0.9, -0.1, 0.0};
		double[] p0 = r.path(start, v, 0, 64);
		for (int i = 0; i < 3; i++) {
			assertEquals(start[i], p0[i], 1e-9);
			assertEquals(v[i], p0[i + 3], 1e-9);
		}
		double[] touch = r.path(start, v, Runway.TOUCH_TICKS, 64);
		assertArrayEquals(r.touch, new double[]{touch[0], touch[1], touch[2]}, 1e-9);
		assertEquals(Runway.TOUCH_SPEED, touch[3], 1e-9);
		double[] stop = r.path(start, v, Runway.TOUCH_TICKS + Runway.SKID_TICKS + 5, 64);
		assertEquals(r.site[0], stop[0], 1e-9);
		assertEquals(0.0, stop[3], 1e-9);
		// it slows down all the way in, never climbs, and stays above the ground until the touch
		double lastSpeed = Double.MAX_VALUE, lastY = Double.MAX_VALUE;
		for (int t = 0; t <= Runway.TOUCH_TICKS + Runway.SKID_TICKS; t++) {
			double[] p = r.path(start, v, t, 64);
			double speed = Math.hypot(p[3], p[5]);
			assertTrue(speed <= lastSpeed + 1e-9, "speeds up at tick " + t);
			assertTrue(p[1] <= lastY + 1e-9, "climbs at tick " + t);
			assertTrue(p[1] >= 64 - 1e-9);
			lastSpeed = speed;
			lastY = p[1];
		}
	}
}
