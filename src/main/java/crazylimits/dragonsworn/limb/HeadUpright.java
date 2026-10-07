package crazylimits.dragonsworn.limb;

/**
 * On a wall the head keeps its crown up in the world, whatever points it: the wall poses twist the upper
 * neck so the crown is up as keyed, but a bite or a breath aimed into a tunnel (the head pointing back into
 * the face) bends that twisted neck until the head hangs upside down. The last step on the head: it is
 * turned about its own length (pivot to snout) until its crown is as near the world's up as it can be,
 * so the jaws stay exactly where the aim put them. Pointing nearly straight up or down the crown has no
 * up to turn to: the turn fades out there ({@link #FROM}..{@link #FULL}).
 *
 * <p>Editor space (pixels, degrees; a bone's rotation Rz Ry Rx), as {@link Joint} and {@link Affine}.
 */
public final class HeadUpright {
	/** The head's length at rest (head_group's pivot to the head's anchor: body/PartSolver) and its crown, square to it. */
	static final double[] LENGTH = unit(0.0, 2.0, -21.0), CROWN = unit(0.0, 21.0, 2.0);
	/** How far the head points off straight up or down (the sine) where the turn starts, and where it is whole. */
	static final double FROM = 0.25, FULL = 0.5;

	private HeadUpright() {
	}

	/**
	 * The head bone's rotation (degrees X, Y, Z, relative to its parent: {@code rot}) turned about its length
	 * so its crown is up, {@code weight} of the way. {@code parent}: the parent bone's matrix in the model
	 * ({@link Affine}); {@code up}: the world's up in the model.
	 */
	public static double[] turn(double[] parent, double[] rot, double[] up, double weight) {
		if (weight <= 0.0) return rot.clone();
		double[] l = Affine.rotationZYX(rot[0], rot[1], rot[2]);
		double[] h = Affine.mul(parent, l);
		double[] f = dir(h, LENGTH), c = dir(h, CROWN);
		double[] u = unit(up[0], up[1], up[2]);
		double along = u[0] * f[0] + u[1] * f[1] + u[2] * f[2];
		double[] side = {u[0] - along * f[0], u[1] - along * f[1], u[2] - along * f[2]};
		double off = Math.sqrt(side[0] * side[0] + side[1] * side[1] + side[2] * side[2]);
		double w = weight * smooth((off - FROM) / (FULL - FROM));
		if (w <= 0.0) return rot.clone();
		// the signed angle from the crown to the up (square to the length), about the length
		double cos = c[0] * side[0] + c[1] * side[1] + c[2] * side[2];
		double sin = f[0] * (c[1] * side[2] - c[2] * side[1]) + f[1] * (c[2] * side[0] - c[0] * side[2]) + f[2] * (c[0] * side[1] - c[1] * side[0]);
		double angle = Math.atan2(sin, cos) * w;
		double[] turned = Affine.mul(l, about(LENGTH, angle));
		return euler(turned);
	}

	/** Rotation by {@code angle} (radians) about unit axis {@code k}, as an {@link Affine}. */
	static double[] about(double[] k, double angle) {
		double c = Math.cos(angle), s = Math.sin(angle), t = 1.0 - c;
		double x = k[0], y = k[1], z = k[2];
		double[] m = Affine.identity();
		m[0] = t * x * x + c;
		m[1] = t * x * y - s * z;
		m[2] = t * x * z + s * y;
		m[4] = t * x * y + s * z;
		m[5] = t * y * y + c;
		m[6] = t * y * z - s * x;
		m[8] = t * x * z - s * y;
		m[9] = t * y * z + s * x;
		m[10] = t * z * z + c;
		return m;
	}

	/** The X, Y, Z (degrees) whose Rz Ry Rx is {@code m}'s rotation. */
	static double[] euler(double[] m) {
		double y = Math.asin(Math.max(-1.0, Math.min(1.0, -m[8])));
		double x, z;
		if (Math.abs(m[8]) < 0.999999) {
			x = Math.atan2(m[9], m[10]);
			z = Math.atan2(m[4], m[0]);
		} else {
			// straight up or down the X and Z turns are one: all of it on X
			x = Math.atan2(-m[6], m[5]);
			z = 0.0;
		}
		return new double[]{Math.toDegrees(x), Math.toDegrees(y), Math.toDegrees(z)};
	}

	private static double[] dir(double[] m, double[] v) {
		return new double[]{m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[4] * v[0] + m[5] * v[1] + m[6] * v[2],
				m[8] * v[0] + m[9] * v[1] + m[10] * v[2]};
	}

	private static double[] unit(double x, double y, double z) {
		double n = Math.sqrt(x * x + y * y + z * z);
		return new double[]{x / n, y / n, z / n};
	}

	private static double smooth(double x) {
		x = Math.max(0.0, Math.min(1.0, x));
		return x * x * (3.0 - 2.0 * x);
	}
}
