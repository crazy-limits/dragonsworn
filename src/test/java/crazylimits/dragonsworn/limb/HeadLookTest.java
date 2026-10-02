package crazylimits.dragonsworn.limb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeadLookTest {
	private static HeadLook settle(double yaw, double pitch, double attention) {
		HeadLook look = new HeadLook();
		for (int t = 0; t < 120; t++) look.update(t, yaw, pitch, attention);
		return look;
	}

	@Test
	void theWholeTurnIsSharedDownTheNeckHeadMost() {
		HeadLook look = settle(40, 10, 1);
		double yaw = look.headYaw(), pitch = look.headPitch();
		for (int i = 0; i < 4; i++) {
			yaw += look.neckYaw(i);
			pitch += look.neckPitch(i);
			assertTrue(look.neckYaw(i) < look.headYaw(), "the head turns most");
		}
		assertEquals(40, yaw, 0.1);
		assertEquals(10, pitch, 0.1);
		assertTrue(look.neckYaw(0) < look.neckYaw(2), "the base turns least");
	}

	@Test
	void itTurnsOnlySoFar() {
		assertEquals(HeadLook.MAX_YAW, settle(100, 0, 1).yaw(), 0.1);
		assertEquals(-HeadLook.MAX_DOWN, settle(0, -80, 1).pitch(), 0.1);
		assertEquals(0.0, settle(170, 0, 1).yaw(), 0.01, "behind it: lets go");
	}

	@Test
	void theTailSwingsTheOtherWay() {
		HeadLook look = settle(50, 0, 1);
		// head left (+yaw) puts the tail tip right (+Y on a tail segment)
		assertTrue(look.tailYaw(9) > 0);
		assertEquals(50 * HeadLook.TAIL_COUNTER / 9, look.tailYaw(9), 0.1);
	}

	@Test
	void aStruckTailLetsItsSwingGo() {
		HeadLook look = settle(50, 0, 1);
		for (int t = 120; t < 240; t++) look.update(t, 50, 0, 1, false);
		assertEquals(50, look.yaw(), 0.1, "the head still looks");
		assertEquals(0.0, look.tailYaw(9), 0.01, "the strike has the tail");
		look.update(240, 50, 0, 1, true);
		assertTrue(look.tailYaw(9) < 0.5 * 50 * HeadLook.TAIL_COUNTER / 9, "no snap back");
	}

	@Test
	void attentionFadesInAndOut() {
		HeadLook look = new HeadLook();
		look.update(0, 40, 0, 1);
		assertTrue(look.yaw() < 10, "no snap");
		for (int t = 1; t < 120; t++) look.update(t, 40, 0, 1);
		assertEquals(40, look.yaw(), 0.5);
		for (int t = 120; t < 240; t++) look.update(t, Double.NaN, Double.NaN, 1);
		assertEquals(0, look.yaw(), 0.1, "nothing to look at: back to the animation");
	}

	@Test
	void frameRateDoesNotChangeTheMotion() {
		HeadLook ticks = new HeadLook(), frames = new HeadLook();
		for (int t = 0; t <= 20; t++) ticks.update(t, 30, 0, 1);
		for (int f = 0; f <= 80; f++) frames.update(f / 4.0, 30, 0, 1);
		assertEquals(ticks.yaw(), frames.yaw(), 0.6);
	}
}
