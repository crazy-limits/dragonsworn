package crazylimits.dragonsworn.limb;

import crazylimits.dragonsworn.math.Maths;

/**
 * How the body sits over uneven ground: from the ground's height under the four feet it pitches
 * (front higher: nose up), rolls (left higher: right side down) and rises or sinks, each partly and
 * gently, the way an animal keeps its body nearer level than the slope and lets the legs take up the
 * rest (see {@link LimbIK}). Ticked on both sides, so hitboxes follow the body as drawn.
 *
 * <p>The height is eased in the world, not relative to the dragon's position: the position steps a
 * whole block at a time as the dragon walks up or down (it keeps to the ground under it), and the body
 * must not jump with it.
 */
public final class GroundFit {
	/** Where the feet stand, model blocks (x, z): left hind, right hind, left front, right front. */
	public static final double[][] FOOTPRINT = {{-1.0, 1.3}, {1.0, 1.3}, {-3.8, -3.7}, {3.8, -3.7}};
	/** How much of the slope the body takes, front to back and side to side. */
	static final double PITCH_FOLLOW = 0.7, ROLL_FOLLOW = 0.5;
	public static final double MAX_PITCH = 22.0, MAX_ROLL = 12.0, MIN_LIFT = -2.0, MAX_LIFT = 1.0;
	private static final double LENGTH = FOOTPRINT[0][1] - FOOTPRINT[2][1];
	private static final double WIDTH = (FOOTPRINT[1][0] - FOOTPRINT[0][0] + FOOTPRINT[3][0] - FOOTPRINT[2][0]) / 2.0;
	private static final double RATE = 0.25;

	private double pitch, roll, lift, prevPitch, prevRoll, prevLift;
	/** The body's eased height in the world (blocks), NaN before the first tick on the ground. */
	private double height = Double.NaN;

	/**
	 * One tick. {@code heights}: the ground under each foot relative to the dragon's position {@code y}
	 * (blocks; NaN where there is none). Off the ground ({@code grounded} false) everything eases back
	 * to zero.
	 */
	public void tick(boolean grounded, double[] heights, double y) {
		prevPitch = pitch;
		prevRoll = roll;
		prevLift = lift;
		double tp = 0.0, tr = 0.0, tl = 0.0;
		if (grounded) {
			double[] h = new double[4];
			double sum = 0.0;
			int n = 0;
			for (int i = 0; i < 4; i++) {
				if (Double.isNaN(heights[i])) continue;
				sum += heights[i];
				n++;
			}
			double mean = n > 0 ? sum / n : 0.0;
			// no ground under a foot (a ledge): take it as level with the others
			for (int i = 0; i < 4; i++) h[i] = Double.isNaN(heights[i]) ? mean : heights[i];
			double hind = (h[0] + h[1]) / 2.0, front = (h[2] + h[3]) / 2.0, left = (h[0] + h[2]) / 2.0, right = (h[1] + h[3]) / 2.0;
			tp = Maths.clampAbs(PITCH_FOLLOW * Math.toDegrees(Math.atan2(front - hind, LENGTH)), MAX_PITCH);
			tr = Maths.clampAbs(ROLL_FOLLOW * Math.toDegrees(Math.atan2(left - right, WIDTH)), MAX_ROLL);
			tl = Math.max(MIN_LIFT, Math.min(MAX_LIFT, mean));
		}
		pitch += (tp - pitch) * RATE;
		roll += (tr - roll) * RATE;
		// the body's height in the world eases toward where it should be; the lift is what puts it there
		// from this tick's position (the renderer blends position and lift between ticks alike)
		double target = y + tl;
		if (Double.isNaN(height)) height = y + lift;
		height += (target - height) * RATE;
		lift = Math.max(MIN_LIFT, Math.min(MAX_LIFT, height - y));
		height = y + lift;
	}

	/** Nose up positive, degrees. */
	public double pitch(float partialTick) {
		return prevPitch + (pitch - prevPitch) * partialTick;
	}

	/** Right side down positive, degrees. */
	public double roll(float partialTick) {
		return prevRoll + (roll - prevRoll) * partialTick;
	}

	/** How far the body is raised (negative: lowered) from the dragon's position, blocks. */
	public double lift(float partialTick) {
		return prevLift + (lift - prevLift) * partialTick;
	}
}
