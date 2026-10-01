package crazylimits.dragonfall.limb;

/**
 * The head turning to what the dragon pays attention to (its prey, a player close by): the turn is
 * shared down the neck, base least and head most, the way a long neck carries the head round, and the
 * tail swings a little the other way for balance. It eases in and out; a target behind the dragon is let
 * go (the body turns for that), and so is everything while an attack or a roar aims the head itself.
 *
 * <p>Angles are degrees from where the animation points the head: yaw positive to the left (a bone's
 * +Y turns its front left), pitch positive up. The outputs are additions to the bones' rotations.
 */
public final class HeadLook {
	public static final double MAX_YAW = 70.0, MAX_UP = 35.0, MAX_DOWN = 40.0;
	/** Further round than this the head does not try. */
	public static final double GIVE_UP = 120.0;
	/** Shares of the turn, neck base to tip, then the head (they sum to 1). */
	static final double[] NECK_SHARE = {0.10, 0.17, 0.23, 0.22};
	static final double HEAD_SHARE = 0.28;
	/** How much of the head's yaw the tail swings the other way, over all its segments. */
	static final double TAIL_COUNTER = 0.3;
	/** Per tick: how much of the way to the wanted angle the head goes, and the attention fades in. */
	private static final double FOLLOW = 0.18, FADE = 0.1;

	private double yaw, pitch, weight, last = Double.NaN;

	/**
	 * Advances to {@code time} (ticks). {@code wantYaw}, {@code wantPitch}: where the target is from the
	 * animated head; {@code attention} 0..1 (0: nothing to look at, or the animation has the head).
	 */
	public void update(double time, double wantYaw, double wantPitch, double attention) {
		double dt = Double.isNaN(last) ? 1.0 : Math.max(0.0, Math.min(5.0, time - last));
		last = time;
		if (Double.isNaN(wantYaw) || Double.isNaN(wantPitch) || Math.abs(wantYaw) > GIVE_UP) attention = 0.0;
		double follow = 1.0 - Math.pow(1.0 - FOLLOW, dt), fade = 1.0 - Math.pow(1.0 - FADE, dt);
		if (attention > 0.0) {
			yaw += (Math.max(-MAX_YAW, Math.min(MAX_YAW, wantYaw)) - yaw) * follow;
			pitch += (Math.max(-MAX_DOWN, Math.min(MAX_UP, wantPitch)) - pitch) * follow;
		}
		weight += (attention - weight) * fade;
	}

	public double neckYaw(int segment) {
		return weight * yaw * NECK_SHARE[segment];
	}

	public double neckPitch(int segment) {
		return weight * pitch * NECK_SHARE[segment];
	}

	public double headYaw() {
		return weight * yaw * HEAD_SHARE;
	}

	public double headPitch() {
		return weight * pitch * HEAD_SHARE;
	}

	/** Yaw for each of {@code segments} tail segments (a tail's +Y swings its tip right). */
	public double tailYaw(int segments) {
		return weight * yaw * TAIL_COUNTER / segments;
	}

	/** The head's whole turn now (all shares together). */
	public double yaw() {
		return weight * yaw;
	}

	public double pitch() {
		return weight * pitch;
	}
}
