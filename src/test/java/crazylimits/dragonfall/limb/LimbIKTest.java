package crazylimits.dragonfall.limb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** On the real rig's left limbs (pivots from the geometry, editor space: file x negated). */
class LimbIKTest {
	private static final double[] ZERO = {0, 0, 0};

	private static Joint joint(double x, double y, double z, double rx, double ry, double rz) {
		return new Joint().set(new double[]{x, y, z}, ZERO, new double[]{rx, ry, rz});
	}

	/** The body bone, pitched up 30, turned a little and raised 10 px like the standing pose. */
	private static double[] body() {
		return new Joint().set(new double[]{0, 60, -8.5}, new double[]{0, 10, 0}, new double[]{30, 2, -1.5}).local();
	}

	private static Joint thigh() {
		return joint(-16, 41, 28.5, -20, 0, 0);
	}

	private static Joint shin() {
		return joint(-16, 25, 34, 40, 0, 0);
	}

	private static Joint foot() {
		return joint(-16, 3, 32, -50, 0, 0);
	}

	private static double[] ankle(double[] parent, Joint thigh, Joint shin, Joint foot) {
		double[] m = Affine.mul(Affine.mul(parent, thigh.local()), shin.local());
		return Affine.apply(m, foot.pivot, new double[3]);
	}

	@Test
	void theAnkleReachesItsTargetTurningOnlyAboutX() {
		double[] parent = body();
		Joint thigh = thigh(), shin = shin(), foot = foot();
		double[] start = ankle(parent, thigh, shin, foot);
		// the ground under the foot is 6 px higher: the ankle goes straight up by that
		double[] target = {start[0], start[1] + 6, start[2]};
		target = Affine.apply(parent, inPlane(parent, target, start), new double[3]);
		double left = LimbIK.solveLeg(parent, thigh, shin, foot, target);
		assertTrue(left < 1e-6, "reached: " + left);
		assertArrayEquals(target, ankle(parent, thigh, shin, foot), 1e-6);
		assertEquals(0.0, thigh.rot[1], 0.0);
		assertEquals(0.0, shin.rot[2], 0.0);
		// the foot turned back by what the thigh and shin turned: its angle in the body is unchanged
		assertEquals(-20 + 40 - 50, thigh.rot[0] + shin.rot[0] + foot.rot[0], 1e-9);
	}

	/** {@code target} moved into the leg's plane (the leg turns only about X: it cannot reach sideways). */
	private static double[] inPlane(double[] parent, double[] target, double[] start) {
		double[] inv = Affine.invertRigid(parent);
		double[] t = Affine.apply(inv, target, new double[3]), s = Affine.apply(inv, start, new double[3]);
		t[0] = s[0];
		return t;
	}

	@Test
	void theAnimatedPoseIsAFixedPoint() {
		double[] parent = body();
		Joint thigh = thigh(), shin = shin(), foot = foot();
		LimbIK.solveLeg(parent, thigh, shin, foot, ankle(parent, thigh, shin, foot));
		assertEquals(-20, thigh.rot[0], 1e-6);
		assertEquals(40, shin.rot[0], 1e-6);
		assertEquals(-50, foot.rot[0], 1e-6);
	}

	@Test
	void theKneeKeepsItsBendAndMovesSmoothly() {
		double[] parent = body();
		double[] start = ankle(parent, thigh(), shin(), foot());
		double lastThigh = Double.NaN, lastShin = Double.NaN;
		for (double dy = -8; dy <= 8; dy += 0.25) {
			Joint thigh = thigh(), shin = shin(), foot = foot();
			double[] target = Affine.apply(parent, inPlane(parent, new double[]{start[0], start[1] + dy, start[2]}, start), new double[3]);
			LimbIK.solveLeg(parent, thigh, shin, foot, target);
			assertTrue(knee(shin.rot[0]) * knee(40) > 0, "the knee never flips through: " + knee(shin.rot[0]));
			if (!Double.isNaN(lastThigh)) {
				assertTrue(Math.abs(thigh.rot[0] - lastThigh) < 3 && Math.abs(shin.rot[0] - lastShin) < 3, "no jump at dy " + dy);
			}
			lastThigh = thigh.rot[0];
			lastShin = shin.rot[0];
		}
	}

	/** The knee's angle (thigh to shin, in the leg's plane) for a shin rotation, radians, signed by its bend. */
	private static double knee(double shinDegrees) {
		// thigh: hip (41, 28.5) to knee (25, 34); shin: knee to ankle (3, 32); angles of (y, z)
		double rest = Math.atan2(32 - 34, 3 - 25) - Math.atan2(34 - 28.5, 25 - 41);
		return Math.IEEEremainder(rest + Math.toRadians(shinDegrees), 2 * Math.PI);
	}

	@Test
	void nearFullStretchTheLegStraightensSmoothly() {
		double full = 39.0;
		double last = 0;
		for (double d = 0; d < 60; d += 0.1) {
			double r = LimbIK.soften(d, full);
			assertTrue(r < full, "never quite straight");
			assertTrue(r >= last, "monotone");
			assertTrue(r - last <= 0.1 + 1e-9, "never faster than the target");
			last = r;
		}
		assertEquals(20.0, LimbIK.soften(20.0, full), 0.0, "well within reach: untouched");
	}

	@Test
	void anOutOfReachTargetStraightensTheLegTowardIt() {
		double[] parent = body();
		Joint thigh = thigh(), shin = shin(), foot = foot();
		double[] start = ankle(parent, thigh, shin, foot);
		double[] target = Affine.apply(parent, inPlane(parent, new double[]{start[0], start[1] - 200, start[2]}, start), new double[3]);
		double left = LimbIK.solveLeg(parent, thigh, shin, foot, target);
		assertTrue(ankle(parent, thigh, shin, foot)[1] < start[1], "it reached down");
		assertTrue(left > 100, "but could not get there");
		assertTrue(Double.isFinite(thigh.rot[0]) && Double.isFinite(shin.rot[0]));
	}

	@Test
	void theWristComesToTheGroundTurningOnlyTheShoulderAboutZ() {
		double[] parent = body();
		Joint shoulder = joint(-12, 65, -22, -40, -10, -30);
		double[] contact = {-110, 40, -40};
		double startY = Affine.apply(Affine.mul(parent, shoulder.local()), contact, new double[3])[1];
		for (double dy : new double[]{-10, -3, 4, 12}) {
			Joint s = joint(-12, 65, -22, -40, -10, -30);
			double left = LimbIK.solveWing(parent, s, contact, startY + dy);
			assertTrue(left < 1e-3, "reached " + dy + ": " + left);
			assertEquals(-40, s.rot[0], 0.0);
			assertEquals(-10, s.rot[1], 0.0);
			assertTrue(Math.abs(s.rot[2] + 30) <= LimbIK.MAX_SHOULDER);
			assertEquals(startY + dy, Affine.apply(Affine.mul(parent, s.local()), contact, new double[3])[1], 1e-3);
		}
	}

	@Test
	void theShoulderTurnsOnlySoFar() {
		double[] parent = body();
		Joint s = joint(-12, 65, -22, -40, -10, -30);
		double[] contact = {-110, 40, -40};
		double left = LimbIK.solveWing(parent, s, contact, -500);
		assertTrue(left > 100);
		assertEquals(LimbIK.MAX_SHOULDER, Math.abs(s.rot[2] + 30), 1e-9);
	}

	@Test
	void jointMatchesTheRigConvention() {
		// tools/rig.py: T(p + pivot) . Rz Ry Rx . T(-pivot); a bone turned 90 about X swings its -Z front up
		Joint j = joint(0, 10, 0, 90, 0, 0);
		double[] p = Affine.apply(j.local(), new double[]{0, 10, -5}, new double[3]);
		assertArrayEquals(new double[]{0, 15, 0}, p, 1e-9);
		double[] back = Affine.apply(Affine.invertRigid(j.local()), p, new double[3]);
		assertArrayEquals(new double[]{0, 10, -5}, back, 1e-9);
	}
}
