package crazylimits.dragonsworn.body;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WingRootTest {
	@Test
	void restsWhereItIsBuilt() {
		assertEquals(0.0, WingRoot.fold(0, 0, 0), 1e-9);
	}

	@Test
	void matchesTheRigsForwardKinematics() {
		// the turn that points the strip at the aim through tools/rig.py's FK of the built model
		assertEquals(22.648, WingRoot.fold(5, -8, -15), 0.01);
		assertEquals(-24.485, WingRoot.fold(-6, 10, 12), 0.01);
	}

	@Test
	void turnsAgainstTheFlap() {
		// the shoulder carries the strip round with the membrane; pointing back at the body turns it the other way
		for (double z = -20; z <= 20; z += 5)
			if (z != 0) assertEquals(Math.signum(-z), Math.signum(WingRoot.fold(0, 0, z)), 0.0);
	}

	@Test
	void staysNearItsRestAngle() {
		for (double x = -60; x <= 60; x += 15)
			for (double y = -40; y <= 40; y += 10)
				for (double z = -70; z <= 70; z += 10)
					assertTrue(Math.abs(WingRoot.fold(x, y, z)) <= WingRoot.MAX_TURN);
	}
}
