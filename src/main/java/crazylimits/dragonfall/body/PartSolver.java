package crazylimits.dragonfall.body;

import crazylimits.dragonfall.anim.DragonAnim;

/**
 * Where every hitbox is, relative to the dragon's position: the animation frame's anchors
 * ({@link PoseTrack}), bent by the procedural neck ({@link DragonBody#bends}), the tail hung on the body
 * in its procedural pose ({@link Tail}: the animation's tail laid on the ground, trailing turns, kept out
 * of blocks), the wings turned against the body's pitch ({@link DragonBody#wingCounter}), then turned
 * with the whole body exactly as the renderer turns the model: roll and pitch about {@link #CENTER_Y},
 * then yaw.
 */
public final class PartSolver {
	/** Model-space point (blocks) the body rolls and pitches about: the middle of the torso. */
	public static final double CENTER_Y = 3.6, CENTER_Z = -0.5;

	private final double[] points = new double[PoseTrack.POINTS * 3];
	private final double[] neckX = new double[4], neckY = new double[4], tailX = new double[9], tailY = new double[9];
	/** The neck's bends with the head's (the fifth pivot): the body bends only the neck, a strike the head too. */
	private final double[] neckHeadX = new double[PoseTrack.NECK_PIVOTS], neckHeadY = new double[PoseTrack.NECK_PIVOTS];
	/** The tail as placed: its solver (it remembers how it keeps out of blocks), chain and final bends. */
	public final Tail tail = new Tail();
	private final TailChain chain = new TailChain();
	private final TailMotion.Pose motion = new TailMotion.Pose();
	private final double[] finalTailX = new double[9], finalTailY = new double[9];

	/**
	 * Fills {@code out} ({@link PoseTrack#PARTS} * 3) with each part's center offset from the dragon's
	 * position, in world axes (blocks). {@code strike}: a bite or tail strike aimed on top, or null.
	 */
	public void solve(DragonAnim anim, double seconds, DragonBody body, Strike strike, float partialTick, double[] out) {
		solve(anim, seconds, body, strike, partialTick, null, null, out);
	}

	/**
	 * As above, with the tail's motion given ({@code tailMotion}, e.g. blended from the last animation;
	 * null: {@code anim}'s at {@code seconds}) and kept out of the blocks of {@code world} (null: flat
	 * ground, no blocks).
	 */
	public void solve(DragonAnim anim, double seconds, DragonBody body, Strike strike, float partialTick,
			TailMotion.Pose tailMotion, Tail.World world, double[] out) {
		PoseTrack.sample(anim, seconds, points);
		body.bends(partialTick, neckX, neckY, tailX, tailY);
		System.arraycopy(neckX, 0, neckHeadX, 0, neckX.length);
		System.arraycopy(neckY, 0, neckHeadY, 0, neckY.length);
		neckHeadX[neckX.length] = neckHeadY[neckY.length] = 0.0;
		if (strike != null) strike.addBends(anim, seconds, body, partialTick, neckHeadX, neckHeadY, tailX, tailY);
		bendChain(points, PoseTrack.CHAIN_NECK, neckHeadX, neckHeadY);
		if (tailMotion == null) {
			TailMotion.sample(anim, seconds, motion);
			tailMotion = motion;
		}
		tail.solve(chain.frame(points), tailMotion, tailX, tailY, world, finalTailX, finalTailY);
		for (int p = 0; p < PoseTrack.PARTS; p++) {
			if (PoseTrack.partChain(p) == PoseTrack.CHAIN_TAIL) chain.framePoint(PoseTrack.partDepth(p) - 1, points, p, points, p);
		}
		double counter = body.wingCounter(partialTick, keyframedPitch());
		if (counter != 0.0) {
			twist(PoseTrack.CHAIN_LEFT_WING, PoseTrack.LEFT_SHOULDER, counter);
			twist(PoseTrack.CHAIN_RIGHT_WING, PoseTrack.RIGHT_SHOULDER, counter);
		}
		for (int p = 0; p < PoseTrack.PARTS; p++) toWorld(body, partialTick, points, p, out, p);
	}

	/**
	 * Model point {@code i} of {@code model} into world axes relative to the dragon's position, as
	 * {@code out}'s point {@code o}: rolled and pitched about {@link #CENTER_Y}/{@link #CENTER_Z}, yawed, lifted.
	 */
	public static void toWorld(DragonBody body, float partialTick, double[] model, int i, double[] out, int o) {
		double roll = Math.toRadians(-body.roll(partialTick)), pitch = Math.toRadians(body.pitch(partialTick));
		double yaw = Math.toRadians(-body.yaw(partialTick));
		double cr = Math.cos(roll), sr = Math.sin(roll), cp = Math.cos(pitch), sp = Math.sin(pitch);
		double cy = Math.cos(yaw), sy = Math.sin(yaw);
		double px = model[i * 3], py = model[i * 3 + 1] - CENTER_Y, pz = model[i * 3 + 2] - CENTER_Z;
		// roll about z
		double x1 = px * cr - py * sr, y1 = px * sr + py * cr, z1 = pz;
		// pitch about x (positive lifts the -z front)
		double y2 = y1 * cp - z1 * sp, z2 = y1 * sp + z1 * cp;
		y2 += CENTER_Y;
		z2 += CENTER_Z;
		// yaw about y
		out[o * 3] = x1 * cy + z2 * sy;
		out[o * 3 + 1] = y2 + body.lift(partialTick);
		out[o * 3 + 2] = -x1 * sy + z2 * cy;
	}

	/** The inverse of {@link #toWorld}: a point relative to the dragon's position (world axes) into model space. */
	public static void toModel(DragonBody body, float partialTick, double wx, double wy, double wz, double[] out) {
		double roll = Math.toRadians(-body.roll(partialTick)), pitch = Math.toRadians(body.pitch(partialTick));
		double yaw = Math.toRadians(-body.yaw(partialTick));
		double cr = Math.cos(roll), sr = Math.sin(roll), cp = Math.cos(pitch), sp = Math.sin(pitch);
		double cy = Math.cos(yaw), sy = Math.sin(yaw);
		// un-yaw
		double x1 = wx * cy - wz * sy, z2 = wx * sy + wz * cy;
		double y2 = wy - body.lift(partialTick) - CENTER_Y;
		z2 -= CENTER_Z;
		// un-pitch
		double y1 = y2 * cp + z2 * sp, z1 = -y2 * sp + z2 * cp;
		// un-roll
		out[0] = x1 * cr + y1 * sr;
		out[1] = -x1 * sr + y1 * cr + CENTER_Y;
		out[2] = z1 + CENTER_Z;
	}

	/**
	 * Bends a chain of {@code points} (a {@link PoseTrack} frame): segment {@code i} turns everything past
	 * it (deeper parts and later pivots) about its own pivot, Y then X, the way GeckoLib applies a bone's
	 * rotation (Z, Y, X outermost first). The neck's fifth pivot is the head. (The tail is hung exactly
	 * by {@link TailChain}.)
	 */
	public static void bendChain(double[] points, int chain, double[] bendX, double[] bendY) {
		int pivotStart = chain == PoseTrack.CHAIN_NECK ? PoseTrack.NECK_START : PoseTrack.TAIL_START;
		int pivots = chain == PoseTrack.CHAIN_NECK ? PoseTrack.NECK_PIVOTS : PoseTrack.TAIL_PIVOTS;
		for (int i = 0; i < bendX.length && i < pivots; i++) {
			if (bendX[i] == 0.0 && bendY[i] == 0.0) continue;
			double ax = Math.toRadians(bendX[i]), ay = Math.toRadians(bendY[i]);
			double cx = Math.cos(ax), sx = Math.sin(ax), cy = Math.cos(ay), sy = Math.sin(ay);
			int pivot = pivotStart + i;
			double ox = points[pivot * 3], oy = points[pivot * 3 + 1], oz = points[pivot * 3 + 2];
			for (int p = 0; p < PoseTrack.PARTS; p++) {
				if (PoseTrack.partChain(p) == chain && PoseTrack.partDepth(p) > i) rotate(points, p, ox, oy, oz, cx, sx, cy, sy);
			}
			for (int k = i + 1; k < pivots; k++) rotate(points, pivotStart + k, ox, oy, oz, cx, sx, cy, sy);
		}
	}

	/** The animation's body pitch this frame, nose up positive, degrees. */
	private double keyframedPitch() {
		int a = PoseTrack.BODY_AXIS * 3, b = a + 3;
		return Math.toDegrees(Math.atan2(points[b + 1] - points[a + 1], -(points[b + 2] - points[a + 2])));
	}

	/**
	 * Turns a wing's parts by {@code degrees} about its shoulder's X axis (exported as the pivot and a
	 * point along the axis): the shoulder's own X rotation, innermost in GeckoLib's Z, Y, X order.
	 */
	private void twist(int chain, int shoulder, double degrees) {
		double ox = points[shoulder * 3], oy = points[shoulder * 3 + 1], oz = points[shoulder * 3 + 2];
		double kx = points[shoulder * 3 + 3] - ox, ky = points[shoulder * 3 + 4] - oy, kz = points[shoulder * 3 + 5] - oz;
		double len = Math.sqrt(kx * kx + ky * ky + kz * kz);
		kx /= len;
		ky /= len;
		kz /= len;
		double c = Math.cos(Math.toRadians(degrees)), s = Math.sin(Math.toRadians(degrees));
		for (int p = 0; p < PoseTrack.PARTS; p++) {
			if (PoseTrack.partChain(p) != chain) continue;
			double x = points[p * 3] - ox, y = points[p * 3 + 1] - oy, z = points[p * 3 + 2] - oz;
			// Rodrigues: v c + (k x v) s + k (k . v)(1 - c)
			double dot = kx * x + ky * y + kz * z;
			points[p * 3] = ox + x * c + (ky * z - kz * y) * s + kx * dot * (1 - c);
			points[p * 3 + 1] = oy + y * c + (kz * x - kx * z) * s + ky * dot * (1 - c);
			points[p * 3 + 2] = oz + z * c + (kx * y - ky * x) * s + kz * dot * (1 - c);
		}
	}

	private static void rotate(double[] points, int point, double ox, double oy, double oz, double cx, double sx, double cy, double sy) {
		double x = points[point * 3] - ox, y = points[point * 3 + 1] - oy, z = points[point * 3 + 2] - oz;
		// R = Ry * Rx: x first (in the bone's frame), then y
		double y1 = y * cx - z * sx, z1 = y * sx + z * cx;
		double x2 = x * cy + z1 * sy, z2 = -x * sy + z1 * cy;
		points[point * 3] = x2 + ox;
		points[point * 3 + 1] = y1 + oy;
		points[point * 3 + 2] = z2 + oz;
	}
}
