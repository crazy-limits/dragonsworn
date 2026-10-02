package crazylimits.dragonsworn.limb;

/**
 * Inverse kinematics for the four limbs, at run time, on top of whatever pose the animation is in. The
 * animation stays in charge of the motion (its steps are already matched to the ground speed); IK only
 * carries each foot up or down to the real ground under it. Both solvers are exact and continuous, so a
 * limb never flips between solutions from one frame to the next:
 * <ul>
 *   <li><b>Hind leg</b>: thigh and shin turn about X only, solved in closed form in the leg's plane
 *       (the knee keeps bending the way the animation bends it); the foot turns back by what they
 *       turned, so the sole keeps its angle.</li>
 *   <li><b>Front limb</b> (the folded wing, walking on its wrist): only the shoulder's Z turns, raising
 *       or lowering the whole folded wing rigidly (the wing rule: the fold is untouched).</li>
 * </ul>
 * Editor space throughout: pixels and degrees.
 */
public final class LimbIK {
	/** How far a shoulder may turn to reach the ground, degrees. */
	public static final double MAX_SHOULDER = 35.0;
	/** How far an elbow may bend from the animation to reach, degrees. */
	public static final double MAX_ELBOW = 40.0;
	/** How far a thigh may swing out or in at the hip to reach sideways, degrees. */
	public static final double MAX_SPLAY = 25.0;
	/**
	 * Past this share of its full length a leg straightens ever more slowly toward the target (the soft
	 * reach limit): near full stretch the knee angle would otherwise jump for a tiny move.
	 */
	public static final double SOFT_REACH = 0.88;

	private LimbIK() {}

	/**
	 * Moves the leg so the ankle (the foot's pivot) reaches {@code target} as nearly as it can.
	 * {@code parent}: the model matrix of the bone the thigh hangs from. Returns the distance left
	 * (pixels): non-zero only near or past full stretch (the leg then points at the target).
	 */
	public static double solveLeg(double[] parent, Joint thigh, Joint shin, Joint foot, double[] target) {
		// into the parent's frame, where the leg's plane is x = const and its joints turn in (y, z)
		double[] t = Affine.apply(Affine.invertRigid(parent), target, new double[3]);
		double[] tp = thigh.pivot, sp = add(shin.pivot, shin.pos), ap = add(foot.pivot, foot.pos);
		// rest vectors (in the thigh's and shin's own frames), as (y, z)
		double u1y = sp[1] - tp[1], u1z = sp[2] - tp[2], u2y = ap[1] - sp[1], u2z = ap[2] - sp[2];
		double l1 = Math.hypot(u1y, u1z), l2 = Math.hypot(u2y, u2z);
		double a0 = Math.toRadians(thigh.rot[0]), b0 = Math.toRadians(shin.rot[0]);
		double ty = t[1] - (tp[1] + thigh.pos[1]), tz = t[2] - (tp[2] + thigh.pos[2]);
		double d = Math.hypot(ty, tz);
		double reach = Math.max(Math.abs(l1 - l2) + 1e-3, soften(d, l1 + l2));
		// the knee: the angle between the two bones, the way the animation already bends it
		double phi0 = angle(u2y, u2z) - angle(u1y, u1z);
		double cos = (reach * reach - l1 * l1 - l2 * l2) / (2 * l1 * l2);
		double bend = Math.acos(Math.max(-1.0, Math.min(1.0, cos)));
		double now = wrap(phi0 + b0);
		double b = (now >= 0 ? bend : -bend) - phi0;
		// the thigh: turn the whole leg, as bent, onto the target's direction
		double vy = u1y + (u2y * Math.cos(b) - u2z * Math.sin(b)), vz = u1z + (u2y * Math.sin(b) + u2z * Math.cos(b));
		double a = angle(ty, tz) - angle(vy, vz);
		a = a0 + wrap(a - a0);
		b = b0 + wrap(b - b0);
		double turned = Math.toDegrees((a - a0) + (b - b0));
		thigh.rot[0] = Math.toDegrees(a);
		shin.rot[0] = Math.toDegrees(b);
		foot.rot[0] -= turned;
		return Math.abs(d - reach);
	}

	/**
	 * Turns the shoulder about Z (at most {@link #MAX_SHOULDER} either way) so {@code contact} -- a point
	 * the wing carries, given in the shoulder's local frame -- comes to height {@code targetY} in the
	 * model. Returns the height left to go (pixels).
	 */
	public static double solveWing(double[] parent, Joint shoulder, double[] contact, double targetY) {
		double z0 = shoulder.rot[2];
		double[] p = new double[3];
		java.util.function.DoubleUnaryOperator height = dz -> {
			double[] m = Affine.mul(parent, shoulder.local(shoulder.rot[0], shoulder.rot[1], z0 + dz));
			return Affine.apply(m, contact, p)[1] - targetY;
		};
		// Newton steps from the animated angle: the height is smooth and monotone over this range
		double dz = 0.0, f = height.applyAsDouble(dz);
		for (int i = 0; i < 8 && Math.abs(f) > 1e-3; i++) {
			double slope = (height.applyAsDouble(dz + 0.01) - f) / 0.01;
			if (Math.abs(slope) < 1e-6) break;
			double next = Math.max(-MAX_SHOULDER, Math.min(MAX_SHOULDER, dz - f / slope));
			if (next == dz) break;
			dz = next;
			f = height.applyAsDouble(dz);
		}
		shoulder.rot[2] = z0 + dz;
		return Math.abs(f);
	}

	/**
	 * As {@link #solveLeg}, the ankle also carried sideways: the thigh first swings out or in at the hip
	 * (about Z, at most {@link #MAX_SPLAY}) so that the leg's plane passes through {@code target}, then
	 * the leg bends in that plane. For a foot stepping round in a turn ({@link TurnSteps}).
	 */
	public static double solveLegReach(double[] parent, Joint thigh, Joint shin, Joint foot, double[] target) {
		double[] hip = add(thigh.pivot, thigh.pos);
		double[] inParent = Affine.apply(Affine.invertRigid(parent), target, new double[3]);
		// how far out from the hip the leg's plane lies now, as animated
		double[] ankleRest = add(foot.pivot, foot.pos);
		double[] ankle = Affine.apply(Affine.mul(thigh.local(), shin.local()), ankleRest, new double[3]);
		double c = ankle[0] - hip[0];
		double dx = inParent[0] - hip[0], dy = inParent[1] - hip[1];
		double r = Math.hypot(dx, dy);
		double splay = 0.0;
		if (r > Math.abs(c) + 1e-6) {
			// turned back by the splay the target lies in the plane: dx cos t + dy sin t = c
			double phi = Math.atan2(dy, dx), a = Math.acos(c / r);
			double t1 = wrap(phi - a), t2 = wrap(phi + a);
			splay = Math.abs(t1) < Math.abs(t2) ? t1 : t2;
			splay = Math.max(-Math.toRadians(MAX_SPLAY), Math.min(Math.toRadians(MAX_SPLAY), splay));
		}
		Joint turn = new Joint();
		System.arraycopy(hip, 0, turn.pivot, 0, 3);
		turn.rot[2] = Math.toDegrees(splay);
		double left = solveLeg(Affine.mul(parent, turn.local()), thigh, shin, foot, target);
		thigh.rot[2] += turn.rot[2];
		return left;
	}

	/**
	 * The whole front limb onto a point, as {@code tools/walk.py} solves it offline: the shoulder turns
	 * about all three axes (each at most {@link #MAX_SHOULDER} from the animation) and the elbow about Z
	 * only (at most {@link #MAX_ELBOW}), so {@code contact} -- a point the hand carries, in the elbow's
	 * local frame -- goes to {@code target} (model). The fold beyond the elbow is untouched (the wing
	 * rule). Four angles for three coordinates: damped least squares takes the least change from the
	 * animation, so a planted wrist stays where it stands, in, out, forward or back, while the body turns
	 * over it. Returns the distance left (pixels).
	 */
	public static double solveArmReach(double[] parent, Joint shoulder, Joint elbow, double[] contact, double[] target) {
		// A straight arm (the standing pose) is singular: bending the elbow does not shorten it at first,
		// so the solve would never bend it. Bent starts either way find the reach in (walk.py does the same).
		double[] start = {shoulder.rot[0], shoulder.rot[1], shoulder.rot[2], elbow.rot[2]};
		double[] best = null;
		double bestLeft = Double.MAX_VALUE, bestChange = Double.MAX_VALUE;
		for (double seed : ELBOW_SEEDS) {
			double left = armReach(parent, shoulder, elbow, contact, target, start, seed);
			double change = 0.0;
			for (int a = 0; a < 3; a++) change += Math.abs(shoulder.rot[a] - start[a]);
			change += Math.abs(elbow.rot[2] - start[3]);
			// the nearest miss; among those that reach, the least change from the animation
			boolean better = left < bestLeft - 0.05 || (left < bestLeft + 0.05 && change < bestChange);
			if (better) {
				best = new double[]{shoulder.rot[0], shoulder.rot[1], shoulder.rot[2], elbow.rot[2]};
				bestLeft = left;
				bestChange = change;
			}
			if (seed == 0.0 && left < 1e-2) break;
		}
		if (best == null) {
			// no finite answer (a NaN target): the animation's pose, unreached
			System.arraycopy(start, 0, shoulder.rot, 0, 3);
			elbow.rot[2] = start[3];
			return Double.POSITIVE_INFINITY;
		}
		System.arraycopy(best, 0, shoulder.rot, 0, 3);
		elbow.rot[2] = best[3];
		return bestLeft;
	}

	/** Bent-elbow starts tried by {@link #solveArmReach}, degrees from the animation. */
	private static final double[] ELBOW_SEEDS = {0.0, 15.0, -15.0};

	private static double armReach(double[] parent, Joint shoulder, Joint elbow, double[] contact, double[] target, double[] start, double seed) {
		double[] base = start;
		double[] limit = {MAX_SHOULDER, MAX_SHOULDER, MAX_SHOULDER, MAX_ELBOW};
		double[] d = {0.0, 0.0, 0.0, seed}, r = new double[3], ri = new double[3], p = new double[3];
		double[][] j = new double[3][4];
		armResidual(parent, shoulder, elbow, contact, target, base, d, p, r);
		for (int it = 0; it < 16 && norm(r) > 1e-3; it++) {
			for (int a = 0; a < 4; a++) {
				d[a] += 0.01;
				armResidual(parent, shoulder, elbow, contact, target, base, d, p, ri);
				d[a] -= 0.01;
				for (int k = 0; k < 3; k++) j[k][a] = (ri[k] - r[k]) / 0.01;
			}
			// (J^T J + l I) step = -J^T r: the smallest turn that does it
			double[][] m = new double[4][5];
			for (int a = 0; a < 4; a++) {
				for (int b = 0; b < 4; b++) {
					double sum = a == b ? 1e-2 : 0.0;
					for (int k = 0; k < 3; k++) sum += j[k][a] * j[k][b];
					m[a][b] = sum;
				}
				double g = 0.0;
				for (int k = 0; k < 3; k++) g += j[k][a] * r[k];
				m[a][4] = -g;
			}
			double[] step = solve(m);
			if (step == null) break;
			double moved = 0.0;
			for (int a = 0; a < 4; a++) {
				double next = Math.max(-limit[a], Math.min(limit[a], d[a] + step[a]));
				moved += Math.abs(next - d[a]);
				d[a] = next;
			}
			armResidual(parent, shoulder, elbow, contact, target, base, d, p, r);
			if (moved < 1e-6) break;
		}
		for (int a = 0; a < 3; a++) shoulder.rot[a] = base[a] + d[a];
		elbow.rot[2] = base[3] + d[3];
		return norm(r);
	}

	private static void armResidual(double[] parent, Joint shoulder, Joint elbow, double[] contact, double[] target,
			double[] base, double[] d, double[] p, double[] out) {
		double[] m = Affine.mul(Affine.mul(parent, shoulder.local(base[0] + d[0], base[1] + d[1], base[2] + d[2])),
				elbow.local(elbow.rot[0], elbow.rot[1], base[3] + d[3]));
		Affine.apply(m, contact, p);
		for (int k = 0; k < 3; k++) out[k] = p[k] - target[k];
	}

	private static double norm(double[] v) {
		return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
	}

	/** Gaussian elimination of an n x n system given as rows {coefficients..., rhs}; null when singular. */
	private static double[] solve(double[][] m) {
		int n = m.length;
		for (int c = 0; c < n; c++) {
			int pivot = c;
			for (int r = c + 1; r < n; r++) if (Math.abs(m[r][c]) > Math.abs(m[pivot][c])) pivot = r;
			if (Math.abs(m[pivot][c]) < 1e-12) return null;
			double[] t = m[c];
			m[c] = m[pivot];
			m[pivot] = t;
			for (int r = 0; r < n; r++) {
				if (r == c) continue;
				double f = m[r][c] / m[c][c];
				for (int k = c; k <= n; k++) m[r][k] -= f * m[c][k];
			}
		}
		double[] x = new double[n];
		for (int i = 0; i < n; i++) x[i] = m[i][n] / m[i][i];
		return x;
	}

	/** {@code d} past the soft limit eased toward {@code full} without ever reaching it. */
	static double soften(double d, double full) {
		double soft = SOFT_REACH * full, room = full - soft;
		if (d <= soft) return d;
		return soft + room * (1.0 - Math.exp(-(d - soft) / room)) * 0.999;
	}

	private static double[] add(double[] a, double[] b) {
		return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
	}

	/** Angle of (y, z) the way a rotation about X turns it: R(t) takes angle a to a + t. */
	private static double angle(double y, double z) {
		return Math.atan2(z, y);
	}

	private static double wrap(double r) {
		return Math.IEEEremainder(r, 2 * Math.PI);
	}
}
