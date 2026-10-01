package crazylimits.dragonfall.limb;

/** Rigid 4x4 transforms as row-major {@code double[16]}: just what the limb code needs. */
public final class Affine {
	private Affine() {}

	public static double[] identity() {
		double[] m = new double[16];
		m[0] = m[5] = m[10] = m[15] = 1.0;
		return m;
	}

	/** {@code Rz(rz) . Ry(ry) . Rx(rx)}, degrees (a bone's rotation: X innermost). */
	public static double[] rotationZYX(double rx, double ry, double rz) {
		double a = Math.toRadians(rx), b = Math.toRadians(ry), c = Math.toRadians(rz);
		double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b), cc = Math.cos(c), sc = Math.sin(c);
		double[] m = new double[16];
		m[0] = cc * cb;
		m[1] = cc * sb * sa - sc * ca;
		m[2] = cc * sb * ca + sc * sa;
		m[4] = sc * cb;
		m[5] = sc * sb * sa + cc * ca;
		m[6] = sc * sb * ca - cc * sa;
		m[8] = -sb;
		m[9] = cb * sa;
		m[10] = cb * ca;
		m[15] = 1.0;
		return m;
	}

	public static double[] mul(double[] a, double[] b) {
		double[] m = new double[16];
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				m[i * 4 + j] = a[i * 4] * b[j] + a[i * 4 + 1] * b[4 + j] + a[i * 4 + 2] * b[8 + j] + a[i * 4 + 3] * b[12 + j];
			}
		}
		return m;
	}

	/** {@code m} applied to point {@code p}, into {@code out} (may be {@code p}). */
	public static double[] apply(double[] m, double[] p, double[] out) {
		double x = p[0], y = p[1], z = p[2];
		out[0] = m[0] * x + m[1] * y + m[2] * z + m[3];
		out[1] = m[4] * x + m[5] * y + m[6] * z + m[7];
		out[2] = m[8] * x + m[9] * y + m[10] * z + m[11];
		return out;
	}

	/** Inverse of a rigid transform (rotation and translation only). */
	public static double[] invertRigid(double[] m) {
		double[] r = new double[16];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) r[i * 4 + j] = m[j * 4 + i];
		}
		for (int i = 0; i < 3; i++) r[i * 4 + 3] = -(r[i * 4] * m[3] + r[i * 4 + 1] * m[7] + r[i * 4 + 2] * m[11]);
		r[15] = 1.0;
		return r;
	}
}
