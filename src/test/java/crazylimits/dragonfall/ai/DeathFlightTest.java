package crazylimits.dragonfall.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeathFlightTest {
	private static final double[] ALTAR = {0.5, 65.0, 0.5};

	@Test
	void itFliesOverTheAltarThenRisesThenIsDone() {
		DeathFlight f = new DeathFlight();
		f.start(80, 90, -40, ALTAR);
		assertEquals(DeathFlight.Stage.APPROACH, f.tick(80, 90, -40, false));
		assertEquals(65.0 + DeathFlight.HEIGHT, f.targetY());
		assertEquals(0.5, f.targetX());
		assertFalse(f.hovers(80, -40), "far off it flies");
		assertTrue(f.hovers(5, 5), "near the altar it hovers in");
		// over the altar
		assertEquals(DeathFlight.Stage.RISE, f.tick(1.0, 84.0, 1.0, false));
		assertEquals(84.0 + DeathFlight.RISE, f.targetY(), 1e-9, "straight up from where it arrived");
		assertTrue(f.hovers(1, 1));
		assertEquals(DeathFlight.Stage.RISE, f.tick(1.0, 90.0, 1.0, false));
		assertEquals(DeathFlight.Stage.DONE, f.tick(1.0, 84.0 + DeathFlight.RISE - 0.5, 1.0, false));
	}

	@Test
	void withoutAnAltarItOnlyRises() {
		DeathFlight f = new DeathFlight();
		f.start(10, 70, 10, null);
		assertEquals(DeathFlight.Stage.RISE, f.stage());
		assertEquals(70 + DeathFlight.RISE, f.targetY());
		assertEquals(10, f.targetX());
		assertEquals(DeathFlight.Stage.RISE, f.tick(10, 75, 10, false));
		assertEquals(DeathFlight.Stage.DONE, f.tick(10, 76, 10, true), "a ceiling ends the rise");
	}

	@Test
	void itNeverFliesForever() {
		DeathFlight f = new DeathFlight();
		f.start(200, 90, 200, ALTAR);
		DeathFlight.Stage s = f.stage();
		for (int t = 0; t <= DeathFlight.MAX_TICKS && s != DeathFlight.Stage.DONE; t++) s = f.tick(200, 90, 200, false);
		assertEquals(DeathFlight.Stage.DONE, s, "stuck on the way, it dies where it is");
		f.start(0, 80, 0, null);
		s = f.stage();
		for (int t = 0; t <= DeathFlight.MAX_RISE_TICKS && s != DeathFlight.Stage.DONE; t++) s = f.tick(0, 80, 0, false);
		assertEquals(DeathFlight.Stage.DONE, s, "a rise that gets nowhere ends too");
	}
}
