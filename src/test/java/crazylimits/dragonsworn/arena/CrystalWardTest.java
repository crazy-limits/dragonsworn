package crazylimits.dragonsworn.arena;

import crazylimits.dragonsworn.config.DragonConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrystalWardTest {
	@BeforeEach
	void defaults() {
		DragonConfig.defaults();
	}

	@Test
	void theShortestSpiresAreWardedFirstMoreTheHarderTheDifficulty() {
		// vanilla's spikes: 76 + 3 x rank tall, rank 0..9, in a shuffled order round the ring
		int[] heights = {85, 76, 103, 91, 79, 97, 82, 100, 94, 88};
		int[] warded = new int[4];
		for (int difficulty = 0; difficulty < 4; difficulty++) {
			for (int i = 0; i < heights.length; i++) {
				boolean w = CrystalWard.warded(heights, i, CrystalWard.count(difficulty));
				if (w) warded[difficulty]++;
				for (int j = 0; j < heights.length; j++)
					if (w && heights[j] < heights[i]) assertTrue(CrystalWard.warded(heights, j, CrystalWard.count(difficulty)), "a shorter spire is unwarded");
			}
		}
		assertEquals(warded[0], warded[1], "peaceful wards as easy does");
		assertTrue(warded[1] >= 2, "at least vanilla's two caged crystals on easy");
		assertTrue(warded[1] < warded[2] && warded[2] < warded[3], "more the harder the difficulty");
	}

	@Test
	void anyLayoutWardsExactlyTheCountShortestEqualHeightsInOrder() {
		// Stellarity's ring: its own places and heights, two of them equally tall
		int[] heights = {100, 105, 94, 106, 105, 93, 100, 96, 87, 95};
		for (int count = 0; count <= 10; count++) {
			int n = 0;
			for (int i = 0; i < heights.length; i++) if (CrystalWard.warded(heights, i, count)) n++;
			assertEquals(count, n, "warded crystals for count " + count);
		}
		assertTrue(CrystalWard.warded(heights, 8, 1), "the shortest goes first");
		assertTrue(CrystalWard.warded(heights, 0, 6) && !CrystalWard.warded(heights, 6, 6), "of two equal heights, the first");
		assertFalse(CrystalWard.warded(heights, 10, 10), "a spike not in the layout");
	}

	@Test
	void ringsStayAboveTheSpireAndApart() {
		double glass = Math.sqrt(3.0) / 2.0 + 0.4;   // the crystal's 1-block glass cube, half a diagonal, plus its bob
		double last = glass;
		for (CrystalWard.Ring ring : CrystalWard.RINGS) {
			assertTrue(ring.dip() < CrystalWard.CENTER, "a ring sinks into the spire's top: dips " + ring.dip());
			assertTrue(ring.radius() > last + 0.1, "rings too close: " + ring.radius());
			last = Math.hypot(ring.radius(), CrystalWard.PANE_HEIGHT / 2.0);
			assertTrue(CrystalWard.PANES * ring.cellWidth() <= CrystalWard.TEXTURE_WIDTH);
		}
		assertTrue(CrystalWard.RADIUS >= last, "the bounce sphere is inside the outer ring");
		assertEquals(22.5, CrystalWard.STEP, 1e-9);
	}

	@Test
	void aShotIntoTheWardBouncesOffItsSurface() {
		// straight at the centre from 3 blocks east, 3 blocks a tick: thrown back east
		double[] b = CrystalWard.bounce(3.0, 0.0, 0.0, -3.0, 0.0, 0.0, 0.0, 0.0, 0.0);
		assertNotNull(b);
		assertTrue(b[0] > CrystalWard.RADIUS && b[0] < CrystalWard.RADIUS + 0.1, "set on the sphere: " + b[0]);
		assertEquals(3.0 * CrystalWard.BOUNCE, b[3], 1e-9);
		// a glancing shot: the part along the surface is kept, the part into it turned out
		b = CrystalWard.bounce(-3.0, 1.8, 0.0, 3.0, 0.0, 0.0, 0.0, 0.0, 0.0);
		assertNotNull(b);
		assertTrue(b[4] > 0.0 && b[3] > 0.0, "glances up and on");
		double r = Math.sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]);
		assertTrue(r > CrystalWard.RADIUS, "outside the sphere after the bounce");
	}

	@Test
	void shotsThatMissOrStartInsideFlyOn() {
		assertNull(CrystalWard.bounce(5.0, 0.0, 0.0, -1.0, 0.0, 0.0, 0.0, 0.0, 0.0), "not there yet this step");
		assertNull(CrystalWard.bounce(3.0, 3.0, 0.0, -3.0, 0.0, 0.0, 0.0, 0.0, 0.0), "passes over");
		assertNull(CrystalWard.bounce(3.0, 0.0, 0.0, 3.0, 0.0, 0.0, 0.0, 0.0, 0.0), "flies away");
		assertNull(CrystalWard.bounce(1.0, 0.0, 0.0, 3.0, 0.0, 0.0, 0.0, 0.0, 0.0), "fired from inside");
		assertNull(CrystalWard.bounce(1.0, 0.0, 0.0, -3.0, 0.0, 0.0, 0.0, 0.0, 0.0), "inside, toward the crystal");
	}
}
