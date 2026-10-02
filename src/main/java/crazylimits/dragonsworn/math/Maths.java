package crazylimits.dragonsworn.math;

/** Scalar helpers shared by the core (and the bridge): clamps, blends and easing. */
public final class Maths {
	private Maths() {
	}

	/** {@code v} kept within {@code -limit..limit}. */
	public static double clampAbs(double v, double limit) {
		return Math.max(-limit, Math.min(limit, v));
	}

	/** {@code v} kept within {@code min..max}. */
	public static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}

	/** From {@code a} (k = 0) to {@code b} (k = 1); {@code k} is not clamped. */
	public static double lerp(double a, double b, double k) {
		return a + (b - a) * k;
	}

	/** Hermite smoothstep of {@code u} clamped to 0..1: 0 and 1 at the ends, flat there, 0.5 half way. */
	public static double smoothstep(double u) {
		u = clamp(u, 0.0, 1.0);
		return u * u * (3.0 - 2.0 * u);
	}
}
