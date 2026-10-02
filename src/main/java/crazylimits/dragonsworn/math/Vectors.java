package crazylimits.dragonsworn.math;

/** Vector helpers on {@code double[3]}: allocation-light, for the solvers' inner loops. */
public final class Vectors {
	private Vectors() {
	}

	public static double[] cross(double[] a, double[] b) {
		return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	public static double length(double[] v) {
		return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
	}

	/** Scales {@code v} to unit length in place; a (near) zero vector is left as it is. */
	public static void normalize(double[] v) {
		double len = length(v);
		if (len < 1e-9) return;
		for (int i = 0; i < 3; i++) v[i] /= len;
	}

	/** Rodrigues: turns {@code v} in place about the unit axis k by the angle with cosine {@code c}, sine {@code s}. */
	public static void turn(double[] v, double kx, double ky, double kz, double c, double s) {
		double x = v[0], y = v[1], z = v[2], dot = kx * x + ky * y + kz * z;
		v[0] = x * c + (ky * z - kz * y) * s + kx * dot * (1 - c);
		v[1] = y * c + (kz * x - kx * z) * s + ky * dot * (1 - c);
		v[2] = z * c + (kx * y - ky * x) * s + kz * dot * (1 - c);
	}
}
