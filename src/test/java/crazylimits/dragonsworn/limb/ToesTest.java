package crazylimits.dragonsworn.limb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToesTest {
	private static final double N = Double.NEGATIVE_INFINITY;
	private static final double[] SIDE = {-1.0, 0.0, 1.0}, NONE = {N, N, N, N};

	private static void run(Toes toes, double ticks, double contact, double open, double grip, double[] stop) {
		for (double t = 0; t < ticks; t += 0.37) toes.update(0.37, t, contact, open, grip, SIDE, stop);
	}

	@Test
	void plantedToesAreStraight() {
		Toes toes = new Toes();
		run(toes, 60, 0.0, 0.0, 0.0, NONE);
		for (int i = 0; i < Toes.COUNT; i++) assertTrue(toes.curl(i) < -10.0, "in the air the claws are drawn in");
		run(toes, 60, 1.0, 0.0, 0.0, NONE);
		for (int i = 0; i < Toes.COUNT; i++) assertEquals(0.0, toes.curl(i), 0.01, "straight on the ground");
		assertTrue(toes.spread(0) < -3.0 && toes.spread(2) > 3.0 && toes.spread(1) == 0.0, "splayed outward under the weight");
	}

	@Test
	void liftedToesFollowWithALag() {
		Toes toes = new Toes();
		run(toes, 40, 1.0, 0.0, 0.0, NONE);
		toes.update(0.5, 40, 0.0, 0.0, 0.0, SIDE, NONE);
		assertTrue(toes.curl(1) > -10.0, "not at once");
		run(toes, 40, 0.0, 0.0, 0.0, NONE);
		assertEquals(Toes.AIR_CURL, toes.curl(1), Toes.WAVE + 2.0);
	}

	@Test
	void theGripClosesOnThePreyAndFreezesUntilItLetsGo() {
		Toes toes = new Toes();
		run(toes, 40, 0.0, 1.0, 0.0, NONE);
		assertTrue(toes.curl(1) > 20.0 && toes.spread(2) > 15.0 && toes.spread(0) < -15.0, "spread wide for the catch");
		double[] prey = {-80.0, -60.0, -80.0, -95.0};
		for (double g = 0.0; g < 1.0; g += 0.1) toes.update(0.37, 0, 0.0, 0.0, g, SIDE, prey);
		run(toes, 5, 0.0, 0.0, 1.0, prey);
		assertTrue(toes.frozen());
		for (int i = 0; i < Toes.COUNT; i++) assertEquals(prey[i], toes.curl(i), 1e-9, "closed on the prey");
		// held: frozen, whatever the stops or the time say now
		double[] before = new double[Toes.COUNT];
		for (int i = 0; i < Toes.COUNT; i++) before[i] = toes.curl(i);
		for (double t = 0; t < 100; t += 0.37) {
			toes.update(0.37, t, 0.0, 0.0, 1.0, SIDE, NONE);
			for (int i = 0; i < Toes.COUNT; i++) assertEquals(before[i], toes.curl(i), 0.0, "frozen while it holds");
		}
		// let go: they open again
		run(toes, 40, 0.0, 0.0, 0.0, NONE);
		assertFalse(toes.frozen());
		assertEquals(Toes.AIR_CURL, toes.curl(1), Toes.WAVE + 2.0);
	}

	@Test
	void nothingInTheGripClosesAllTheWay() {
		Toes toes = new Toes();
		run(toes, 20, 0.0, 0.0, 1.0, NONE);
		for (int i = 0; i < Toes.COUNT; i++) assertEquals(Toes.GRIP_CURL, toes.curl(i), 1e-9);
	}
}
