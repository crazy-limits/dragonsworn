package crazylimits.dragonsworn.body;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyPushTest {
	/** A player-sized box (0.6 x 1.8) with its feet at {@code x, y, z}. */
	private static double[] player(double x, double y, double z) {
		return new double[] {x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3};
	}

	private static final double[] BODY = {0.0, 0.0, 0.0, 2.6, 2.4, 2.6};

	@Test
	void onlyWhatIsInsideIsPushed() {
		assertTrue(BodyPush.overlaps(player(1.3, 0.0, 1.3), BODY));
		assertTrue(BodyPush.overlaps(player(2.7, 0.0, 1.3), BODY));
		assertFalse(BodyPush.overlaps(player(5.0, 0.0, 1.0), BODY));
		assertFalse(BodyPush.overlaps(player(2.9, 0.0, 1.0), BODY));   // its side against the box's face
		assertFalse(BodyPush.overlaps(player(1.0, 2.4, 1.0), BODY));   // standing on top
	}

	@Test
	void awayFromTheMiddleAsMobsPushEachOther() {
		double[] out = new double[2];
		BodyPush.push(2.3, 1.3, 1.3, 1.3, 0.0, 0.0, out);
		// one block apart along x: vanilla's full push
		assertEquals(BodyPush.STRENGTH, out[0], 1e-9);
		assertEquals(0.0, out[1], 1e-9);
		BodyPush.push(1.3, 0.3, 1.3, 1.3, 0.0, 0.0, out);
		assertEquals(-BodyPush.STRENGTH, out[1], 1e-9);
		// never more than vanilla's strength, however close or far
		for (double d = 0.02; d < 3.0; d += 0.1) {
			BodyPush.push(1.3 + d, 1.3 + d / 2, 1.3, 1.3, 0.0, 0.0, out);
			assertTrue(Math.hypot(out[0], out[1]) <= BodyPush.STRENGTH * Math.sqrt(2) + 1e-9);
			assertTrue(out[0] > 0.0 && out[1] > 0.0);
		}
	}

	@Test
	void onTheMiddleAwayFromTheDragon() {
		double[] out = new double[2];
		BodyPush.push(1.3, 1.3, 1.3, 1.3, 1.3, 5.0, out);
		assertTrue(out[1] < 0.0);
		assertEquals(0.0, out[0], 1e-9);
		BodyPush.push(1.3, 1.3, 1.3, 1.3, 1.3, 1.3, out);
		assertArrayEquals(new double[2], out);
	}
}
