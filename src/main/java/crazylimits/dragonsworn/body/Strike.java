package crazylimits.dragonsworn.body;

import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.attack.BreathPass;
import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.math.Maths;

import java.util.Arrays;

/**
 * A bite or a tail strike aimed at one point: the procedural layer that puts the jaws (or the tail's tip)
 * exactly where the prey was when the dragon committed to the blow.
 *
 * <p>The keyframes ({@link DragonAnim#ATTACK}, {@link DragonAnim#TAIL_SWEEP}) only give the blow its
 * rhythm: the bite's coil and lunge, the tail's lift and rattle. On top of them every neck segment and
 * the head (or every tail segment) is bent by inverse kinematics, solved on the frame where the blow
 * lands, so that on that frame the end of the chain is at the aim point. The bends are faded in and out
 * over the animation ({@link #weight}): the head swings round onto the prey while it coils; the tail is
 * first cocked the other way, then whipped root first onto it.
 *
 * <p>The aim is a fixed point, not the prey: whatever moved away before the blow is missed. Out of reach,
 * the chain stretches as far as it goes and stops short, a miss too ({@link #solve} returns how short).
 *
 * <p>The stream breaths ({@link DragonAnim#BREATH}, the pass's {@link DragonAnim#GLIDE_BREATH} and the hover's) are aimed too, but by direction: the neck is drawn out
 * straight along the line from its base to the aim, the head pointing down it, so the flames leave the
 * mouth straight at the aim.
 *
 * <p>Points and the aim are relative to the dragon's position in world axes, blocks; bends are degrees,
 * in {@link PartSolver#bendChain}'s convention, the same numbers the renderer adds to the bones.
 */
public final class Strike {
	/** How close (blocks) the IK must put the jaws or the tail's tip on the aim for the blow to reach (half of it), and the tail's hit. */
	public static final double BITE_RADIUS = 1.1, TAIL_RADIUS = 1.3;
	/** How far the tail is cocked away from the prey before the whip, as a share of the strike's bends. */
	static final double COCK = 0.5;
	/** Per-joint reach of the bends, degrees: the four neck segments and the head; every tail segment. */
	private static final double[] NECK_LIMIT_X = {25, 25, 30, 30, 35}, NECK_LIMIT_Y = {30, 30, 35, 35, 40};
	private static final double TAIL_LIMIT_X = 20, TAIL_LIMIT_Y = 32;
	/** How far the breath may straighten the neck and turn the head from the keyframes, degrees. */
	private static final double BREATH_LIMIT = 80.0;
	/** The breath's frame the neck is straightened on: the middle of the stream. */
	static final double BREATH_FRAME = 2.5;
	private static final int ITERATIONS = 30;
	private static final double DAMPING = 1.0, MAX_STEP = Math.toRadians(15.0), STEP = 0.5;

	private DragonAnim anim;
	private double aimX, aimY, aimZ;

	private final double[] frame = new double[PoseTrack.POINTS * 3], work = new double[PoseTrack.POINTS * 3];
	private final double[] neckX = new double[4], neckY = new double[4], tailX = new double[9], tailY = new double[9];
	/** The strike's own bends (degrees), on top of the body's: neck + head, or tail. */
	private final double[] bendX = new double[9], bendY = new double[9];
	private final double[] totalX = new double[9], totalY = new double[9];
	private final double[] target = new double[3], tip = new double[3];
	/** The tail: hung on the body of the blow's frame, with the strike's own motion laid on the flat ground. */
	private final TailChain chain = new TailChain();
	private final TailMotion.Pose motion = new TailMotion.Pose();
	private final double[] laidX = new double[9], laidY = new double[9], tipPoint = new double[3];
	private final double[][] jacobian = new double[18][3];

	/** Whether {@code anim} is a strike (has an aim). */
	public static boolean strikes(DragonAnim anim) {
		return anim != null && (anim.bites() || anim == DragonAnim.TAIL_SWEEP || breathes(anim));
	}

	/** A stream breath: aimed by direction, the neck straightened onto the aim ({@link #straighten}). */
	static boolean breathes(DragonAnim anim) {
		return anim == DragonAnim.BREATH || anim.breathesInFlight();
	}

	/** The moment (animation seconds) the blow lands: the frame the aim is solved on. */
	public static double hitSeconds(DragonAnim anim) {
		return switch (anim) {
			case TAIL_SWEEP -> DragonAnim.TAIL_HIT_SECONDS;
			case BREATH -> BREATH_FRAME;
			case GLIDE_BREATH, HOVER_BREATH -> BreathPass.AIM_SECONDS;
			default -> DragonAnim.BITE_SECONDS;
		};
	}

	public static double radius(DragonAnim anim) {
		return anim == DragonAnim.TAIL_SWEEP ? TAIL_RADIUS : BITE_RADIUS;
	}

	/** How close (blocks) the blow must come to a body to hit it. */
	public static double hitRadius(DragonAnim anim) {
		return anim == DragonAnim.TAIL_SWEEP ? TAIL_RADIUS : DragonConfig.BITE_RADIUS.get();
	}

	/**
	 * Where a bite's jaws are on its blow's frame before the strike bends the neck, with the body as it is
	 * at {@code partialTick}: relative to the dragon, as {forward, up, right} along its facing. A bite
	 * reaches what is near it (the IK does the rest), so a flying dragon puts itself there to bite.
	 */
	public static double[] rest(DragonAnim anim, DragonBody body, float partialTick) {
		double[] frame = new double[PoseTrack.POINTS * 3], world = new double[3];
		PoseTrack.sample(anim, hitSeconds(anim), frame);
		PartSolver.toWorld(body, partialTick, frame, Parts.HEAD, world, 0);
		double yaw = Math.toRadians(body.yaw(partialTick)), fx = Math.sin(yaw), fz = -Math.cos(yaw);
		return new double[] {world[0] * fx + world[2] * fz, world[1], -world[0] * fz + world[2] * fx};
	}

	/** The neck's base (its first pivot) on {@code anim}'s frame at {@code seconds}, relative to the dragon in world axes: where a breath's aim is measured from. */
	public static void neckBase(DragonAnim anim, double seconds, DragonBody body, float partialTick, double[] out) {
		double[] frame = new double[PoseTrack.POINTS * 3];
		PoseTrack.sample(anim, seconds, frame);
		PartSolver.toWorld(body, partialTick, frame, PoseTrack.NECK_START, out, 0);
	}

	/** Aims {@code anim} (a {@link #strikes strike}) at a point relative to the dragon. */
	public void aim(DragonAnim anim, double dx, double dy, double dz) {
		if (!strikes(anim)) throw new IllegalArgumentException(anim + " is not a strike");
		if (anim != this.anim) {
			Arrays.fill(bendX, 0.0);
			Arrays.fill(bendY, 0.0);
		}
		this.anim = anim;
		aimX = dx;
		aimY = dy;
		aimZ = dz;
	}

	public void clear() {
		anim = null;
	}

	/** The strike aimed, or null. */
	public DragonAnim anim() {
		return anim;
	}

	/**
	 * Solves the bends that put the chain's end on the aim at the blow's frame, with the body as it is at
	 * {@code partialTick}. Returns how far (blocks) the end stays from the aim: 0 when it reaches it.
	 */
	public double solve(DragonBody body, float partialTick) {
		if (anim == null) return Double.POSITIVE_INFINITY;
		boolean tail = anim == DragonAnim.TAIL_SWEEP;
		int joints = tail ? PoseTrack.TAIL_PIVOTS : PoseTrack.NECK_PIVOTS;
		PoseTrack.sample(anim, hitSeconds(anim), frame);
		body.bends(partialTick, neckX, neckY, tailX, tailY);
		if (tail) {
			// the tail as the strike's motion has it at the blow (TailMotion), laid on flat ground
			chain.frame(frame);
			TailMotion.sample(anim, hitSeconds(anim), motion);
			TailLay.lay(chain, motion, null, laidX, laidY);
			for (int i = 0; i < tailX.length; i++) {
				tailX[i] += laidX[i];
				tailY[i] += laidY[i];
			}
		}
		PartSolver.toModel(body, partialTick, aimX, aimY, aimZ, target);
		if (breathes(anim)) return straighten();

		double miss = 0.0;
		for (int it = 0; it < ITERATIONS; it++) {
			end(tail, joints, tip);
			double rx = target[0] - tip[0], ry = target[1] - tip[1], rz = target[2] - tip[2];
			miss = Math.sqrt(rx * rx + ry * ry + rz * rz);
			if (miss < 0.01) break;
			// Jacobian by finite differences, per radian
			for (int j = 0; j < 2 * joints; j++) {
				double[] b = j < joints ? bendX : bendY;
				int k = j % joints;
				b[k] += STEP;
				double[] moved = end(tail, joints, new double[3]);
				b[k] -= STEP;
				for (int a = 0; a < 3; a++) jacobian[j][a] = (moved[a] - tip[a]) / Math.toRadians(STEP);
			}
			// damped least squares: dq = J^T (J J^T + l^2 I)^-1 r
			double[] m = new double[9];
			for (int j = 0; j < 2 * joints; j++) {
				for (int a = 0; a < 3; a++) {
					for (int c = 0; c < 3; c++) m[a * 3 + c] += jacobian[j][a] * jacobian[j][c];
				}
			}
			for (int a = 0; a < 3; a++) m[a * 4] += DAMPING * DAMPING;
			double[] y = solve3(m, rx, ry, rz);
			for (int j = 0; j < 2 * joints; j++) {
				double dq = jacobian[j][0] * y[0] + jacobian[j][1] * y[1] + jacobian[j][2] * y[2];
				dq = Math.max(-MAX_STEP, Math.min(MAX_STEP, dq));
				int k = j % joints;
				if (j < joints) bendX[k] = Maths.clampAbs(bendX[k] + Math.toDegrees(dq), tail ? TAIL_LIMIT_X : NECK_LIMIT_X[k]);
				else bendY[k] = Maths.clampAbs(bendY[k] + Math.toDegrees(dq), tail ? TAIL_LIMIT_Y : NECK_LIMIT_Y[k]);
			}
		}
		end(tail, joints, tip);
		double rx = target[0] - tip[0], ry = target[1] - tip[1], rz = target[2] - tip[2];
		return Math.sqrt(rx * rx + ry * ry + rz * rz);
	}

	/**
	 * The breath: each neck segment, then the head, root first, turned so it points from its pivot along
	 * the line from the neck's base to the aim ({@link NeckChain#aim}). Returns how far the mouth's
	 * line passes from the aim.
	 */
	private double straighten() {
		Arrays.fill(bendX, 0.0);
		Arrays.fill(bendY, 0.0);
		int joints = PoseTrack.NECK_PIVOTS, base = PoseTrack.NECK_START;
		double ox = frame[base * 3], oy = frame[base * 3 + 1], oz = frame[base * 3 + 2];
		double[] direction = {target[0] - ox, target[1] - oy, target[2] - oz};
		if (Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1] + direction[2] * direction[2]) < 1e-6) return 0.0;
		for (int i = 0; i < joints; i++) {
			double baseX = i < neckX.length ? neckX[i] : 0.0, baseY = i < neckY.length ? neckY[i] : 0.0;
			for (int j = 0; j < joints; j++) {
				totalX[j] = (j < neckX.length ? neckX[j] : 0.0) + bendX[j];
				totalY[j] = (j < neckY.length ? neckY[j] : 0.0) + bendY[j];
			}
			double[] want = NeckChain.aim(frame, totalX, totalY, i, direction);
			bendX[i] = Maths.clampAbs(want[0] - baseX, BREATH_LIMIT);
			bendY[i] = Maths.clampAbs(want[1] - baseY, BREATH_LIMIT);
		}
		end(false, joints, tip);
		// distance of the aim from the line the head points along
		int h = base + joints - 1;
		double hx = tip[0] - work[h * 3], hy = tip[1] - work[h * 3 + 1], hz = tip[2] - work[h * 3 + 2];
		double hl = Math.sqrt(hx * hx + hy * hy + hz * hz);
		double tx = target[0] - tip[0], ty = target[1] - tip[1], tz = target[2] - tip[2];
		double along = (tx * hx + ty * hy + tz * hz) / hl;
		return Math.sqrt(Math.max(0.0, tx * tx + ty * ty + tz * tz - along * along));
	}

	/** Where the chain's end is at the blow (after {@link #solve}), relative to the dragon in world axes. */
	public void blow(DragonBody body, float partialTick, double[] out) {
		double[] model = {tip[0], tip[1], tip[2]};
		PartSolver.toWorld(body, partialTick, model, 0, out, 0);
	}

	/**
	 * Adds this frame's share of the strike to the bends {@link PartSolver} and the renderer put on the
	 * neck and head ({@code neckX/Y}: 5 joints) and the tail (9), when {@code playing} is the strike.
	 */
	public void addBends(DragonAnim playing, double seconds, DragonBody body, float partialTick,
			double[] neckX, double[] neckY, double[] tailX, double[] tailY) {
		if (anim == null || playing != anim) return;
		solve(body, partialTick);
		boolean tail = anim == DragonAnim.TAIL_SWEEP;
		double[] outX = tail ? tailX : neckX, outY = tail ? tailY : neckY;
		int joints = tail ? PoseTrack.TAIL_PIVOTS : PoseTrack.NECK_PIVOTS;
		for (int i = 0; i < joints; i++) {
			double w = weight(anim, seconds, i);
			outX[i] += w * bendX[i];
			outY[i] += w * bendY[i];
		}
	}

	/**
	 * How much of the strike's bends segment {@code segment} carries at {@code seconds} into the animation:
	 * exactly 1 for every segment at the blow. The bite swings the head onto the aim through the coil and
	 * the lunge. The tail is cocked away ({@code -COCK}) during the wind-up, then whipped onto the aim
	 * root first, the tip arriving just before the blow.
	 */
	public static double weight(DragonAnim anim, double seconds, int segment) {
		double hit = hitSeconds(anim);
		if (anim.bites()) {
			if (seconds <= hit) return Maths.smoothstep(seconds / hit);
			return 1.0 - Maths.smoothstep((seconds - hit - 0.1) / 0.5);
		}
		if (anim == DragonAnim.BREATH) {
			// the neck stretches out at the end of the inhale and holds through the stream (see BreathAttack)
			return Maths.smoothstep((seconds - 0.6) / 0.45) * (1.0 - Maths.smoothstep((seconds - 4.0) / 0.6));
		}
		if (anim.breathesInFlight()) {
			// with the pose's swing down before the fire, held through the stream, gone over the recovery
			double fire = BreathPass.WINDUP_TICKS / 20.0, end = fire + BreathPass.STREAM_TICKS / 20.0;
			return Maths.smoothstep((seconds - fire + BreathPass.LUNGE_SECONDS) / BreathPass.LUNGE_LENGTH) * (1.0 - Maths.smoothstep((seconds - end) / 0.6));
		}
		double whip = 0.15, start = hit - 0.02 - whip - 0.02 * (PoseTrack.TAIL_PIVOTS - 1 - segment);
		double cocked = -COCK * Maths.smoothstep(seconds / (hit - 0.35));
		double w = cocked + (1.0 - cocked) * Maths.smoothstep((seconds - start) / whip);
		return w * (1.0 - Maths.smoothstep((seconds - hit - 0.1) / 0.7));
	}

	/** The end of the chain in model space with the body's bends (and the tail's motion) plus the strike's. */
	private double[] end(boolean tail, int joints, double[] out) {
		System.arraycopy(frame, 0, work, 0, work.length);
		for (int i = 0; i < joints; i++) {
			double baseX = tail ? tailX[i] : i < neckX.length ? neckX[i] : 0.0;
			double baseY = tail ? tailY[i] : i < neckY.length ? neckY[i] : 0.0;
			totalX[i] = baseX + bendX[i];
			totalY[i] = baseY + bendY[i];
		}
		if (tail) {
			chain.pose(totalX, totalY);
			chain.framePoint(PoseTrack.partDepth(Parts.TAIL_TIP) - 1, frame, Parts.TAIL_TIP, tipPoint, 0);
			out[0] = tipPoint[0];
			out[1] = tipPoint[1];
			out[2] = tipPoint[2];
			return out;
		}
		// bendChain stops at the chain's own joint count (5 on the neck)
		PartSolver.bendChain(work, PoseTrack.CHAIN_NECK, totalX, totalY);
		int part = Parts.HEAD;
		out[0] = work[part * 3];
		out[1] = work[part * 3 + 1];
		out[2] = work[part * 3 + 2];
		return out;
	}

	/** Solves the symmetric 3x3 system {@code m} y = r (Cramer). */
	private static double[] solve3(double[] m, double rx, double ry, double rz) {
		double a = m[0], b = m[1], c = m[2], d = m[3], e = m[4], f = m[5], g = m[6], h = m[7], i = m[8];
		double det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
		if (Math.abs(det) < 1e-12) return new double[3];
		double x = rx * (e * i - f * h) - b * (ry * i - f * rz) + c * (ry * h - e * rz);
		double y = a * (ry * i - f * rz) - rx * (d * i - f * g) + c * (d * rz - ry * g);
		double z = a * (e * rz - ry * h) - b * (d * rz - ry * g) + rx * (d * h - e * g);
		return new double[]{x / det, y / det, z / det};
	}
}
