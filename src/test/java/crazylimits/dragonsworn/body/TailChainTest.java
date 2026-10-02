package crazylimits.dragonsworn.body;

import crazylimits.dragonsworn.anim.DragonAnim;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Characterization tests: pin what {@link TailChain} does now. */
class TailChainTest {
	private static final int N = TailChain.SEGMENTS;
	private static final double EPS = 1e-9;
	private static final double[] I = {1, 0, 0, 0, 1, 0, 0, 0, 1};
	private static final double[] REST = PoseData.TAIL_REST, CAPSULE = PoseData.TAIL_CAPSULE;

	/** Hung at its rest root with the identity: the rest chain in editor space. */
	private static TailChain atRest() {
		return new TailChain().base(REST[0], REST[1], REST[2], I);
	}

	private static double[] zeros() {
		return new double[N];
	}

	private static double[][] randomBends(long seed) {
		Random random = new Random(seed);
		double[] x = new double[N], y = new double[N];
		for (int s = 0; s < N; s++) {
			x[s] = random.nextDouble() * 120 - 60;
			y[s] = random.nextDouble() * 120 - 60;
		}
		return new double[][]{x, y};
	}

	private static double[] pivot(TailChain chain, int s) {
		return new double[]{chain.pivot(s, 0), chain.pivot(s, 1), chain.pivot(s, 2)};
	}

	private static double dist(double[] a, int i, double[] b, int j) {
		double dx = a[i * 3] - b[j * 3], dy = a[i * 3 + 1] - b[j * 3 + 1], dz = a[i * 3 + 2] - b[j * 3 + 2];
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static void assertPoint(double x, double y, double z, double[] p, int o, double eps, String what) {
		assertEquals(x, p[o * 3], eps, what + " x");
		assertEquals(y, p[o * 3 + 1], eps, what + " y");
		assertEquals(z, p[o * 3 + 2], eps, what + " z");
	}

	@Test
	void theTailHasNineSegmentsRunningBackFromTheBody() {
		assertEquals(9, N);
		assertEquals(N * 3, REST.length);
		assertEquals(N * 7, CAPSULE.length);
		for (int s = 1; s < N; s++) assertTrue(REST[s * 3 + 2] > REST[s * 3 - 1], "segment " + s + " lies behind the one before (+z)");
		for (int s = 0; s < N; s++) {
			assertEquals(CAPSULE[s * 7 + 6], TailChain.radius(s), "radius of " + s);
			assertTrue(TailChain.radius(s) > 0, "radius of " + s);
			assertEquals(REST[s * 3 + 2], CAPSULE[s * 7 + 2], EPS, "capsule " + s + " starts at its pivot");
		}
	}

	@Test
	void zeroBendsGiveTheRestChain() {
		TailChain chain = atRest();
		chain.pose(zeros(), zeros());
		double[] cap = new double[6];
		for (int s = 0; s < N; s++) {
			for (int a = 0; a < 3; a++) assertEquals(REST[s * 3 + a], chain.pivot(s, a), EPS, "pivot " + s + " axis " + a);
			chain.capsule(s, cap);
			for (int k = 0; k < 6; k++) assertEquals(CAPSULE[s * 7 + k], cap[k], EPS, "capsule " + s + " value " + k);
		}
	}

	@Test
	void anUnposedChainHasItsPivotsAtTheOrigin() {
		TailChain chain = atRest();
		for (int s = 0; s < N; s++) for (int a = 0; a < 3; a++) assertEquals(0.0, chain.pivot(s, a), "pivot " + s + " before pose()");
	}

	@Test
	void bendingKeepsEverySegmentsLengthAndTheRootInPlace() {
		for (long seed = 1; seed <= 20; seed++) {
			double[][] b = randomBends(seed);
			TailChain chain = atRest();
			chain.pose(b[0], b[1]);
			for (int a = 0; a < 3; a++) assertEquals(REST[a], chain.pivot(0, a), EPS, "the root never moves");
			for (int s = 1; s < N; s++) {
				double[] p = pivot(chain, s), q = pivot(chain, s - 1);
				double bent = dist(p, 0, q, 0), rest = dist(REST, s, REST, s - 1);
				assertEquals(rest, bent, 1e-9, "seed " + seed + " segment " + s);
			}
			double[] cap = new double[6];
			for (int s = 0; s < N; s++) {
				chain.capsule(s, cap);
				double len = dist(cap, 0, cap, 1);
				double restLen = Math.sqrt(Math.pow(CAPSULE[s * 7 + 3] - CAPSULE[s * 7], 2) + Math.pow(CAPSULE[s * 7 + 4] - CAPSULE[s * 7 + 1], 2)
						+ Math.pow(CAPSULE[s * 7 + 5] - CAPSULE[s * 7 + 2], 2));
				assertEquals(restLen, len, 1e-9, "capsule " + s + " seed " + seed);
			}
		}
	}

	@Test
	void plusXLowersTheFarEndAndPlusYSwingsItRight() {
		TailChain chain = atRest();
		double[] x = zeros(), y = zeros();
		x[0] = 20;
		chain.pose(x, y);
		assertTrue(chain.pivot(N - 1, 1) < REST[(N - 1) * 3 + 1] - 1, "+X lowers the tip: " + chain.pivot(N - 1, 1));
		assertEquals(0.0, chain.pivot(N - 1, 0), EPS, "a pure X bend stays in the middle plane");
		x[0] = 0;
		y[0] = 20;
		chain.pose(x, y);
		assertTrue(chain.pivot(N - 1, 0) > 1, "+Y swings the tip to +x (right): " + chain.pivot(N - 1, 0));
	}

	@Test
	void aRightAngleOnTheRootSwingsTheNextPivotExactlySideways() {
		TailChain chain = atRest();
		double[] y = zeros();
		y[0] = 90;
		chain.pose(zeros(), y);
		double len = REST[5] - REST[2];
		assertEquals(REST[1 * 3 + 1] - REST[1], 0.0, EPS, "tail_1 to tail_2 is level at rest");
		assertEquals(len, chain.pivot(1, 0), 1e-12);
		assertEquals(REST[1], chain.pivot(1, 1), 1e-12);
		assertEquals(REST[2], chain.pivot(1, 2), 1e-12);
	}

	@Test
	void aBendOnOneSegmentTurnsEverythingAfterItRigidly() {
		int k = 3;
		double angle = 35;
		TailChain chain = atRest();
		double[] y = zeros();
		y[k] = angle;
		chain.pose(zeros(), y);
		double c = Math.cos(Math.toRadians(angle)), sn = Math.sin(Math.toRadians(angle));
		for (int s = 0; s < N; s++) {
			double dx = REST[s * 3] - REST[k * 3], dy = REST[s * 3 + 1] - REST[k * 3 + 1], dz = REST[s * 3 + 2] - REST[k * 3 + 2];
			double ex = s <= k ? REST[s * 3] : REST[k * 3] + c * dx + sn * dz;
			double ey = s <= k ? REST[s * 3 + 1] : REST[k * 3 + 1] + dy;
			double ez = s <= k ? REST[s * 3 + 2] : REST[k * 3 + 2] - sn * dx + c * dz;
			assertEquals(ex, chain.pivot(s, 0), 1e-9, "pivot " + s + " x");
			assertEquals(ey, chain.pivot(s, 1), 1e-9, "pivot " + s + " y");
			assertEquals(ez, chain.pivot(s, 2), 1e-9, "pivot " + s + " z");
		}
	}

	@Test
	void bendsAccumulateDownTheChain() {
		// the same Y bend on every segment: the tip has turned through N x the bend
		TailChain chain = atRest();
		double[] y = zeros();
		java.util.Arrays.fill(y, 10);
		chain.pose(zeros(), y);
		double[] cap = new double[6];
		chain.capsule(N - 1, cap);
		double heading = Math.toDegrees(Math.atan2(cap[3] - cap[0], cap[5] - cap[2]));
		assertEquals(10.0 * N, heading, 1e-6, "the last segment points 90 deg off the body");
	}

	@Test
	void theYBendTurnsBeforeTheXBend() {
		// Ry . Rx (X innermost): a 90 deg X with a 90 deg Y points the next pivot straight down (Rx . Ry would point it sideways)
		TailChain chain = atRest();
		double[] x = zeros(), y = zeros();
		x[0] = 90;
		y[0] = 90;
		chain.pose(x, y);
		double len = REST[5] - REST[2];
		assertEquals(REST[0], chain.pivot(1, 0), 1e-12);
		assertEquals(REST[1] - len, chain.pivot(1, 1), 1e-12);
		assertEquals(REST[2], chain.pivot(1, 2), 1e-12);
	}

	@Test
	void poseFromKeepsTheSegmentsBeforeItAndMatchesAFullPose() {
		double[][] a = randomBends(7), b = randomBends(8);
		TailChain chain = atRest();
		chain.pose(a[0], a[1]);
		double[][] before = new double[N][];
		for (int s = 0; s < N; s++) before[s] = pivot(chain, s);
		int first = 4;
		chain.poseFrom(first, b[0], b[1]);
		for (int s = 0; s <= first; s++) assertArrayEquals(before[s], pivot(chain, s), EPS, "pivot " + s + " is placed by the bends before it");
		double[] mx = a[0].clone(), my = a[1].clone();
		System.arraycopy(b[0], first, mx, first, N - first);
		System.arraycopy(b[1], first, my, first, N - first);
		TailChain full = atRest();
		full.pose(mx, my);
		double[] p = new double[3], q = new double[3];
		for (int s = 0; s < N; s++) {
			full.point(s, REST[s * 3], REST[s * 3 + 1], REST[s * 3 + 2] + 0.5, p, 0);
			chain.point(s, REST[s * 3], REST[s * 3 + 1], REST[s * 3 + 2] + 0.5, q, 0);
			assertArrayEquals(p, q, 1e-12, "segment " + s);
		}
	}

	@Test
	void aSegmentAloneDoesNotMoveTheOnesAfterIt() {
		// segment() places only its own pivot and rotation: the later ones keep their last pose until re-posed
		TailChain chain = atRest();
		chain.pose(zeros(), zeros());
		chain.segment(2, 0, 45);
		for (int s = 3; s < N; s++) assertArrayEquals(new double[]{REST[s * 3], REST[s * 3 + 1], REST[s * 3 + 2]}, pivot(chain, s), EPS, "pivot " + s);
		double[] tip = new double[3];
		chain.point(2, REST[3 * 3], REST[3 * 3 + 1], REST[3 * 3 + 2], tip, 0);
		assertTrue(tip[0] > 0.5, "segment 2 itself is swung: " + tip[0]);
	}

	@Test
	void aRotatedTranslatedBaseCarriesTheWholeChain() {
		double yaw = Math.toRadians(30), c = Math.cos(yaw), sn = Math.sin(yaw);
		double[] m = {c, 0, sn, 0, 1, 0, -sn, 0, c};
		double bx = 1.5, by = -2, bz = 0.25;
		double[][] b = randomBends(3);
		TailChain moved = new TailChain().base(bx, by, bz, m), rest = atRest();
		moved.pose(b[0], b[1]);
		rest.pose(b[0], b[1]);
		for (int s = 0; s < N; s++) {
			double dx = rest.pivot(s, 0) - REST[0], dy = rest.pivot(s, 1) - REST[1], dz = rest.pivot(s, 2) - REST[2];
			assertEquals(bx + c * dx + sn * dz, moved.pivot(s, 0), 1e-9, "pivot " + s + " x");
			assertEquals(by + dy, moved.pivot(s, 1), 1e-9, "pivot " + s + " y");
			assertEquals(bz - sn * dx + c * dz, moved.pivot(s, 2), 1e-9, "pivot " + s + " z");
		}
	}

	@Test
	void theDrawnBodyMatrixIsInPixelsAndHangsTheRootAtTheRestPivot() {
		double[] m = {1, 0, 0, 16, 0, 1, 0, -32, 0, 0, 1, 8, 0, 0, 0, 1};
		TailChain chain = new TailChain().body(m);
		chain.pose(zeros(), zeros());
		for (int s = 0; s < N; s++) {
			assertEquals(REST[s * 3] + 1, chain.pivot(s, 0), EPS, "pivot " + s + " x");
			assertEquals(REST[s * 3 + 1] - 2, chain.pivot(s, 1), EPS, "pivot " + s + " y");
			assertEquals(REST[s * 3 + 2] + 0.5, chain.pivot(s, 2), EPS, "pivot " + s + " z");
		}
	}

	@Test
	void aFrameWithLevelBodyAxesHangsTheRootAtTheFramesTailPoint() {
		double[] frame = new double[PoseTrack.POINTS * 3];
		int t = PoseTrack.TAIL_START * 3, a = PoseTrack.BODY_AXIS * 3;
		frame[t] = 0.25;
		frame[t + 1] = 3;
		frame[t + 2] = 2;
		frame[a + 3 + 2] = -1;   // a point one block ahead (-z)
		frame[a + 6 + 1] = 1;    // a point one block above
		TailChain chain = new TailChain().frame(frame);
		chain.pose(zeros(), zeros());
		for (int s = 0; s < N; s++) {
			assertEquals(0.25 + REST[s * 3] - REST[0], chain.pivot(s, 0), EPS, "pivot " + s + " x");
			assertEquals(3 + REST[s * 3 + 1] - REST[1], chain.pivot(s, 1), EPS, "pivot " + s + " y");
			assertEquals(2 + REST[s * 3 + 2] - REST[2], chain.pivot(s, 2), EPS, "pivot " + s + " z");
		}
	}

	@Test
	void unbentFramePointsStayWhereTheFrameHasThem() {
		double worst = 0;
		String where = "";
		for (DragonAnim anim : DragonAnim.values()) {
			for (double t = 0; t < 2.0; t += 0.25) {
				double[] frame = new double[PoseTrack.POINTS * 3];
				PoseTrack.sample(anim, t, frame);
				TailChain chain = new TailChain().frame(frame);
				chain.pose(zeros(), zeros());
				double[] out = new double[3];
				for (int s = 0; s < N; s++) {
					int i = PoseTrack.TAIL_START + s;
					// unbent, a frame point goes back to itself exactly (body . body^T)
					chain.framePoint(s, frame, i, out, 0);
					assertPoint(frame[i * 3], frame[i * 3 + 1], frame[i * 3 + 2], out, 0, 1e-9, anim + " " + t + " tail point " + s);
					// the chain's own pivots match the frame's straight tail up to the frame's quantization (1/256 block per point:
					// the body axes' error, ~0.004 rad, grows toward the tip; measured worst ~0.039 blocks, HOVER_BITE)
					double d = dist(pivot(chain, s), 0, frame, i);
					if (d > worst) {
						worst = d;
						where = anim + " at " + t + " s, pivot " + s;
					}
				}
			}
		}
		assertTrue(worst < 0.05, "worst gap " + worst + " (" + where + ")");
	}

	@Test
	void framePointMayWriteBackIntoTheFrame() {
		double[] frame = new double[PoseTrack.POINTS * 3];
		PoseTrack.sample(DragonAnim.IDLE, 0, frame);
		TailChain chain = new TailChain().frame(frame);
		double[] y = zeros();
		y[0] = 30;
		chain.pose(zeros(), y);
		int i = PoseTrack.TAIL_START + N - 1;
		double[] copy = new double[3];
		chain.framePoint(N - 1, frame, i, copy, 0);
		chain.framePoint(N - 1, frame, i, frame, i);
		assertPoint(copy[0], copy[1], copy[2], frame, i, 0, "in place");
	}

	@Test
	void theAxesAreAnOrthonormalRightHandedFrame() {
		double[] m = new double[9];
		// a tilted up and a back axis not quite perpendicular to it, both unnormalized
		TailChain.axes(0.2, 2.0, 0.1, 0.3, 0.5, 3.0, m);
		double[][] cols = {{m[0], m[3], m[6]}, {m[1], m[4], m[7]}, {m[2], m[5], m[8]}};
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				double d = cols[i][0] * cols[j][0] + cols[i][1] * cols[j][1] + cols[i][2] * cols[j][2];
				assertEquals(i == j ? 1.0 : 0.0, d, 1e-12, "column " + i + " . column " + j);
			}
		}
		double det = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6]);
		assertEquals(1.0, det, 1e-12);
		double l = Math.sqrt(0.04 + 4.0 + 0.01);
		assertArrayEquals(new double[]{0.2 / l, 2.0 / l, 0.1 / l}, cols[1], 1e-12, "the up axis is kept, only normalized");
		assertTrue(cols[2][2] > 0.9, "the back axis is only straightened against the up axis");
		double[] id = new double[9];
		TailChain.axes(0, 5, 0, 0, 0, 2, id);
		assertArrayEquals(I, id, 1e-12, "up +y, back +z: the identity");
	}
}
