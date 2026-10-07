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
		// vanilla's spikes: 76 + 3 x rank tall, rank 0..9
		int[] warded = new int[4];
		for (int difficulty = 0; difficulty < 4; difficulty++) {
			for (int rank = 0; rank < 10; rank++) {
				boolean w = CrystalWard.warded(76 + 3 * rank, CrystalWard.count(difficulty));
				if (w) warded[difficulty]++;
				if (w && rank > 0) assertTrue(CrystalWard.warded(76 + 3 * (rank - 1), CrystalWard.count(difficulty)), "a shorter spire is unwarded");
			}
		}
		assertEquals(warded[0], warded[1], "peaceful wards as easy does");
		assertTrue(warded[1] >= 2, "at least vanilla's two caged crystals on easy");
		assertTrue(warded[1] < warded[2] && warded[2] < warded[3], "more the harder the difficulty");
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
