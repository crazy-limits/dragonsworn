package crazylimits.dragonsworn.attack;

import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.math.Maths;

/**
 * The breath pass: a flying dragon lines up on its prey, glides over it head down and pours void flame
 * onto the ground under its flight path ({@link DragonAnim#GLIDE_BREATH}), then flies on. Game-free
 * (timing, aim, when to start), so it is unit tested; the bridge's {@code BreathPassPhase} runs it.
 *
 * <p>The timing mirrors {@code glide_breath} in {@code tools/anims.py} (inhale 1.2 s, stream 2.4 s,
 * recover 0.8 s); the inhale heats the dragon up as the perched breath does ({@link #heat}: the same glow
 * frames, played faster). The stream is aimed by direction from the neck's base: a cone ahead and below
 * ({@link #PITCH_MIN}..{@link #PITCH_MAX} below level, {@link #YAW_ARC} either side). The aim swings after
 * the prey no faster than {@link #STREAM_TURN} degrees a tick, so the flames rake the ground along the
 * flight path, through the prey, and on ahead once it is behind (never back under the body).
 */
public final class BreathPass {
	public static final int WINDUP_TICKS = 24;
	public static final int STREAM_TICKS = 48;
	public static final int RECOVER_TICKS = 16;
	public static final int TOTAL_TICKS = WINDUP_TICKS + STREAM_TICKS + RECOVER_TICKS;
	/** The pose's frame the neck is straightened on: the middle of the stream (seconds). */
	public static final double AIM_SECONDS = (WINDUP_TICKS + STREAM_TICKS / 2.0) / 20.0;
	/** Seconds before the fire the neck swings down ({@code anims.PASS_LUNGE}), and over how long. */
	public static final double LUNGE_SECONDS = 0.5, LUNGE_LENGTH = 0.3;

	/** Height (blocks) over the prey's feet, or the ground under it when that is higher, it passes at. */
	public static final double HEIGHT = 11.0;
	/** It swings out at least this far (horizontal blocks) for its run before turning in. */
	public static final double RUN_UP = 44.0;
	/** The inhale starts this far short of the prey (horizontal blocks), the prey within {@link #LINE_UP} degrees of its facing. */
	public static final double START_DISTANCE = 34.0, LINE_UP = 20.0;
	/** Glide speed it holds over the pass, blocks per tick: at most, and at least. */
	public static final double MAX_SPEED = 0.95, MIN_SPEED = 0.6;
	/** It never starts a pass at prey further than this. */
	public static final double MAX_RANGE = 96.0;

	/** The neck's base, blocks from the entity's origin in the glide: up, and forward along its facing. */
	public static final double NECK_UP = 3.7, NECK_FORWARD = 1.8;
	/** The aim's cone, degrees: below level, and either side of the facing; where it rests with no prey. */
	public static final double PITCH_MIN = 25.0, PITCH_MAX = 80.0, YAW_ARC = 40.0, PITCH_REST = 50.0;
	/** Degrees per tick the aim swings after the prey: brisk while it inhales, slower while it pours. */
	public static final double WINDUP_TURN = 5.0, STREAM_TURN = 2.2;
	/** How far the flames carry from the neck's base along the aim. */
	public static final double RANGE = 22.0;
	public static final int DAMAGE_INTERVAL = 4;

	private BreathPass() {}

	public static boolean streaming(int tick) {
		return tick >= WINDUP_TICKS && tick < WINDUP_TICKS + STREAM_TICKS;
	}

	/** The last part of the inhale, when the heat reaches the jaw and embers flicker in the mouth. */
	public static boolean glowing(int tick) {
		return tick >= WINDUP_TICKS * 3 / 4 && tick < WINDUP_TICKS;
	}

	/** How far the heat has climbed at {@code tick} (fractional): 0-1 over the inhale (see {@link BreathAttack#heat}). */
	public static double heat(double tick) {
		return Math.max(0.0, Math.min(1.0, tick / WINDUP_TICKS));
	}

	/** How bright the heat glows: full while inhaling, flickering while it pours, fading over the recovery. */
	public static double heatBrightness(double tick) {
		return BreathAttack.brightness(tick, WINDUP_TICKS, STREAM_TICKS, RECOVER_TICKS);
	}

	/**
	 * Whether the pass starts now: the prey at (dx, dz) from the dragon is ahead within
	 * {@link #START_DISTANCE} (but not already under it) and within {@link #LINE_UP} degrees of the facing.
	 */
	public static boolean linedUp(float yaw, double dx, double dz) {
		double d = Math.hypot(dx, dz);
		return d <= START_DISTANCE && d > START_DISTANCE * 0.4 && Math.abs(BreathAttack.offFacing(yaw, dx, dz)) <= LINE_UP;
	}

	/**
	 * The aim's angles toward the point (dx, dy, dz) from the neck's base, clamped into the cone:
	 * {degrees off the facing {@code yaw}, right positive; degrees below level}. A point behind is
	 * clamped to the cone's edge nearest to it, never past the side of the facing.
	 */
	public static double[] angles(float yaw, double dx, double dy, double dz) {
		double off = BreathAttack.offFacing(yaw, dx, dz);
		double down = Math.toDegrees(Math.atan2(-dy, Math.hypot(dx, dz)));
		// behind the neck (under or past the body): the steepest it pours, straight on
		if (Math.abs(off) > 90.0) return new double[] {0.0, PITCH_MAX};
		return new double[] {Maths.clampAbs(off, YAW_ARC), Math.max(PITCH_MIN, Math.min(PITCH_MAX, down))};
	}

	/** {@code at} swung toward {@code want} by at most {@code step} degrees on each angle. */
	public static double[] chase(double[] at, double[] want, double step) {
		return new double[] {at[0] + Maths.clampAbs(want[0] - at[0], step), at[1] + Maths.clampAbs(want[1] - at[1], step)};
	}

	/** The unit direction of the aim's angles from the facing {@code yaw} (vanilla's: the head along (sin, 0, -cos)). */
	public static double[] direction(float yaw, double[] angles) {
		double heading = Math.toRadians(yaw + angles[0]), down = Math.toRadians(angles[1]), c = Math.cos(down);
		return new double[] {Math.sin(heading) * c, -Math.sin(down), -Math.cos(heading) * c};
	}

	/** The neck's base, relative to the entity's origin, for the facing {@code yaw}. */
	public static double[] neckBase(float yaw) {
		double[] f = BreathAttack.facing(yaw);
		return new double[] {f[0] * NECK_FORWARD, NECK_UP, f[2] * NECK_FORWARD};
	}
}
