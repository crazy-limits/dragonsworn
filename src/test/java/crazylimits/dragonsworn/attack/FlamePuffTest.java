package crazylimits.dragonsworn.attack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlamePuffTest {
	private static final double EPS = 1e-9;

	@Test
	void movesAsTheFlameParticle() {
		FlamePuff puff = new FlamePuff(new double[] {0, 10, 0}, new double[] {1, 0, 0}, 100.0);
		double[] from = new double[3];
		// the particle: move by its velocity, then velocity *= friction, then the rise
		double x = 0, y = 10, vx = 1, vy = 0;
		for (int t = 0; t < 10; t++) {
			puff.step(from);
			assertEquals(x, from[0], EPS);
			x += vx;
			y += vy;
			vx *= FlamePuff.FRICTION;
			vy = vy * FlamePuff.FRICTION + FlamePuff.RISE;
			assertEquals(x, puff.pos()[0], EPS);
			assertEquals(y, puff.pos()[1], EPS);
		}
	}

	@Test
	void burnsOutWhenItTurnsToSmoke() {
		FlamePuff puff = new FlamePuff(new double[3], new double[] {0, 0, FlamePuff.JET}, 100.0);
		double[] from = new double[3];
		for (int t = 0; t < FlamePuff.FIRE_TICKS; t++) {
			assertTrue(puff.burning());
			puff.step(from);
		}
		assertFalse(puff.burning());
		assertEquals(FlamePuff.reach(FlamePuff.JET), puff.pos()[2], EPS);
	}

	@Test
	void burnsOutAtItsRange() {
		FlamePuff puff = new FlamePuff(new double[3], new double[] {2, 0, 0}, 3.0);
		double[] from = new double[3];
		puff.step(from);
		assertTrue(puff.burning());
		puff.step(from);
		assertFalse(puff.burning());
	}

	@Test
	void widensWithTheDistanceFlownAndStopsOnABlock() {
		FlamePuff puff = new FlamePuff(new double[3], new double[] {1, 0, 0}, 100.0);
		assertEquals(BreathAttack.MOUTH_RADIUS, puff.radius(), EPS);
		puff.step(new double[3]);
		assertEquals(BreathAttack.MOUTH_RADIUS + BreathAttack.SPREAD, puff.radius(), EPS);
		puff.stopAt(new double[] {0.5, 0, 0});
		assertFalse(puff.burning());
		assertEquals(0.5, puff.pos()[0], EPS);
	}

	@Test
	void thePerchedStreamBurnsAboutElevenBlocksOut() {
		assertEquals(10.9, FlamePuff.reach(FlamePuff.JET), 0.05);
	}
}
