package crazylimits.dragonfall.body;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GripTest {
	@Test
	void encodesHoldAndPrey() {
		assertEquals(0, Grip.encode(Grip.Hold.NONE, 42));
		assertEquals(0, Grip.encode(Grip.Hold.JAW, -1));
		assertEquals(Grip.Hold.NONE, Grip.hold(0));
		assertEquals(-1, Grip.entity(0));
		for (Grip.Hold hold : new Grip.Hold[]{Grip.Hold.REACH, Grip.Hold.TALON, Grip.Hold.JAW}) {
			for (int id : new int[]{0, 1, 77, 1 << 20}) {
				int bits = Grip.encode(hold, id);
				assertNotEquals(0, bits);
				assertEquals(hold, Grip.hold(bits));
				assertEquals(id, Grip.entity(bits));
			}
		}
	}

	@Test
	void shakeSwingsBothWaysAndStopsWithItsWeight() {
		double min = 0, max = 0;
		for (double t = 0; t < 200; t += 0.25) {
			double yaw = Grip.shakeYaw(3, t, 1.0);
			min = Math.min(min, yaw);
			max = Math.max(max, yaw);
			assertEquals(0.0, Grip.shakeYaw(3, t, 0.0), 1e-12);
			assertEquals(0.0, Grip.shakeRoll(t, 0.0), 1e-12);
			assertTrue(Math.abs(Grip.shakeRoll(t, 1.0)) < 25.0);
		}
		assertTrue(max > 8.0 && min < -8.0, "the head is thrown to both sides: " + min + ".." + max);
		assertTrue(max < 16.0 && min > -16.0, "within a neck segment's reach");
		assertTrue(Math.abs(Grip.shakeYaw(0, 3.0, 1.0)) < Math.abs(Grip.shakeYaw(3, 3.0, 1.0)), "the head end throws furthest");
	}
}
