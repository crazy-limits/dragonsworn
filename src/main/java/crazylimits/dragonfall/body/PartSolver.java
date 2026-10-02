package crazylimits.dragonfall.body;

import crazylimits.dragonfall.anim.DragonAnim;

/**
 * Where every hitbox is, relative to the dragon's position: the animation frame's anchors
 * ({@link PoseTrack}; blended out of the last animation as GeckoLib blends the model), bent by the
 * procedural neck ({@link DragonBody#bends}) and the head's look ({@link #lookX}), the tail hung on the body
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
	private static final double[] HEAD_PIVOT = {0.0, 53 / 16.0, -84 / 16.0}, HEAD_ANCHOR = {0.0, 55 / 16.0, -105 / 16.0},
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
		solve(anim, seconds, null, 0.0, 1.0, 0, body, strike, partialTick, tailMotion, world, out);
	}

	/**
	 * As above, blended into {@code anim} by {@code blend} (0..1; 1 or a null {@code from}: {@code anim}
	 * alone), as GeckoLib blends the model into a new animation: from the pose last solved when blend
	 * {@code transition} began (whatever showed then, a blend too), else from {@code from}'s frame at
	 * {@code fromSeconds}. {@code seconds} is the time into {@code anim} the model shows.
	 */
	public void solve(DragonAnim anim, double seconds, DragonAnim from, double fromSeconds, double blend, int transition,
			DragonBody body, Strike strike, float partialTick, TailMotion.Pose tailMotion, Tail.World world, double[] out) {
		PoseTrack.sample(anim, seconds, points);
		if (from != null && blend < 1.0) {
			if (transition != blendOf) {
				blendOf = transition;
				if (solvedOnce) System.arraycopy(lastFrame, 0, blendFrom, 0, points.length);
				else PoseTrack.sample(from, fromSeconds, blendFrom);
			}
			for (int i = 0; i < points.length; i++) points[i] = blendFrom[i] + (points[i] - blendFrom[i]) * blend;
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
	 * Bends a chain of {@code points} (a {@link PoseTrack} frame) by angles added to its bones' rotations,
	 * as the renderer adds them. The neck (four segments, then the head) exactly: {@link #bendNeck}. The
	 * tail approximately: segment {@code i} turns everything past it about its own pivot, Y then X, in
	 * model axes (the tail is hung exactly by {@link TailChain}).
	 */
	public static void bendChain(double[] points, int chain, double[] bendX, double[] bendY) {
		if (chain == PoseTrack.CHAIN_NECK) {
			bendNeck(points, bendX, bendY);
			return;
		}
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

	/**
	 * The head's pivot, eyes and forward from a bent frame: the head is rigid, so the turn that carries
	 * its rest pivot-to-anchor line onto the posed one carries the eyes and the forward too (the shortest
	 * such turn: a roll about that line, nearly the forward itself, is lost).
	 */
	private void poseHead(double[] frame) {
		int pivot = (PoseTrack.NECK_START + PoseTrack.NECK_PIVOTS - 1) * 3, anchor = 0;
		double[] a = {HEAD_ANCHOR[0] - HEAD_PIVOT[0], HEAD_ANCHOR[1] - HEAD_PIVOT[1], HEAD_ANCHOR[2] - HEAD_PIVOT[2]};
		double[] b = {frame[anchor] - frame[pivot], frame[anchor + 1] - frame[pivot + 1], frame[anchor + 2] - frame[pivot + 2]};
		normalize(a);
		normalize(b);
		double kx = a[1] * b[2] - a[2] * b[1], ky = a[2] * b[0] - a[0] * b[2], kz = a[0] * b[1] - a[1] * b[0];
		double s = Math.sqrt(kx * kx + ky * ky + kz * kz), c = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
		if (s > 1e-9) {
			kx /= s;
			ky /= s;
			kz /= s;
		}
		double[] eyes = {EYES[0] - HEAD_PIVOT[0], EYES[1] - HEAD_PIVOT[1], EYES[2] - HEAD_PIVOT[2]};
		turn(eyes, kx, ky, kz, c, s);
		double[] forward = {0.0, 0.0, -1.0};
		turn(forward, kx, ky, kz, c, s);
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
		yaw = ((yaw % 360.0) + 540.0) % 360.0 - 180.0;
		out[0] = yaw;
		out[1] = Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)) - Math.atan2(fy, Math.hypot(fx, fz)));
		return true;
	}

	private static void normalize(double[] v) {
		double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		if (len < 1e-9) return;
		for (int i = 0; i < 3; i++) v[i] /= len;
	}

	/** Rodrigues: turns {@code v} about the unit axis k by the angle with cosine c, sine s. */
	private static void turn(double[] v, double kx, double ky, double kz, double c, double s) {
		double x = v[0], y = v[1], z = v[2], dot = kx * x + ky * y + kz * z;
		v[0] = x * c + (ky * z - kz * y) * s + kx * dot * (1 - c);
		v[1] = y * c + (kz * x - kx * z) * s + ky * dot * (1 - c);
		v[2] = z * c + (kx * y - ky * x) * s + kz * dot * (1 - c);
	}

	/**
	 * The neck's bones at rest (blocks, editor space, x = 0): the pivots of neck_1..neck_4 and head_group,
	 * then the head part's anchor. No neck bone has a rest rotation, and no animation keys their roll or
	 * position (only the death rolls the head), so each bone's keyed pitch and yaw follow from where the
	 * frame puts its pivot and the next one.
	 */
	private static final double[][] NECK_REST = {{0, 53.125 / 16, -29 / 16.0}, {0, 53.125 / 16, -44 / 16.0},
			{0, 55.125 / 16, -58.5 / 16}, {0, 55.125 / 16, -72 / 16.0}, HEAD_PIVOT, HEAD_ANCHOR};

	/**
	 * The neck bent exactly as GeckoLib draws it: bone {@code i} (neck_1..neck_4, then head_group) is turned
	 * by Rz Ry Rx of its keyed angles, and the renderer adds {@code bendY[i]} to its Y and {@code bendX[i]}
	 * to its X, so the yaw turns about the bone's axis as tilted by its parents (and the yaw part of its
	 * own key), the pitch about its own X. The keyed angles are recovered from the frame (pivot to next
	 * pivot, through the body's frame), so with no bends the frame is left exactly as it was.
	 */
	static void bendNeck(double[] points, double[] bendX, double[] bendY) {
		int joints = Math.min(PoseTrack.NECK_PIVOTS, bendX.length);
		boolean any = false;
		for (int i = 0; i < joints; i++) any |= bendX[i] != 0.0 || bendY[i] != 0.0;
		if (!any) return;
		Neck neck = new Neck(points, bendX, bendY, PoseTrack.NECK_PIVOTS);
		for (int p = 0; p < PoseTrack.PARTS; p++) {
			if (PoseTrack.partChain(p) != PoseTrack.CHAIN_NECK) continue;
			int bone = PoseTrack.partDepth(p) - 1;
			carry(points, p, neck.pivot[bone], neck.moved[bone], neck.turn[bone]);
		}
		for (int k = 1; k < PoseTrack.NECK_PIVOTS; k++) {
			int at = (PoseTrack.NECK_START + k) * 3;
			points[at] = neck.moved[k][0];
			points[at + 1] = neck.moved[k][1];
			points[at + 2] = neck.moved[k][2];
		}
	}

	/**
	 * The bends of neck joint {@code joint} (degrees: X, Y, on top of its keyed angles) that point its
	 * segment (its pivot to the next one, the head's to its anchor) along {@code direction} (model space),
	 * with the bends of the joints before it as given ({@code bendX/Y}, later ones ignored). {@code points}
	 * is the unbent frame.
	 */
	public static double[] neckAim(double[] points, double[] bendX, double[] bendY, int joint, double[] direction) {
		Neck neck = new Neck(points, bendX, bendY, joint);
		double[] rest = NECK_REST[joint], next = NECK_REST[joint + 1];
		double dy = next[1] - rest[1], dz = next[2] - rest[2];
		double[] v = mulT(neck.bent, direction);
		double scale = Math.hypot(dy, dz) / Math.max(1e-9, Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]));
		double[] want = keyedAngles(dy, dz, v[0] * scale, v[1] * scale, v[2] * scale);
		double[] keyed = neck.keyedAngles(joint);
		return new double[]{Math.toDegrees(wrapRadians(want[0] - keyed[0])), Math.toDegrees(wrapRadians(want[1] - keyed[1]))};
	}

	/** The neck's forward kinematics over a frame, joint by joint (see {@link #bendNeck}). */
	private static final class Neck {
		/** Each pivot (then the head's anchor) as the frame has it, and as bent. */
		final double[][] pivot = new double[PoseTrack.NECK_PIVOTS + 1][], moved = new double[PoseTrack.NECK_PIVOTS + 1][];
		/** Per joint: the turn from the frame's bone to the bent one. */
		final double[][] turn = new double[PoseTrack.NECK_PIVOTS][];
		private final double[] frame;
		/** The rotations reached so far: as keyed, and bent (after {@code joints} joints). */
		double[] keyed, bent;

		/** Runs the first {@code joints} joints (all five: the whole neck). */
		Neck(double[] points, double[] bendX, double[] bendY, int joints) {
			this.frame = points;
			int n = PoseTrack.NECK_PIVOTS;
			// the body's rotation (the neck's parent): its frame points, one block ahead (-z) and above (+y)
			int b = PoseTrack.BODY_AXIS * 3;
			double[] ey = {points[b + 6] - points[b], points[b + 7] - points[b + 1], points[b + 8] - points[b + 2]};
			double[] ez = {points[b] - points[b + 3], points[b + 1] - points[b + 4], points[b + 2] - points[b + 5]};
			normalize(ey);
			double[] ex = cross(ey, ez);
			normalize(ex);
			ez = cross(ex, ey);
			keyed = new double[]{ex[0], ey[0], ez[0], ex[1], ey[1], ez[1], ex[2], ey[2], ez[2]};
			bent = keyed.clone();
			for (int k = 0; k <= n; k++) {
				int at = k < n ? (PoseTrack.NECK_START + k) * 3 : 0;
				pivot[k] = new double[]{points[at], points[at + 1], points[at + 2]};
			}
			moved[0] = pivot[0].clone();
			for (int k = 0; k < joints; k++) {
				double[] angles = keyedAngles(k);
				double bx = k < bendX.length ? bendX[k] : 0.0, by = k < bendY.length ? bendY[k] : 0.0;
				keyed = mul(keyed, yx(angles[1], angles[0]));
				bent = mul(bent, yx(angles[1] + Math.toRadians(by), angles[0] + Math.toRadians(bx)));
				// what this bone carries: from where the frame has it to where the bend puts it
				turn[k] = mul(bent, transpose(keyed));
				double[] d = mulV(turn[k], segment(k));
				moved[k + 1] = new double[]{moved[k][0] + d[0], moved[k][1] + d[1], moved[k][2] + d[2]};
			}
		}

		/** Joint {@code k}'s keyed angles (radians: X, Y), in its parent's keyed frame (joints before it run). */
		double[] keyedAngles(int k) {
			double[] rest = NECK_REST[k], next = NECK_REST[k + 1];
			double dy = next[1] - rest[1], dz = next[2] - rest[2];
			double[] v = mulT(keyed, segment(k));
			double scale = Math.hypot(dy, dz) / Math.max(1e-9, Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]));
			return PartSolver.keyedAngles(dy, dz, v[0] * scale, v[1] * scale, v[2] * scale);
		}

		private double[] segment(int k) {
			return new double[]{pivot[k + 1][0] - pivot[k][0], pivot[k + 1][1] - pivot[k][1], pivot[k + 1][2] - pivot[k][2]};
		}
	}

	/**
	 * The keyed X and Y (radians) that turn a rest segment (0, dy, dz) onto {@code (vx, vy, vz)} (same
	 * length) by Ry Rx: of the two, the one turned least.
	 */
	static double[] keyedAngles(double dy, double dz, double vx, double vy, double vz) {
		double r = Math.hypot(dy, dz), phi = Math.atan2(dz, dy);
		double a = Math.acos(Math.max(-1.0, Math.min(1.0, vy / r)));
		double[] best = null;
		for (double alpha : new double[]{a, -a}) {
			double kx = wrapRadians(alpha - phi), z1 = r * Math.sin(alpha);
			double ky = z1 >= 0 ? Math.atan2(vx, vz) : Math.atan2(-vx, -vz);
			if (best == null || Math.abs(kx) + Math.abs(ky) < Math.abs(best[0]) + Math.abs(best[1])) best = new double[]{kx, ky};
		}
		return best;
	}

	private static void carry(double[] points, int i, double[] from, double[] to, double[] turn) {
		double[] d = mulV(turn, new double[]{points[i * 3] - from[0], points[i * 3 + 1] - from[1], points[i * 3 + 2] - from[2]});
		points[i * 3] = to[0] + d[0];
		points[i * 3 + 1] = to[1] + d[1];
		points[i * 3 + 2] = to[2] + d[2];
	}

	/** Ry(y) Rx(x), row-major 3x3, as rig.py's euler_zyx with no Z. */
	private static double[] yx(double y, double x) {
		double cy = Math.cos(y), sy = Math.sin(y), cx = Math.cos(x), sx = Math.sin(x);
		return new double[]{cy, sy * sx, sy * cx, 0, cx, -sx, -sy, cy * sx, cy * cx};
	}

	private static double[] mul(double[] a, double[] b) {
		double[] m = new double[9];
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) m[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
		}
		return m;
	}

	private static double[] transpose(double[] a) {
		return new double[]{a[0], a[3], a[6], a[1], a[4], a[7], a[2], a[5], a[8]};
	}

	private static double[] mulV(double[] m, double[] v) {
		return new double[]{m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
				m[6] * v[0] + m[7] * v[1] + m[8] * v[2]};
	}

	private static double[] mulT(double[] m, double[] v) {
		return mulV(transpose(m), v);
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	private static double wrapRadians(double a) {
		return Math.atan2(Math.sin(a), Math.cos(a));
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
