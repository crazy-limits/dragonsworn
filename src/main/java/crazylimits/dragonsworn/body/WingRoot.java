package crazylimits.dragonsworn.body;

/**
 * The wings' root webs: where each inner membrane meets the body it folds down (at {@link #FOLD_X}, just
 * outside the body's flank) into a strip {@link #DEPTH} deep, tilted {@link #TILT} degrees in under the body
 * ({@code tools/build_wings.py}, the bone's rest rotation). As the shoulder lifts, sweeps or twists the
 * membrane, the strip turns about the fold (its bone's Z) so it keeps pointing at where its tip is at rest
 * ({@link #AIM}), inside the body, never more than {@link #MAX_TURN} off its rest angle. That keeps the gap
 * between the membrane's root and the body closed.
 * Model space as {@link PoseTrack}: pixels, y up, head toward -z, +x the dragon's right; the left wing's.
 */
public final class WingRoot {
	/** The left shoulder's pivot (the left_wing bone). */
	public static final double[] PIVOT = {-12.0, 65.0, -22.0};
	/** The fold: a line along z through this point (the root web's pivot), x and y. */
	public static final double FOLD_X = -19.0, FOLD_Y = 65.0;
	/** How far the strip reaches from the fold, and the middle of its length along z. */
	public static final double DEPTH = 24.0, MID_Z = 8.0;
	/** Its rest angle past square to the membrane, toward the body, degrees (editor +Z for the left wing). */
	public static final double TILT = 30.0;
	/** What it points at: where its tip is at rest, in the body's frame. */
	public static final double[] AIM = {FOLD_X + DEPTH * Math.sin(Math.toRadians(TILT)), FOLD_Y - DEPTH * Math.cos(Math.toRadians(TILT)), MID_Z};
	/** The most it turns away from its rest angle, degrees. */
	public static final double MAX_TURN = 40.0;

	private WingRoot() {}

	/**
	 * Degrees to turn the left root web about its Z (editor convention, added on top of its keyframe) for a
	 * left shoulder turned by (x, y, z) degrees (GeckoLib's order: Z, then Y, then X, about the pivot).
	 * The right root web takes {@code -fold(x, -y, -z)} of its own shoulder's (x, y, z): it is the mirror.
	 */
	public static double fold(double x, double y, double z) {
		// the aim in the shoulder's frame: R^T (aim - pivot), R = Rz Ry Rx
		double[] d = {AIM[0] - PIVOT[0], AIM[1] - PIVOT[1], AIM[2] - PIVOT[2]};
		d = rotZ(d, -z);
		d = rotY(d, -y);
		d = rotX(d, -x);
		double dx = d[0] + PIVOT[0] - FOLD_X, dy = d[1] + PIVOT[1] - FOLD_Y;
		// untilted the strip hangs along -y; turned by t about Z it points along (sin t, -cos t)
		double turn = Math.toDegrees(Math.atan2(dx, -dy)) - TILT;
		return Math.max(-MAX_TURN, Math.min(MAX_TURN, turn));
	}

	private static double[] rotX(double[] v, double deg) {
		double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
		return new double[]{v[0], c * v[1] - s * v[2], s * v[1] + c * v[2]};
	}

	private static double[] rotY(double[] v, double deg) {
		double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
		return new double[]{c * v[0] + s * v[2], v[1], -s * v[0] + c * v[2]};
	}

	private static double[] rotZ(double[] v, double deg) {
		double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
		return new double[]{c * v[0] - s * v[1], s * v[0] + c * v[1], v[2]};
	}
}
