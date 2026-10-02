package crazylimits.dragonsworn.body;

import crazylimits.dragonsworn.anim.DragonAnim;
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

	@Test
	void theLookIsMeasuredFromTheEyesAlongTheHead() {
		PartSolver solver = new PartSolver();
		DragonBody body = facing(0);
		double[] out = new double[PoseTrack.PARTS * 3], angles = new double[2];
		assertFalse(solver.lookAngles(0, 0, -20, angles), "nothing posed yet");
		solver.solve(DragonAnim.GLIDE, 0, body, null, 1, out);
		// far along the line from the head's pivot through its anchor: about straight ahead
		double[] head = {out[0], out[1], out[2]};
		double[] at = new double[3];
		PartSolver.toModel(body, 1, head[0], head[1], head[2] - 40, at);
		assertTrue(solver.lookAngles(at[0], at[1], at[2], angles));
		assertEquals(0.0, angles[0], 5.0);   // the glide sways the head a few degrees
		assertEquals(0.0, angles[1], 8.0);
		// to its left (-x, facing north): yaw positive; above: pitch positive
		assertTrue(solver.lookAngles(at[0] - 20, at[1], at[2], angles) && angles[0] > 20, "left: " + angles[0]);
		assertTrue(solver.lookAngles(at[0], at[1] + 30, at[2], angles) && angles[1] > 20, "up: " + angles[1]);
	}

	@Test
	void theHeadAndNeckHitboxesTurnWithTheLook() {
		PartSolver solver = new PartSolver();
		DragonBody body = facing(0);
		double[] still = new double[PoseTrack.PARTS * 3], turned = new double[PoseTrack.PARTS * 3];
		solver.solve(DragonAnim.IDLE, 0, body, null, 1, still);
		for (int i = 0; i < PoseTrack.NECK_PIVOTS; i++) solver.lookY[i] = 12;
		solver.solve(DragonAnim.IDLE, 0, body, null, 1, turned);
		assertTrue(turned[0] < still[0] - 1.0, "the head swings left: " + still[0] + " -> " + turned[0]);
		assertTrue(turned[8 * 3] < still[8 * 3], "the neck with it");
		assertEquals(still[2 * 3], turned[2 * 3], 1e-9, "the body stays");
	}

	@Test
	void aBlendStartsFromTheLastAnimationsPose() {
		DragonBody body = facing(0);
		double[] from = solve(DragonAnim.GLIDE, 0.5, body), to = solve(DragonAnim.IDLE, 0, body);
		double[] half = new double[PoseTrack.PARTS * 3];
		new PartSolver().solve(DragonAnim.IDLE, 0, new PartSolver.Blend(DragonAnim.GLIDE, 0.5, 0.0, 1), body, null, 1, null, null, half);
		assertEquals(from[1], half[1], 1e-6, "blend 0: still the glide");
		new PartSolver().solve(DragonAnim.IDLE, 0, new PartSolver.Blend(DragonAnim.GLIDE, 0.5, 0.5, 1), body, null, 1, null, null, half);
		assertEquals((from[1] + to[1]) / 2, half[1], 1e-6, "halfway");
	}

	/**
	 * The neck bent as GeckoLib draws it: the head's anchor (and neck_4's) with angles added to the five
	 * neck bones' rotations, against tools/rig.py's forward kinematics of the same pose (blocks).
	 */
	@Test
	void theNeckBendsAsTheModelIsDrawn() {
		double[] bx = {10, -5, 8, 0, 12}, by = {20, 15, -10, 25, 30};
		Object[][] cases = {
				// animation, seconds, head unbent, head bent, neck_4's anchor bent (rig.py)
				{DragonAnim.FLY, 0.0, 0.0000, 4.2513, -6.6720, -2.9952, 5.1329, -4.8745, -1.4814, 4.6681, -4.5039},
				{DragonAnim.GLIDE, 0.0, -0.0441, 3.9286, -6.5656, -2.9954, 4.8457, -4.7089, -1.4648, 4.3853, -4.3786},
				{DragonAnim.ATTACK, 0.5, 0.0000, 6.7096, -5.4350, -2.8196, 6.8267, -3.6233, -1.2784, 6.7177, -3.1107},
				{DragonAnim.ROAR, 1.0, -0.0331, 9.7760, -1.9997, -2.1380, 8.4428, -0.4091, -1.0363, 7.8920, -1.4302},
		};
		double[] frame = new double[PoseTrack.POINTS * 3];
		for (Object[] c : cases) {
			PoseTrack.sample((DragonAnim) c[0], (Double) c[1], frame);
			for (int a = 0; a < 3; a++) assertEquals((Double) c[2 + a], frame[a], 0.01, c[0] + " unbent head");
			PartSolver.bendChain(frame, PoseTrack.CHAIN_NECK, bx, by);
			for (int a = 0; a < 3; a++) {
				assertEquals((Double) c[5 + a], frame[a], 0.02, c[0] + " bent head, axis " + a);
				assertEquals((Double) c[8 + a], frame[3 + a], 0.02, c[0] + " bent neck_4, axis " + a);
			}
		}
	}

	@Test
	void theKeyedAnglesComeBack() {
		double dy = 0.3, dz = -1.2;
		for (double kx : new double[]{-1.0, -0.3, 0.0, 0.4, 1.1}) {
			for (double ky : new double[]{-0.8, 0.0, 0.2, 0.9}) {
				// Ry(ky) Rx(kx) (0, dy, dz)
				double y1 = dy * Math.cos(kx) - dz * Math.sin(kx), z1 = dy * Math.sin(kx) + dz * Math.cos(kx);
				double[] got = NeckChain.keyedAngles(dy, dz, z1 * Math.sin(ky), y1, z1 * Math.cos(ky));
				assertEquals(kx, got[0], 1e-9);
				assertEquals(ky, got[1], 1e-9);
			}
		}
	}

	@Test
	void aNewBlendStartsFromThePoseShownThen() {
		DragonBody body = facing(0);
		PartSolver solver = new PartSolver();
		double[] half = new double[PoseTrack.PARTS * 3], next = new double[PoseTrack.PARTS * 3];
		solver.solve(DragonAnim.GLIDE, 0.5, body, null, 1, half);
		solver.solve(DragonAnim.IDLE, 0, new PartSolver.Blend(DragonAnim.GLIDE, 0.5, 0.5, 1), body, null, 1, null, null, half);
		// half way into the idle the choice changes again: the walk blends in from that half-blended pose
		solver.solve(DragonAnim.WALK, 0, new PartSolver.Blend(DragonAnim.IDLE, 0, 0.0, 2), body, null, 1, null, null, next);
		assertArrayEquals(new double[]{half[0], half[1], half[2]}, new double[]{next[0], next[1], next[2]}, 1e-6);
	}
}
