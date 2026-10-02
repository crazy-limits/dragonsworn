package crazylimits.dragonsworn.math;

/** Angle wrapping, in Minecraft's conventions (degrees for yaw and pitch, radians inside the solvers). */
public final class Angles {
	private Angles() {
	}

	/** {@code degrees} wrapped into {@code -180 <= a < 180} (as vanilla's {@code Mth.wrapDegrees}). */
	public static double wrapDegrees(double degrees) {
		degrees %= 360.0;
		if (degrees >= 180.0) degrees -= 360.0;
		if (degrees < -180.0) degrees += 360.0;
		return degrees;
	}

	/** As {@link #wrapDegrees(double)}, in float arithmetic (entity rotations are floats). */
	public static float wrapDegrees(float degrees) {
		float d = degrees % 360.0F;
		if (d >= 180.0F) d -= 360.0F;
		if (d < -180.0F) d += 360.0F;
		return d;
	}

	/** {@code radians} wrapped into {@code -PI..PI}: the signed turn the shorter way round. */
	public static double wrapRadians(double radians) {
		return Math.IEEEremainder(radians, 2 * Math.PI);
	}
}
