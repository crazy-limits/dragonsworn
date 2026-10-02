package crazylimits.dragonsworn.body;

import crazylimits.dragonsworn.anim.DragonAnim;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StrikeTest {
	private static DragonBody facing(double yaw) {
		DragonBody body = new DragonBody();
		body.tick(yaw, 0, 64, 0, DragonBody.Mode.GROUND);
		return body;
	}

	/** Where the part is at the blow's frame with the strike's bends at full weight, via the PartSolver. */
	private static double[] blowPart(DragonAnim anim, DragonBody body, Strike strike, int part) {
		double[] out = new double[PoseTrack.PARTS * 3];
		new PartSolver().solve(anim, Strike.hitSeconds(anim), body, strike, 1.0F, out);
		return new double[]{out[part * 3], out[part * 3 + 1], out[part * 3 + 2]};
	}

	private static double distance(double[] a, double x, double y, double z) {
		return Math.sqrt((a[0] - x) * (a[0] - x) + (a[1] - y) * (a[1] - y) + (a[2] - z) * (a[2] - z));
	}

	@Test
	void theJawsCloseExactlyOnAReachablePoint() {
		DragonBody body = facing(0);   // yaw 0: facing -z (north); +x is its right
		Strike strike = new Strike();
		double[][] aims = {{0, 1.0, -6}, {3, 0.9, -4}, {-3, 2.0, -5}, {2, 0.9, -5}};
		for (double[] a : aims) {
			strike.aim(DragonAnim.ATTACK, a[0], a[1], a[2]);
			assertTrue(strike.solve(body, 1.0F) < 0.05, "reaches " + a[0] + ", " + a[1] + ", " + a[2]);
			// the hitbox (what the server and the renderer place) is on the aim too
			assertTrue(distance(blowPart(DragonAnim.ATTACK, body, strike, Parts.HEAD), a[0], a[1], a[2]) < 0.05);
		}
	}

	@Test
	void theTailWhipsOntoAPointBesideOrBehind() {
		DragonBody body = facing(0);
		Strike strike = new Strike();
		double[][] aims = {{6, 0.9, 4}, {-5, 0.9, 7}, {0, 0.9, 10}, {6, 0.9, -1}};
		for (double[] a : aims) {
			strike.aim(DragonAnim.TAIL_SWEEP, a[0], a[1], a[2]);
			assertTrue(strike.solve(body, 1.0F) < 0.05, "reaches " + a[0] + ", " + a[1] + ", " + a[2]);
			assertTrue(distance(blowPart(DragonAnim.TAIL_SWEEP, body, strike, Parts.TAIL_TIP), a[0], a[1], a[2]) < 0.05);
		}
	}

	@Test
	void theAimTurnsWithTheDragon() {
		DragonBody body = facing(90);  // facing +x (east): its right is +z
		Strike strike = new Strike();
		strike.aim(DragonAnim.ATTACK, 5, 1.0, 1.5);
		assertTrue(strike.solve(body, 1.0F) < 0.05);
		double[] blow = new double[3];
		strike.blow(body, 1.0F, blow);
		assertEquals(0.0, distance(blow, 5, 1.0, 1.5), 0.05);
	}

	@Test
	void outOfReachItStopsShort() {
		DragonBody body = facing(0);
		Strike strike = new Strike();
		strike.aim(DragonAnim.ATTACK, 0, 1.0, -20);
		assertTrue(strike.solve(body, 1.0F) > Strike.BITE_RADIUS, "a bite cannot reach 20 blocks");
		strike.aim(DragonAnim.TAIL_SWEEP, 0, 1.0, -14);
		assertTrue(strike.solve(body, 1.0F) > Strike.TAIL_RADIUS, "the tail cannot reach far in front");
	}

	@Test
	void everySegmentIsOnTheAimAtTheBlow() {
		for (DragonAnim anim : new DragonAnim[]{DragonAnim.ATTACK, DragonAnim.TAIL_SWEEP}) {
			for (int k = 0; k < 9; k++) assertEquals(1.0, Strike.weight(anim, Strike.hitSeconds(anim), k), 1e-9);
			assertEquals(0.0, Strike.weight(anim, 0.0, 0), 1e-9, "starts from the keyframes");
			assertEquals(0.0, Strike.weight(anim, PoseTrack.length(anim), 0), 1e-9, "ends on the keyframes");
		}
		assertTrue(Strike.weight(DragonAnim.TAIL_SWEEP, 0.45, 4) < -0.4, "the tail is cocked away first");
		assertTrue(Strike.weight(DragonAnim.TAIL_SWEEP, 0.6, 0) > Strike.weight(DragonAnim.TAIL_SWEEP, 0.6, 8), "the root whips first");
	}

	@Test
	void noAimNoBends() {
		Strike strike = new Strike();
		double[] nx = new double[5], ny = new double[5], tx = new double[9], ty = new double[9];
		strike.addBends(DragonAnim.ATTACK, 0.6, facing(0), 1.0F, nx, ny, tx, ty);
		for (double v : nx) assertEquals(0.0, v);
		strike.aim(DragonAnim.ATTACK, 3, 1, -6);
		strike.addBends(DragonAnim.IDLE, 0.6, facing(0), 1.0F, nx, ny, tx, ty);
		for (double v : nx) assertEquals(0.0, v, "only while the strike plays");
	}

	@Test
	void theBreathStretchesTheNeckStraightAtTheAim() {
		DragonBody body = facing(0);
		Strike strike = new Strike();
		double[][] aims = {{0, 0.5, -16}, {5, 0.5, -14}, {-6, 1.0, -10}};
		for (double[] a : aims) {
			strike.aim(DragonAnim.BREATH, a[0], a[1], a[2]);
			assertTrue(strike.solve(body, 1.0F) < 0.1, "the head points at " + a[0] + ", " + a[1] + ", " + a[2]);
			double[] out = new double[PoseTrack.PARTS * 3];
			new PartSolver().solve(DragonAnim.BREATH, Strike.hitSeconds(DragonAnim.BREATH), body, strike, 1.0F, out);
			// the neck's hitboxes and the head lie on one line toward the aim: a straight neck
			double[] head = {out[0], out[1], out[2]};
			double dx = a[0] - head[0], dy = a[1] - head[1], dz = a[2] - head[2], len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			for (int part : new int[]{1, 8}) {
				double px = out[part * 3] - head[0], py = out[part * 3 + 1] - head[1], pz = out[part * 3 + 2] - head[2];
				double along = (px * dx + py * dy + pz * dz) / len;
				double off = Math.sqrt(Math.max(0, px * px + py * py + pz * pz - along * along));
				assertTrue(off < 0.35, "neck part " + part + " is " + off + " off the line");
				assertTrue(along < 0, "the neck is behind the head");
			}
		}
	}

	@Test
	void thePassStretchesTheNeckDownAtTheGround() {
		DragonBody body = new DragonBody();
		body.tick(0, 0, 64, 0, DragonBody.Mode.FLIGHT);
		Strike strike = new Strike();
		// the pass's cone: ahead and below, a little to either side (yaw 0 faces -z)
		double[][] aims = {{0, -11, -11}, {5, -11, -8}, {-4, -11, -14}, {0, -11, -3}};
		for (double[] a : aims) {
			strike.aim(DragonAnim.GLIDE_BREATH, a[0], a[1], a[2]);
			assertTrue(strike.solve(body, 1.0F) < 0.1, "the head points at " + a[0] + ", " + a[1] + ", " + a[2]);
		}
		// it carries the bends through the stream and none at its start
		assertEquals(0.0, Strike.weight(DragonAnim.GLIDE_BREATH, 0.2, 0));
		assertEquals(1.0, Strike.weight(DragonAnim.GLIDE_BREATH, Strike.hitSeconds(DragonAnim.GLIDE_BREATH), 0));
	}

	@Test
	void theBitesSatUpOrClingingReachWhatTheBiteOnAllFoursDoes() {
		DragonBody body = facing(0);
		Strike strike = new Strike();
		// a body 5-6 blocks ahead (the narrow footholds are picked that far from the prey), and to the sides
		double[][] aims = {{0, 1.0, -5}, {2, 0.9, -5}, {-2, 2.0, -5}, {0, 2.0, -6}, {1, 1.0, -5.5}};
		for (DragonAnim bite : new DragonAnim[]{DragonAnim.UPRIGHT_BITE, DragonAnim.CLING_BITE}) {
			for (double[] a : aims) {
				strike = new Strike();
				strike.aim(bite, a[0], a[1], a[2]);
				double miss = strike.solve(body, 1.0F);
				assertTrue(miss < 0.05, bite + " reaches " + a[0] + ", " + a[1] + ", " + a[2] + ": misses by " + miss);
			}
			// 6 ahead at a body's middle: within the bite's reach as the fight counts it (half its radius)
			strike = new Strike();
			strike.aim(bite, 0, 1.0, -6);
			assertTrue(strike.solve(body, 1.0F) < Strike.radius(bite) * 0.5, bite + " reaches 6 ahead");
		}
	}
}
