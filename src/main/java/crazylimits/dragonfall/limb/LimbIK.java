package crazylimits.dragonfall.limb;

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
