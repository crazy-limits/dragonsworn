package crazylimits.dragonsworn.body;

import crazylimits.dragonsworn.math.Angles;
import crazylimits.dragonsworn.math.Mat3;
import crazylimits.dragonsworn.math.Vectors;

/**
 * The neck's exact kinematics over a {@link PoseTrack} frame (the four neck bones, then head_group), as
 * GeckoLib draws them: bending it by procedural angles ({@link #bend}) and solving the bend that points a
 * segment somewhere ({@link #aim}). The tail's twin is {@link TailChain}.
 */
public final class NeckChain {
	private NeckChain() {
	}

	/**
	 * The neck's bones at rest (blocks, editor space, x = 0): the pivots of neck_1..neck_4 and head_group,
	 * then the head part's anchor. No neck bone has a rest rotation or a keyed position; their keyed roll is
	 * in the frame ({@link PoseTrack#neckRoll}: the wall poses twist the upper neck), so each bone's keyed pitch
	 * and yaw follow from where the frame puts its pivot and the next one, under that roll.
	 */
	private static final double[][] NECK_REST = {{0, 53.125 / 16, -29 / 16.0}, {0, 53.125 / 16, -44 / 16.0},
			{0, 55.125 / 16, -58.5 / 16}, {0, 55.125 / 16, -72 / 16.0}, PartSolver.HEAD_PIVOT, PartSolver.HEAD_ANCHOR};

	/**
	 * The neck bent exactly as GeckoLib draws it: bone {@code i} (neck_1..neck_4, then head_group) is turned
	 * by Rz Ry Rx of its keyed angles (Z the frame's roll), and the renderer adds {@code bendY[i]} to its Y and {@code bendX[i]}
	 * to its X, so the yaw turns about the bone's axis as tilted by its parents (and the yaw part of its
	 * own key), the pitch about its own X. The keyed angles are recovered from the frame (pivot to next
	 * pivot, through the body's frame), so with no bends the frame is left exactly as it was.
	 */
	static void bend(double[] points, double[] bendX, double[] bendY) {
		int joints = Math.min(PoseTrack.NECK_PIVOTS, bendX.length);
		boolean any = false;
		for (int i = 0; i < joints; i++) any |= bendX[i] != 0.0 || bendY[i] != 0.0;
		if (!any) return;
		Neck neck = new Neck(points, bendX, bendY, PoseTrack.NECK_PIVOTS);
		for (int p = 0; p < PoseTrack.PARTS; p++) {
			if (PoseTrack.partChain(p) != PoseTrack.CHAIN_NECK) continue;
			int bone = PoseTrack.partDepth(p) - 1;
			carry(points, p, neck.pivot[bone], neck.moved[bone], neck.turn[bone]);
		}
		for (int k = 1; k < PoseTrack.NECK_PIVOTS; k++) {
			int at = (PoseTrack.NECK_START + k) * 3;
			points[at] = neck.moved[k][0];
			points[at + 1] = neck.moved[k][1];
			points[at + 2] = neck.moved[k][2];
		}
	}

	/**
	 * The bends of neck joint {@code joint} (degrees: X, Y, on top of its keyed angles) that point its
	 * segment (its pivot to the next one, the head's to its anchor) along {@code direction} (model space),
	 * with the bends of the joints before it as given ({@code bendX/Y}, later ones ignored). {@code points}
	 * is the unbent frame.
	 */
	public static double[] aim(double[] points, double[] bendX, double[] bendY, int joint, double[] direction) {
		Neck neck = new Neck(points, bendX, bendY, joint);
		double[] rest = NECK_REST[joint], next = NECK_REST[joint + 1];
		double dy = next[1] - rest[1], dz = next[2] - rest[2];
		double[] v = Mat3.mulT(Mat3.z(neck.roll(joint)), Mat3.mulT(neck.bent, direction));
		double scale = Math.hypot(dy, dz) / Math.max(1e-9, Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]));
		double[] want = keyedAngles(dy, dz, v[0] * scale, v[1] * scale, v[2] * scale);
		double[] keyed = neck.keyedAngles(joint);
		return new double[]{Math.toDegrees(Angles.wrapRadians(want[0] - keyed[0])), Math.toDegrees(Angles.wrapRadians(want[1] - keyed[1]))};
	}

	/** The neck's forward kinematics over a frame, joint by joint (see {@link #bend}). */
	private static final class Neck {
		/** Each pivot (then the head's anchor) as the frame has it, and as bent. */
		final double[][] pivot = new double[PoseTrack.NECK_PIVOTS + 1][], moved = new double[PoseTrack.NECK_PIVOTS + 1][];
		/** Per joint: the turn from the frame's bone to the bent one. */
		final double[][] turn = new double[PoseTrack.NECK_PIVOTS][];
		private final double[] frame;
		/** The rotations reached so far: as keyed, and bent (after {@code joints} joints). */
		double[] keyed, bent;

		/** Runs the first {@code joints} joints (all five: the whole neck). */
		Neck(double[] points, double[] bendX, double[] bendY, int joints) {
			this.frame = points;
			int n = PoseTrack.NECK_PIVOTS;
			// the body's rotation (the neck's parent): its frame points, one block ahead (-z) and above (+y)
			int b = PoseTrack.BODY_AXIS * 3;
			double[] ey = {points[b + 6] - points[b], points[b + 7] - points[b + 1], points[b + 8] - points[b + 2]};
			double[] ez = {points[b] - points[b + 3], points[b + 1] - points[b + 4], points[b + 2] - points[b + 5]};
			Vectors.normalize(ey);
			double[] ex = Vectors.cross(ey, ez);
			Vectors.normalize(ex);
			ez = Vectors.cross(ex, ey);
			keyed = new double[]{ex[0], ey[0], ez[0], ex[1], ey[1], ez[1], ex[2], ey[2], ez[2]};
			bent = keyed.clone();
			for (int k = 0; k <= n; k++) {
				int at = k < n ? (PoseTrack.NECK_START + k) * 3 : 0;
				pivot[k] = new double[]{points[at], points[at + 1], points[at + 2]};
			}
			moved[0] = pivot[0].clone();
			for (int k = 0; k < joints; k++) {
				double[] angles = keyedAngles(k);
				double bx = k < bendX.length ? bendX[k] : 0.0, by = k < bendY.length ? bendY[k] : 0.0, roll = roll(k);
				keyed = Mat3.mul(keyed, Mat3.zyx(roll, angles[1], angles[0]));
				bent = Mat3.mul(bent, Mat3.zyx(roll, angles[1] + Math.toRadians(by), angles[0] + Math.toRadians(bx)));
				// what this bone carries: from where the frame has it to where the bend puts it
				turn[k] = Mat3.mul(bent, Mat3.transpose(keyed));
				double[] d = Mat3.mulV(turn[k], segment(k));
				moved[k + 1] = new double[]{moved[k][0] + d[0], moved[k][1] + d[1], moved[k][2] + d[2]};
			}
		}

		/** Joint {@code k}'s keyed roll (radians), from the frame. */
		double roll(int k) {
			return Math.toRadians(PoseTrack.neckRoll(frame, k));
		}

		/** Joint {@code k}'s keyed angles (radians: X, Y), in its parent's keyed frame (joints before it run), under its roll. */
		double[] keyedAngles(int k) {
			double[] rest = NECK_REST[k], next = NECK_REST[k + 1];
			double dy = next[1] - rest[1], dz = next[2] - rest[2];
			double[] v = Mat3.mulT(Mat3.z(roll(k)), Mat3.mulT(keyed, segment(k)));
			double scale = Math.hypot(dy, dz) / Math.max(1e-9, Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]));
			return NeckChain.keyedAngles(dy, dz, v[0] * scale, v[1] * scale, v[2] * scale);
		}

		private double[] segment(int k) {
			return new double[]{pivot[k + 1][0] - pivot[k][0], pivot[k + 1][1] - pivot[k][1], pivot[k + 1][2] - pivot[k][2]};
		}
	}

	/**
	 * The keyed X and Y (radians) that turn a rest segment (0, dy, dz) onto {@code (vx, vy, vz)} (same
	 * length) by Ry Rx: of the two, the one turned least.
	 */
	static double[] keyedAngles(double dy, double dz, double vx, double vy, double vz) {
		double r = Math.hypot(dy, dz), phi = Math.atan2(dz, dy);
		double a = Math.acos(Math.max(-1.0, Math.min(1.0, vy / r)));
		double[] best = null;
		for (double alpha : new double[]{a, -a}) {
			double kx = Angles.wrapRadians(alpha - phi), z1 = r * Math.sin(alpha);
			double ky = z1 >= 0 ? Math.atan2(vx, vz) : Math.atan2(-vx, -vz);
			if (best == null || Math.abs(kx) + Math.abs(ky) < Math.abs(best[0]) + Math.abs(best[1])) best = new double[]{kx, ky};
		}
		return best;
	}

	private static void carry(double[] points, int i, double[] from, double[] to, double[] turn) {
		double[] d = Mat3.mulV(turn, new double[]{points[i * 3] - from[0], points[i * 3 + 1] - from[1], points[i * 3 + 2] - from[2]});
		points[i * 3] = to[0] + d[0];
		points[i * 3 + 1] = to[1] + d[1];
		points[i * 3 + 2] = to[2] + d[2];
	}
}
