package crazylimits.dragonfall.body;

import crazylimits.dragonfall.anim.DragonAnim;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PartSolverTest {
	private static double[] solve(DragonAnim anim, double t, DragonBody body) {
		double[] out = new double[PoseTrack.PARTS * 3];
		new PartSolver().solve(anim, t, body, null, 1, out);
		return out;
	}

	private static DragonBody facing(double yaw) {
		DragonBody body = new DragonBody();
		body.tick(yaw, 0, 64, 0, DragonBody.Mode.GROUND);
		return body;
	}

	@Test
	void everyAnimationHasATrack() {
		double[] out = new double[PoseTrack.POINTS * 3];
		for (DragonAnim anim : DragonAnim.values()) {
			PoseTrack.sample(anim, 0.3, out);
			assertTrue(PoseTrack.length(anim) > 0);
		}
		assertEquals("head", PoseTrack.partName(0));
	}

	@Test
	void theHeadIsInFrontAndTheTailBehind() {
		double[] p = solve(DragonAnim.IDLE, 0, facing(0));
		// yaw 0 faces north (-z)
		assertTrue(p[2] < -4, "head ahead: " + p[2]);
		assertTrue(p[12 * 3 + 2] > 8, "tail tip behind: " + p[12 * 3 + 2]);
		assertTrue(p[1] > 2, "a standing dragon holds its head up: " + p[1]);
		// the hind legs reach the ground
		assertTrue(p[19 * 3 + 1] < 2.5);
	}

	@Test
	void yawTurnsEverything() {
		double[] north = solve(DragonAnim.GLIDE, 0, facing(0));
		double[] east = solve(DragonAnim.GLIDE, 0, facing(90));
		// facing east the head is at +x, where it was at -z
		assertEquals(-north[2], east[0], 1e-6);
		assertEquals(north[1], east[1], 1e-6);
	}

	@Test
	void wingsReachFarOutInTheGlide() {
		double[] p = solve(DragonAnim.GLIDE, 0, facing(0));
		assertTrue(p[15 * 3] < -9, "left fan tip far to the left: " + p[15 * 3]);
		assertTrue(p[18 * 3] > 9, "right fan tip far to the right: " + p[18 * 3]);
	}

	@Test
	void wingsTurnAgainstTheHoversRaisedChest() {
		DragonBody ground = facing(0);
		DragonBody air = new DragonBody();
		for (int t = 0; t < 60; t++) air.tick(0, 0, 64, 0, DragonBody.Mode.HOVER);
		// the hover's chest is up ~38 degrees: the shoulders turn as far as they can against it
		assertEquals(-DragonBody.WING_FLEX, air.wingCounter(1, 38), 0.5);
		assertEquals(0.0, ground.wingCounter(1, 38), 1e-9);
		double[] still = solve(DragonAnim.HOVER, 0, ground), turned = solve(DragonAnim.HOVER, 0, air);
		assertArrayEquals(new double[]{still[0], still[1], still[2]}, new double[]{turned[0], turned[1], turned[2]}, 1e-6, "the head stays");
		double moved = 0;
		for (int part : new int[]{14, 15, 17, 18}) {
			double dx = turned[part * 3] - still[part * 3], dy = turned[part * 3 + 1] - still[part * 3 + 1], dz = turned[part * 3 + 2] - still[part * 3 + 2];
			moved = Math.max(moved, Math.sqrt(dx * dx + dy * dy + dz * dz));
		}
		assertTrue(moved > 0.3, "the wing membranes turn with the shoulders: " + moved);
	}
}
