package crazylimits.dragonsworn.attack;

import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.math.Angles;

/**
 * The stream breath: the perched dragon inhales, opens its jaw and pours void flame along the ground in
 * front, the neck stretched straight at its target and following it, the body turning only when
 * the target leaves the neck's reach ({@link #bodyTurns}) (the aim: see
 * {@code BreathStreamPhase} and {@code body/Strike}). Game-free (timing, aim, the stream's shape), so it is unit
 * tested; the bridge's {@code BreathStreamPhase} runs it on the vanilla dragon.
 *
 * <p>The timing mirrors {@code breath} in {@code tools/anims.py} (inhale 2.0 s, stream 3.0 s, recover
 * 0.8 s). While it inhales the dragon heats up ({@link #heat}): its chest starts to glow, the glow climbs
 * the throat and lights the jaw, and the fire comes. The mouth is where the rendered model's mouth is in that pose (measured in game: the renderer's
 * procedural neck bends move it from the keyframed pose). The stream sweeps: it starts steep, landing
 * ~13.5 blocks ahead, and lifts until it lands ~19 blocks ahead. The client aims the flames from the
 * model's mouth at the point this axis hits, so what burns is where the flames land.
 */
public final class BreathAttack {
	public static final int WINDUP_TICKS = 40;
	public static final int STREAM_TICKS = 60;
	public static final int RECOVER_TICKS = 16;
	public static final int TOTAL_TICKS = WINDUP_TICKS + STREAM_TICKS + RECOVER_TICKS;

	/**
	 * Mouth in the stream pose, blocks from the entity's origin: up, and forward along its facing. The
	 * head is low, at the chest's height, on a straight neck ({@code breath} in {@code tools/anims.py}:
	 * 49.6 px up, 120 px ahead), plus the 0.2 forward the renderer's neck bends add.
	 */
	public static final double MOUTH_UP = 3.1, MOUTH_FORWARD = 7.7;
	/** The stream's pitch below level at the start and at the end of the stream: it sweeps outward. */
	public static final double PITCH_START = 28.0, PITCH_END = 15.3;

	/** How far the flames carry along their axis before they die out. */
	public static final double RANGE = 18.0;
	/** The stream's radius at the mouth, and how much it widens per block. */
	public static final double MOUTH_RADIUS = 0.6, SPREAD = 0.16;
	/** Where the stream meets a block it splashes: everything this close to the impact burns too. */
	public static final double SPLASH_RADIUS = 3.0;
	public static final int DAMAGE_INTERVAL = 10;

	/** Degrees per tick the dragon turns after its target: brisk while it inhales, slow while it pours. */
	public static final float WINDUP_TURN = 3.0F, STREAM_TURN = 1.2F;
	/**
	 * The neck aims the breath by itself (body/Strike straightens it onto the aim): the body stays put
	 * while the target is within {@link #NECK_ARC} degrees of its facing. Further off, the body turns
	 * after it, until the target is back within {@link #SETTLED_ARC}, and stops there.
	 */
	public static final double NECK_ARC = 40.0, SETTLED_ARC = 15.0;
	/** Blocks per tick the stream's aim follows its target while it pours (a sprinting player makes 0.28). */
	public static final double AIM_SPEED = 0.2;

	private BreathAttack() {}

	public static boolean streaming(int tick) {
		return tick >= WINDUP_TICKS && tick < WINDUP_TICKS + STREAM_TICKS;
	}

	/** The last part of the inhale, when the heat reaches the jaw and embers flicker in the mouth. */
	public static boolean glowing(int tick) {
		return tick >= WINDUP_TICKS * 3 / 4 && tick < WINDUP_TICKS;
	}

	/**
	 * How far the heat has climbed at {@code tick} (fractional) of the phase, 0-1 over the inhale. The
	 * heat glow textures ({@code tools/heat.py}) light the chest at 0-0.37, the throat at 0.3-0.8 and the
	 * jaw at 0.68-1: at 1 the fire comes.
	 */
	public static double heat(double tick) {
		return Math.max(0.0, Math.min(1.0, tick / WINDUP_TICKS));
	}

	/** How bright the heat glows at {@code tick}: full while inhaling, flickering while it pours, fading over the recovery. */
	public static double heatBrightness(double tick) {
		return brightness(tick, WINDUP_TICKS, STREAM_TICKS, RECOVER_TICKS);
	}

	/** {@link #heatBrightness} for a breath of these lengths (the pass's too, {@link BreathPass}). */
	static double brightness(double tick, int windup, int stream, int recover) {
		if (tick < windup) return 1.0;
		double flicker = 0.88 + 0.06 * Math.sin(tick * 1.3) + 0.06 * Math.sin(tick * 3.1);
		double cool = (tick - windup - stream) / recover;
		return flicker * (1.0 - Math.max(0.0, Math.min(1.0, cool)));
	}

	/**
	 * A fireball comes out of the same heat: the inhale's glow plays {@link #FIREBALL_SPEEDUP} times
	 * faster, the fireball flies once it reaches the jaw ({@link #FIREBALL_WINDUP_TICKS}) and the glow
	 * cools over {@link #FIREBALL_COOL_TICKS}.
	 */
	public static final int FIREBALL_SPEEDUP = 3;
	public static final int FIREBALL_WINDUP_TICKS = (WINDUP_TICKS + FIREBALL_SPEEDUP - 1) / FIREBALL_SPEEDUP;
	public static final int FIREBALL_COOL_TICKS = RECOVER_TICKS / FIREBALL_SPEEDUP;
	/**
	 * Before the windup the head turns to the target; the dragon heats up only once the head points within
	 * {@link #FIREBALL_CONE} degrees of it (never backwards over its own body), turning for up to
	 * {@link #FIREBALL_AIM_TICKS}, else the shot is dropped before any glow.
	 */
	public static final double FIREBALL_CONE = 25.0;
	public static final int FIREBALL_AIM_TICKS = 20;
	/**
	 * Once heated it fires at the target if that is within {@link #FIREBALL_FIRE_CONE} degrees of the head's
	 * line, else down the line. It leads a moving target: the fireball covers about {@link #FIREBALL_SPEED}
	 * blocks a tick on average (vanilla's accelerates from 0.1 toward 1.9), led for at most
	 * {@link #FIREBALL_LEAD_TICKS}.
	 */
	public static final double FIREBALL_FIRE_CONE = 75.0;
	public static final double FIREBALL_SPEED = 1.0;
	public static final int FIREBALL_LEAD_TICKS = 40;

	/** {@link #heat} for a fireball, {@code tick} (fractional) from the start of its windup. */
	public static double fireballHeat(double tick) {
		return heat(tick * FIREBALL_SPEEDUP);
	}

	/** How bright a fireball's heat glows: full until the shot, then cooling off. */
	public static double fireballHeatBrightness(double tick) {
		double cool = (tick - FIREBALL_WINDUP_TICKS) / FIREBALL_COOL_TICKS;
		return 1.0 - Math.max(0.0, Math.min(1.0, cool));
	}

	/** Whether a perched dragon about to attack picks the stream; {@code roll} is uniform in [0, 1). */
	public static boolean chooseStream(double roll, int streamsThisLanding) {
		return streamsThisLanding < DragonConfig.MAX_STREAMS.get() && roll < DragonConfig.STREAM_CHANCE.get();
	}

	/** The dragon's facing as a unit vector. Vanilla dragon yaw: the head points along (sin, 0, -cos). */
	public static double[] facing(double yawDegrees) {
		double yaw = Math.toRadians(yawDegrees);
		return new double[] {Math.sin(yaw), 0.0, -Math.cos(yaw)};
	}

	/** The mouth, relative to the entity's origin, in the stream pose. */
	public static double[] mouth(double yawDegrees) {
		double[] f = facing(yawDegrees);
		return new double[] {f[0] * MOUTH_FORWARD, MOUTH_UP, f[2] * MOUTH_FORWARD};
	}

	/** Degrees below level the stream points at {@code tick} of the phase. */
	public static double pitchAt(int tick) {
		double k = Math.max(0.0, Math.min(1.0, (tick - WINDUP_TICKS) / (double) (STREAM_TICKS - 1)));
		return PITCH_START + (PITCH_END - PITCH_START) * k;
	}

	/** The stream's axis at {@code tick}: the facing, pitched down. */
	public static double[] direction(double yawDegrees, int tick) {
		double[] f = facing(yawDegrees);
		double pitch = Math.toRadians(pitchAt(tick)), c = Math.cos(pitch);
		return new double[] {f[0] * c, -Math.sin(pitch), f[2] * c};
	}

	/**
	 * Whether a body at {@code point} (radius {@code bodyRadius}) is in the stream that leaves
	 * {@code origin} along the unit vector {@code dir} and stops after {@code length} blocks.
	 */
	public static boolean inStream(double[] origin, double[] dir, double length, double[] point, double bodyRadius) {
		double dx = point[0] - origin[0], dy = point[1] - origin[1], dz = point[2] - origin[2];
		double along = dx * dir[0] + dy * dir[1] + dz * dir[2];
		if (along < 0.0 || along > length + bodyRadius) return false;
		double clamped = Math.min(along, length);
		double ox = dx - dir[0] * clamped, oy = dy - dir[1] * clamped, oz = dz - dir[2] * clamped;
		double reach = MOUTH_RADIUS + SPREAD * clamped + bodyRadius;
		return ox * ox + oy * oy + oz * oz <= reach * reach;
	}

	/** Vanilla dragon yaw that faces along (dx, dz). */
	public static float yawToward(double dx, double dz) {
		return (float) Math.toDegrees(Math.atan2(dx, -dz));
	}

	/**
	 * Whether the body turns this tick after a target {@code off} degrees off its facing: it starts once
	 * the target leaves the neck's arc and keeps going ({@code turning}) until it is nearly faced again.
	 */
	public static boolean bodyTurns(double off, boolean turning) {
		double a = Math.abs(off);
		return turning ? a > SETTLED_ARC : a > NECK_ARC;
	}

	/** Degrees the target at (dx, dz) is off the facing {@code yaw}, right positive. */
	public static float offFacing(float yaw, double dx, double dz) {
		return Angles.wrapDegrees(yawToward(dx, dz) - yaw);
	}

	/** {@code yaw} turned toward (dx, dz) by at most {@code maxStep} degrees, the short way round. */
	public static float turnToward(float yaw, double dx, double dz, float maxStep) {
		float delta = Angles.wrapDegrees(yawToward(dx, dz) - yaw);
		return yaw + Math.max(-maxStep, Math.min(maxStep, delta));
	}
}
