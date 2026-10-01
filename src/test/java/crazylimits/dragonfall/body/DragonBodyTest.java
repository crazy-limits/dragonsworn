package crazylimits.dragonfall.body;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DragonBodyTest {
	/** Flies a circle: yaw increasing (a right turn) at `rate` degrees/tick and `speed` blocks/tick. */
	private static DragonBody circle(double rate, double speed, int ticks, DragonBody.Mode mode) {
		DragonBody body = new DragonBody();
		double yaw = 0, x = 0, z = 0;
		for (int i = 0; i < ticks; i++) {
			yaw += rate;
			double r = Math.toRadians(yaw);
			x += Math.sin(r) * speed;
			z -= Math.cos(r) * speed;
			body.tick(yaw, x, 80, z, mode);
		}
		return body;
	}

	@Test
	void aTurnInFlightBanksIntoIt() {
		DragonBody right = circle(4.0, 1.0, 60, DragonBody.Mode.FLIGHT);
		assertTrue(right.roll(1) > 40, "hard right turn banks right: " + right.roll(1));
		assertTrue(right.roll(1) <= DragonBody.MAX_BANK);
		DragonBody left = circle(-4.0, 1.0, 60, DragonBody.Mode.FLIGHT);
		assertTrue(left.roll(1) < -40);
		assertEquals(0.0, circle(4.0, 1.0, 60, DragonBody.Mode.GROUND).roll(1), 1e-6, "no banking on the ground");
	}

	@Test
	void theHeadLeadsAndTheTailTrails() {
		DragonBody body = circle(3.0, 0.2, 40, DragonBody.Mode.GROUND);
		double[] nx = new double[4], ny = new double[4], tx = new double[9], ty = new double[9];
		body.bends(1, nx, ny, tx, ty);
		// a right turn: the neck turns its far end right (negative Y in render terms); the tail still
		// points along the old heading, which leaves each segment swung out to the body's right (positive Y)
		for (double v : ny) assertTrue(v < -1, "neck bends into the turn");
		for (double v : ty) assertTrue(v > 1, "tail trails the turn");
		assertEquals(body.yaw(1), 120 - DragonBody.BODY_LAG * 3.0, 1e-6, "the body is BODY_LAG ticks behind");
	}

	@Test
	void bankedTurnsPullTheNeckTowardTheBodysTop() {
		DragonBody body = circle(4.0, 1.0, 60, DragonBody.Mode.FLIGHT);
		double[] nx = new double[4], ny = new double[4], tx = new double[9], ty = new double[9];
		body.bends(1, nx, ny, tx, ty);
		assertTrue(nx[0] > 0, "steeply banked, a level turn is mostly a pull up toward the inside");
	}

	@Test
	void straightFlightHasNoBends() {
		DragonBody body = circle(0, 1.0, 40, DragonBody.Mode.FLIGHT);
		double[] nx = new double[4], ny = new double[4], tx = new double[9], ty = new double[9];
		body.bends(1, nx, ny, tx, ty);
		for (double v : ny) assertEquals(0, v, 1e-9);
		assertEquals(0, body.roll(1), 1e-9);
	}

	@Test
	void speedingUpLeansForward() {
		DragonBody body = new DragonBody();
		double z = 0, v = 0;
		for (int i = 0; i < 30; i++) {
			v += 0.02;
			z -= v;
			body.tick(0, 0, 80, z, DragonBody.Mode.HOVER);
		}
		assertTrue(body.pitch(1) < -10, "head and body tilt forward: " + body.pitch(1));
	}
}
