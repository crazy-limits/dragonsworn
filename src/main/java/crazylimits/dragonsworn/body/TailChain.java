package crazylimits.dragonsworn.body;

/**
 * The tail's nine segments as GeckoLib draws them: hung from the body bone, each turned about its own
 * pivot by its bend (Y then X, X innermost: a bone's Z, Y, X order with no Z), in the frame of the one
 * before it. Exact forward kinematics, so the hitboxes, the strike's IK and the block collisions see the
 * tail where the renderer puts it.
 *
 * <p>Model space in blocks (editor space / 16: y up, the head toward -z, +x the dragon's right). Bends
 * are degrees, the numbers added to the bones: +X lowers a segment's far end, +Y swings it right.
 * The tail is keyed straight in every animation; all its motion is these bends.
 */
public final class TailChain {
	public static final int SEGMENTS = PoseData.TAIL_PIVOTS;
	private static final double[] REST = PoseData.TAIL_REST, CAPSULE = PoseData.TAIL_CAPSULE;

	/** tail_1's pivot and the body bone's rotation (row-major 3x3), from the frame. */
	private final double[] base = new double[3], body = new double[9];
	/** Per segment, bent: its pivot and its rotation (body x every bend up to it). */
	private final double[] pivot = new double[SEGMENTS * 3], rot = new double[SEGMENTS * 9];
	private final double[] tmp = new double[3];

	/** Hangs the chain from the body of a {@link PoseTrack} frame. */
	public TailChain frame(double[] frame) {
		int t = PoseTrack.TAIL_START * 3, a = PoseTrack.BODY_AXIS * 3;
		double[] m = new double[9];
		// body axes: z points back (away from the point ahead), y up
		double zx = frame[a] - frame[a + 3], zy = frame[a + 1] - frame[a + 4], zz = frame[a + 2] - frame[a + 5];
		double yx = frame[a + 6] - frame[a], yy = frame[a + 7] - frame[a + 1], yz = frame[a + 8] - frame[a + 2];
		axes(yx, yy, yz, zx, zy, zz, m);
		return base(frame[t], frame[t + 1], frame[t + 2], m);
	}

	/** Hangs the chain from tail_1's pivot (model, blocks) with the body bone's rotation {@code m} (row-major 3x3). */
	public TailChain base(double x, double y, double z, double[] m) {
		base[0] = x;
		base[1] = y;
		base[2] = z;
		System.arraycopy(m, 0, body, 0, 9);
		return this;
	}

	/**
	 * Hangs the chain from the body bone as drawn: its matrix in the model, row-major 4x4 in pixels
	 * (as {@code limb.Affine}), through all its parents.
	 */
	public TailChain body(double[] m) {
		double qx = REST[0] * 16.0, qy = REST[1] * 16.0, qz = REST[2] * 16.0;
		double[] r = {m[0], m[1], m[2], m[4], m[5], m[6], m[8], m[9], m[10]};
		return base((m[0] * qx + m[1] * qy + m[2] * qz + m[3]) / 16.0, (m[4] * qx + m[5] * qy + m[6] * qz + m[7]) / 16.0,
				(m[8] * qx + m[9] * qy + m[10] * qz + m[11]) / 16.0, r);
	}

	/** Orthonormal rotation from an up (y) and a back (z) axis, into {@code m}. */
	static void axes(double yx, double yy, double yz, double zx, double zy, double zz, double[] m) {
		double l = Math.sqrt(yx * yx + yy * yy + yz * yz);
		yx /= l;
		yy /= l;
		yz /= l;
		double d = zx * yx + zy * yy + zz * yz;
		zx -= d * yx;
		zy -= d * yy;
		zz -= d * yz;
		l = Math.sqrt(zx * zx + zy * zy + zz * zz);
		zx /= l;
		zy /= l;
		zz /= l;
		double xx = yy * zz - yz * zy, xy = yz * zx - yx * zz, xz = yx * zy - yy * zx;
		m[0] = xx; m[1] = yx; m[2] = zx;
		m[3] = xy; m[4] = yy; m[5] = zy;
		m[6] = xz; m[7] = yz; m[8] = zz;
	}

	/** Bends every segment. */
	public void pose(double[] bendX, double[] bendY) {
		poseFrom(0, bendX, bendY);
	}

	/** Bends segments {@code first} onward (the ones before keep their last pose). */
	public void poseFrom(int first, double[] bendX, double[] bendY) {
		for (int s = first; s < SEGMENTS; s++) segment(s, bendX[s], bendY[s]);
	}

	/** Bends segment {@code s} alone, on the segments before it as last posed. */
	public void segment(int s, double bendX, double bendY) {
		double[] parent = body;
		int po = 0;
		if (s == 0) {
			pivot[0] = base[0];
			pivot[1] = base[1];
			pivot[2] = base[2];
		} else {
			parent = rot;
			po = (s - 1) * 9;
			double dx = REST[s * 3] - REST[s * 3 - 3], dy = REST[s * 3 + 1] - REST[s * 3 - 2], dz = REST[s * 3 + 2] - REST[s * 3 - 1];
			for (int r = 0; r < 3; r++) {
				pivot[s * 3 + r] = pivot[s * 3 - 3 + r] + rot[po + r * 3] * dx + rot[po + r * 3 + 1] * dy + rot[po + r * 3 + 2] * dz;
			}
		}
		// parent . Ry . Rx
		double ax = Math.toRadians(bendX), ay = Math.toRadians(bendY);
		double cx = Math.cos(ax), sx = Math.sin(ax), cy = Math.cos(ay), sy = Math.sin(ay);
		// Ry . Rx = [[cy, sy sx, sy cx], [0, cx, -sx], [-sy, cy sx, cy cx]]
		double l00 = cy, l01 = sy * sx, l02 = sy * cx, l11 = cx, l12 = -sx, l20 = -sy, l21 = cy * sx, l22 = cy * cx;
		int o = s * 9;
		for (int r = 0; r < 3; r++) {
			double p0 = parent[po + r * 3], p1 = parent[po + r * 3 + 1], p2 = parent[po + r * 3 + 2];
			rot[o + r * 3] = p0 * l00 + p2 * l20;
			rot[o + r * 3 + 1] = p0 * l01 + p1 * l11 + p2 * l21;
			rot[o + r * 3 + 2] = p0 * l02 + p1 * l12 + p2 * l22;
		}
	}

	/** A rest point (editor space, blocks) of segment {@code s}, bent, into {@code out} at point {@code o}. */
	public void point(int s, double qx, double qy, double qz, double[] out, int o) {
		double dx = qx - REST[s * 3], dy = qy - REST[s * 3 + 1], dz = qz - REST[s * 3 + 2];
		int m = s * 9;
		for (int r = 0; r < 3; r++) {
			out[o * 3 + r] = pivot[s * 3 + r] + rot[m + r * 3] * dx + rot[m + r * 3 + 1] * dy + rot[m + r * 3 + 2] * dz;
		}
	}

	/**
	 * Point {@code i} of a {@link PoseTrack} frame (where the straight tail has it), moved with segment
	 * {@code s} onto the bent chain, into {@code out} at point {@code o} ({@code out} may be the frame).
	 */
	public void framePoint(int s, double[] frame, int i, double[] out, int o) {
		// back into the body's rest frame: body^T (f - straight pivot)
		double fx = frame[i * 3] - base[0], fy = frame[i * 3 + 1] - base[1], fz = frame[i * 3 + 2] - base[2];
		for (int r = 0; r < 3; r++) tmp[r] = body[r] * fx + body[3 + r] * fy + body[6 + r] * fz;
		point(s, REST[0] + tmp[0], REST[1] + tmp[1], REST[2] + tmp[2], out, o);
	}

	/** Segment {@code s}'s capsule, bent: axis start and end into {@code out} (points 0 and 1). */
	public void capsule(int s, double[] out) {
		int c = s * 7;
		point(s, CAPSULE[c], CAPSULE[c + 1], CAPSULE[c + 2], out, 0);
		point(s, CAPSULE[c + 3], CAPSULE[c + 4], CAPSULE[c + 5], out, 1);
	}

	public static double radius(int s) {
		return CAPSULE[s * 7 + 6];
	}

	/** Segment {@code s}'s pivot, bent. */
	public double pivot(int s, int axis) {
		return pivot[s * 3 + axis];
	}
}
