package crazylimits.dragonsworn.limb;

/**
 * The head turning to what the dragon pays attention to (its prey, a player close by): the turn is
 * shared down the neck, base least and head most, the way a long neck carries the head round, and the
 * tail swings a little the other way for balance. It eases in and out; a target behind the dragon is let
 * go (the body turns for that), and so is everything while a bite, a roar or the breath aims the head
 * itself. A tail strike keeps the eyes on its target, but the tail lets its balancing swing go: the
 * strike aims the tail.
 *
 * <p>Angles are degrees from where the animation points the head: yaw positive to the left (a bone's
 * +Y turns its front left), pitch positive up. The outputs are additions to the bones' rotations.
 *
 * <p>Updated once a game tick on both sides (the hitboxes turn with the head); the renderer reads it
 * between ticks with a partial tick.
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

	private double yaw, pitch, weight, balance = 1.0, last = Double.NaN;
	/** The values before the last update, for reading between updates. */
	private double prevYaw, prevPitch, prevWeight, prevBalance = 1.0;

	/**
	 * Advances to {@code time} (ticks). {@code wantYaw}, {@code wantPitch}: where the target is from the
	 * eyes, against the animated head; {@code attention} 0..1 (0: nothing to look at, or the animation has the head).
	 */
	public void update(double time, double wantYaw, double wantPitch, double attention) {
		update(time, wantYaw, wantPitch, attention, true);
	}

	/** {@code tailFree}: false while something else (a tail strike) aims the tail; its counter-swing fades out. */
	public void update(double time, double wantYaw, double wantPitch, double attention, boolean tailFree) {
		double dt = Double.isNaN(last) ? 1.0 : Math.max(0.0, Math.min(5.0, time - last));
		last = time;
		prevYaw = yaw;
		prevPitch = pitch;
		prevWeight = weight;
		prevBalance = balance;
		if (Double.isNaN(wantYaw) || Double.isNaN(wantPitch) || Math.abs(wantYaw) > GIVE_UP) attention = 0.0;
		double follow = 1.0 - Math.pow(1.0 - FOLLOW, dt), fade = 1.0 - Math.pow(1.0 - FADE, dt);
		if (attention > 0.0) {
			yaw += (Math.max(-MAX_YAW, Math.min(MAX_YAW, wantYaw)) - yaw) * follow;
			pitch += (Math.max(-MAX_DOWN, Math.min(MAX_UP, wantPitch)) - pitch) * follow;
		}
		weight += (attention - weight) * fade;
		balance += ((tailFree ? 1.0 : 0.0) - balance) * fade;
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
		return balance * weight * yaw * TAIL_COUNTER / segments;
	}

	/** As {@link #neckYaw(int)}, {@code partial} of the way from before the last update to after it. */
	public double neckYaw(int segment, double partial) {
		return yaw(partial) * NECK_SHARE[segment];
	}

	public double neckPitch(int segment, double partial) {
		return pitch(partial) * NECK_SHARE[segment];
	}

	public double headYaw(double partial) {
		return yaw(partial) * HEAD_SHARE;
	}

	public double headPitch(double partial) {
		return pitch(partial) * HEAD_SHARE;
	}

	public double tailYaw(int segments, double partial) {
		return lerp(prevBalance, balance, partial) * yaw(partial) * TAIL_COUNTER / segments;
	}

	/** The head's whole turn, between the last two updates. */
	public double yaw(double partial) {
		return lerp(prevWeight, weight, partial) * lerp(prevYaw, yaw, partial);
	}

	public double pitch(double partial) {
		return lerp(prevWeight, weight, partial) * lerp(prevPitch, pitch, partial);
	}

	private static double lerp(double a, double b, double k) {
		return a + (b - a) * k;
	}

	/** The head's whole turn now (all shares together). */
	public double yaw() {
		return weight * yaw;
	}

	public double pitch() {
		return weight * pitch;
	}
}
