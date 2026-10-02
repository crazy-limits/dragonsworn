package crazylimits.dragonsworn.ai;

import crazylimits.dragonsworn.config.DragonConfig;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RoamingTest {
	private static double turn(double from, double to) {
		return Math.abs(Math.IEEEremainder(to - from, 2 * Math.PI));
	}

	@Test
	void legsDriftInsteadOfCircling() {
		Random random = new Random(1);
		double heading = 0.3, total = 0;
		for (int i = 0; i < 1000; i++) {
			double next = Roaming.nextHeading(heading, 0, random);
			assertTrue(turn(heading, next) <= Roaming.DRIFT + 1e-9);
			total += next - heading;
			heading = next;
		}
		// a random walk of headings, not a steady turn one way round a center
		assertTrue(Math.abs(total / 1000) < 0.1);
	}

	@Test
	void laterAttemptsSwingWiderUpToTurningBack() {
		Random random = new Random(2);
		double widest = 0;
		for (int i = 0; i < 2000; i++) widest = Math.max(widest, turn(0, Roaming.nextHeading(0, 6, random)));
		assertTrue(widest > Math.toRadians(170), "can turn right round");
	}

	@Test
	void spellsStayInRange() {
		Random random = new Random(3);
		for (int i = 0; i < 1000; i++) {
			int air = Roaming.airSpell(random), ground = Roaming.groundSpell(random), pause = Roaming.pause(random);
			assertTrue(air >= DragonConfig.FLIGHT_MIN.get() && air <= DragonConfig.FLIGHT_MAX.get());
			assertTrue(ground >= DragonConfig.GROUND_MIN.get() && ground <= DragonConfig.GROUND_MAX.get());
			assertTrue(pause >= DragonConfig.PAUSE_MIN.get() && pause <= DragonConfig.PAUSE_MAX.get());
		}
	}

	@Test
	void headingOfYawMatchesTheNose() {
		// yaw 0: nose toward -z; yaw 90: nose toward +x
		assertEquals(-Math.PI / 2, Roaming.headingOfYaw(0), 1e-9);
		assertEquals(0, Roaming.headingOfYaw(90), 1e-9);
	}
}
