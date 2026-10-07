package crazylimits.dragonsworn.math;

/** 3x3 rotation matrices, row-major in a {@code double[9]}, applied to column vectors ({@code double[3]}). */
public final class Mat3 {
	private Mat3() {
	}

	/** Ry(y) Rx(x) (radians), as {@code tools/rig.py}'s euler_zyx with no Z. */
	public static double[] yx(double y, double x) {
		double cy = Math.cos(y), sy = Math.sin(y), cx = Math.cos(x), sx = Math.sin(x);
		return new double[]{cy, sy * sx, sy * cx, 0, cx, -sx, -sy, cy * sx, cy * cx};
	}

	/** Rz(z) Ry(y) Rx(x) (radians), as {@code tools/rig.py}'s euler_zyx. */
	public static double[] zyx(double z, double y, double x) {
		double cz = Math.cos(z), sz = Math.sin(z);
		return mul(new double[]{cz, -sz, 0, sz, cz, 0, 0, 0, 1}, yx(y, x));
	}

	/** Rz(z) (radians). */
	public static double[] z(double z) {
		double c = Math.cos(z), s = Math.sin(z);
		return new double[]{c, -s, 0, s, c, 0, 0, 0, 1};
	}

	/** {@code a b}. */
	public static double[] mul(double[] a, double[] b) {
		double[] m = new double[9];
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) m[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
		}
		return m;
	}

	public static double[] transpose(double[] a) {
		return new double[]{a[0], a[3], a[6], a[1], a[4], a[7], a[2], a[5], a[8]};
	}

	/** {@code m v}. */
	public static double[] mulV(double[] m, double[] v) {
		return new double[]{m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
				m[6] * v[0] + m[7] * v[1] + m[8] * v[2]};
	}

	/** {@code m}<sup>T</sup> {@code v}: a rotation undone. */
	public static double[] mulT(double[] m, double[] v) {
		return mulV(transpose(m), v);
	}
}
