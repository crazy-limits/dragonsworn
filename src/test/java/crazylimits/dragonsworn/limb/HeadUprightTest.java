package crazylimits.dragonsworn.limb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The head turned about its own length until its crown is up: the jaws stay on their aim. */
class HeadUprightTest {
	private static final double[] UP = {0.0, 1.0, 0.0};

	private static double[] dir(double[] parent, double[] rot, double[] v) {
		double[] m = Affine.mul(parent, Affine.rotationZYX(rot[0], rot[1], rot[2]));
		return new double[]{m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[4] * v[0] + m[5] * v[1] + m[6] * v[2],
				m[8] * v[0] + m[9] * v[1] + m[10] * v[2]};
	}

	@Test
	void anUpsideDownHeadIsTurnedCrownUpPointingWhereItDid() {
		// the parent pitched and yawed, the head rolled over and turned
		double[] parent = Affine.rotationZYX(30.0, 40.0, 0.0);
		double[] rot = {-20.0, 25.0, 170.0};
		double[] before = dir(parent, rot, HeadUpright.LENGTH);
		assertTrue(dir(parent, rot, HeadUpright.CROWN)[1] < -0.5, "upside down to begin with");
		double[] turned = HeadUpright.turn(parent, rot, UP, 1.0);
		assertArrayEquals(before, dir(parent, turned, HeadUpright.LENGTH), 1e-9, "the jaws stay on the aim");
		double[] crown = dir(parent, turned, HeadUpright.CROWN);
		double[] f = before;
		double along = f[1];
		double best = Math.sqrt(1.0 - along * along);
		assertEquals(best, crown[1], 1e-9, "the crown as near up as it can be");
	}

	@Test
	void anUprightHeadOrOneOffTheWallIsLeftAlone() {
		double[] parent = Affine.identity();
		double[] rot = {10.0, -30.0, 4.0};
		double[] turned = HeadUpright.turn(parent, rot, UP, 0.0);
		assertArrayEquals(rot, turned, 1e-12);
		// pointing straight up: no up to turn to
		double[] high = {90.0 - Math.toDegrees(Math.atan2(2.0, 21.0)), 120.0, 0.0};
		assertArrayEquals(high, HeadUpright.turn(parent, high, UP, 1.0), 1e-9);
	}
}
