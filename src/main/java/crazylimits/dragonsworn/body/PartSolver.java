package crazylimits.dragonsworn.body;

import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.limb.BodyFrame;
import crazylimits.dragonsworn.math.Angles;
import crazylimits.dragonsworn.math.Vectors;

/**
 * Where every hitbox is, relative to the dragon's position: the animation frame's anchors
 * ({@link PoseTrack}; blended out of the last animation as GeckoLib blends the model), bent by the
 * procedural neck ({@link DragonBody#bends}) and the head's look ({@link #lookX}), the tail hung on the body
 * in its procedural pose ({@link Tail}: the animation's tail laid on the ground, trailing turns, kept out
 * of blocks), the wings turned against the body's pitch ({@link DragonBody#wingCounter}), then turned
 * with the whole body exactly as the renderer turns the model: roll and pitch about {@link BodyFrame#CENTER_Y},
 * then yaw.
 */
public final class PartSolver {
	private final double[] points = new double[PoseTrack.POINTS * 3];
	private final double[] neckX = new double[4], neckY = new double[4], tailX = new double[9], tailY = new double[9];
	/** The neck's bends with the head's (the fifth pivot): the body bends only the neck, a strike the head too. */
	private final double[] neckHeadX = new double[PoseTrack.NECK_PIVOTS], neckHeadY = new double[PoseTrack.NECK_PIVOTS];
	/** The tail as placed: its solver (it remembers how it keeps out of blocks), chain and final bends. */
	public final Tail tail = new Tail();
	private final TailChain chain = new TailChain();
	private final TailMotion.Pose motion = new TailMotion.Pose();
	private final double[] finalTailX = new double[9], finalTailY = new double[9];
	/** Scratch: the frame bent without the look. */
	private final double[] fromPoints = new double[PoseTrack.POINTS * 3];
	/** The last frame solved (before any bends), and the one the blend running starts from. */
	private final double[] lastFrame = new double[PoseTrack.POINTS * 3], blendFrom = new double[PoseTrack.POINTS * 3];
	private boolean solvedOnce;
	private int blendOf = Integer.MIN_VALUE;
	/**
	 * The head's look ({@code HeadLook}), set by the caller before solving: bends of the four neck segments
	 * and the head (X pitch, Y yaw, degrees), and each tail segment's counter-swing (yaw).
	 */
	public final double[] lookX = new double[PoseTrack.NECK_PIVOTS], lookY = new double[PoseTrack.NECK_PIVOTS];
	public double lookTail;
	/** The head as posed without the look (model space, blocks): its pivot, eyes and forward; valid after a solve. */
	private final double[] headPivot = new double[3], headEyes = new double[3], headForward = {0.0, 0.0, -1.0};
	private boolean headPosed;

	/**
	 * The head at rest (blocks): head_group's pivot, the head part's anchor (tools/parts.py) and between the
	 * eyes (the pupils sit on the skull's front corners: `jaw_upper`'s back cube, x +-7, 3 px under its
	 * top, a pixel behind its front face).
	 */
	static final double[] HEAD_PIVOT = {0.0, 53 / 16.0, -84 / 16.0}, HEAD_ANCHOR = {0.0, 55 / 16.0, -105 / 16.0},
			EYES = {0.0, 58.25 / 16.0, -97.5 / 16.0};

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
		solve(anim, seconds, Blend.NONE, body, strike, partialTick, tailMotion, world, out);
	}

	/**
	 * How the model blends into the animation it plays, as GeckoLib blends it into a new one: {@code amount}
	 * (0..1) of the way from the pose last solved when blend {@code transition} began (whatever showed then,
	 * a blend too), else from {@code from}'s frame at {@code fromSeconds}. {@link #NONE} (or a null
	 * {@code from}, or an amount of 1): the animation alone.
	 */
	public record Blend(DragonAnim from, double fromSeconds, double amount, int transition) {
		public static final Blend NONE = new Blend(null, 0.0, 1.0, 0);
	}

	/** As above, blended into {@code anim}; {@code seconds} is the time into {@code anim} the model shows. */
	public void solve(DragonAnim anim, double seconds, Blend blend, DragonBody body, Strike strike, float partialTick,
			TailMotion.Pose tailMotion, Tail.World world, double[] out) {
		PoseTrack.sample(anim, seconds, points);
		if (blend.from() != null && blend.amount() < 1.0) {
			if (blend.transition() != blendOf) {
				blendOf = blend.transition();
				if (solvedOnce) System.arraycopy(lastFrame, 0, blendFrom, 0, points.length);
				else PoseTrack.sample(blend.from(), blend.fromSeconds(), blendFrom);
			}
			for (int i = 0; i < points.length; i++) points[i] = blendFrom[i] + (points[i] - blendFrom[i]) * blend.amount();
		}
		System.arraycopy(points, 0, lastFrame, 0, points.length);
		solvedOnce = true;
		body.bends(partialTick, neckX, neckY, tailX, tailY);
		System.arraycopy(neckX, 0, neckHeadX, 0, neckX.length);
		System.arraycopy(neckY, 0, neckHeadY, 0, neckY.length);
		neckHeadX[neckX.length] = neckHeadY[neckY.length] = 0.0;
		if (strike != null) strike.addBends(anim, seconds, body, partialTick, neckHeadX, neckHeadY, tailX, tailY);
		// where the head points without the look: what the look turns it from
		System.arraycopy(points, 0, fromPoints, 0, points.length);
		bendChain(fromPoints, PoseTrack.CHAIN_NECK, neckHeadX, neckHeadY);
		poseHead(fromPoints);
		for (int i = 0; i < neckHeadX.length; i++) {
			neckHeadX[i] += lookX[i];
			neckHeadY[i] += lookY[i];
		}
		for (int i = 0; i < tailY.length; i++) tailY[i] += lookTail;
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
	 * {@code out}'s point {@code o}: rolled and pitched about {@link BodyFrame#CENTER_Y}/{@link BodyFrame#CENTER_Z}, yawed, lifted.
	 */
	public static void toWorld(DragonBody body, float partialTick, double[] model, int i, double[] out, int o) {
		double roll = Math.toRadians(-body.roll(partialTick)), pitch = Math.toRadians(body.pitch(partialTick));
		double yaw = Math.toRadians(-body.yaw(partialTick));
		double cr = Math.cos(roll), sr = Math.sin(roll), cp = Math.cos(pitch), sp = Math.sin(pitch);
		double cy = Math.cos(yaw), sy = Math.sin(yaw);
		double px = model[i * 3], py = model[i * 3 + 1] - BodyFrame.CENTER_Y, pz = model[i * 3 + 2] - BodyFrame.CENTER_Z;
		// roll about z
		double x1 = px * cr - py * sr, y1 = px * sr + py * cr, z1 = pz;
		// pitch about x (positive lifts the -z front)
		double y2 = y1 * cp - z1 * sp, z2 = y1 * sp + z1 * cp;
		y2 += BodyFrame.CENTER_Y;
		z2 += BodyFrame.CENTER_Z;
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
		double y2 = wy - body.lift(partialTick) - BodyFrame.CENTER_Y;
		z2 -= BodyFrame.CENTER_Z;
		// un-pitch
		double y1 = y2 * cp + z2 * sp, z1 = -y2 * sp + z2 * cp;
		// un-roll
		out[0] = x1 * cr + y1 * sr;
		out[1] = -x1 * sr + y1 * cr + BodyFrame.CENTER_Y;
		out[2] = z1 + BodyFrame.CENTER_Z;
	}

	/**
	 * Bends a chain of {@code points} (a {@link PoseTrack} frame) by angles added to its bones' rotations,
	 * as the renderer adds them. The neck (four segments, then the head) exactly: {@link NeckChain#bend}. The
	 * tail approximately: segment {@code i} turns everything past it about its own pivot, Y then X, in
	 * model axes (the tail is hung exactly by {@link TailChain}).
	 */
	public static void bendChain(double[] points, int chain, double[] bendX, double[] bendY) {
		if (chain == PoseTrack.CHAIN_NECK) {
			NeckChain.bend(points, bendX, bendY);
			return;
		}
		int pivotStart = PoseTrack.TAIL_START, pivots = PoseTrack.TAIL_PIVOTS;
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

	/**
	 * The head's pivot, eyes and forward from a bent frame: the head is rigid, so the turn that carries
	 * its rest pivot-to-anchor line onto the posed one carries the eyes and the forward too (the shortest
	 * such turn: a roll about that line, nearly the forward itself, is lost).
	 */
	private void poseHead(double[] frame) {
		int pivot = (PoseTrack.NECK_START + PoseTrack.NECK_PIVOTS - 1) * 3, anchor = 0;
		double[] a = {HEAD_ANCHOR[0] - HEAD_PIVOT[0], HEAD_ANCHOR[1] - HEAD_PIVOT[1], HEAD_ANCHOR[2] - HEAD_PIVOT[2]};
		double[] b = {frame[anchor] - frame[pivot], frame[anchor + 1] - frame[pivot + 1], frame[anchor + 2] - frame[pivot + 2]};
		Vectors.normalize(a);
		Vectors.normalize(b);
		double kx = a[1] * b[2] - a[2] * b[1], ky = a[2] * b[0] - a[0] * b[2], kz = a[0] * b[1] - a[1] * b[0];
		double s = Math.sqrt(kx * kx + ky * ky + kz * kz), c = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
		if (s > 1e-9) {
			kx /= s;
			ky /= s;
			kz /= s;
		}
		double[] eyes = {EYES[0] - HEAD_PIVOT[0], EYES[1] - HEAD_PIVOT[1], EYES[2] - HEAD_PIVOT[2]};
		Vectors.turn(eyes, kx, ky, kz, c, s);
		double[] forward = {0.0, 0.0, -1.0};
		Vectors.turn(forward, kx, ky, kz, c, s);
		for (int i = 0; i < 3; i++) {
			headPivot[i] = frame[pivot + i];
			headEyes[i] = frame[pivot + i] + eyes[i];
			headForward[i] = forward[i];
		}
		headPosed = true;
	}

	/**
	 * Where a point (model space, blocks) is from the eyes of the head as last solved, against where the
	 * head points without its look: {@code out} = yaw (left positive), pitch (up positive), degrees.
	 * False before the first solve.
	 */
	public boolean lookAngles(double x, double y, double z, double[] out) {
		if (!headPosed) return false;
		double dx = x - headEyes[0], dy = y - headEyes[1], dz = z - headEyes[2];
		double fx = headForward[0], fy = headForward[1], fz = headForward[2];
		double yaw = Math.toDegrees(Math.atan2(-dx, -dz) - Math.atan2(-fx, -fz));
		out[0] = Angles.wrapDegrees(yaw);
		out[1] = Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)) - Math.atan2(fy, Math.hypot(fx, fz)));
		return true;
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
