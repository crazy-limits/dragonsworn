package crazylimits.dragonfall.anim;

/**
 * The stream breath: the perched dragon inhales, opens its jaw and pours void flame along the ground in
 * front, turning slowly after its target, the neck stretched straight at it (the aim: see
 * {@code BreathStreamPhase} and {@code body/Strike}). Game-free (timing, aim, the stream's shape), so it is unit
 * tested; the bridge's {@code BreathStreamPhase} runs it on the vanilla dragon.
 *
 * <p>The timing mirrors {@code breath} in {@code tools/anims.py} (inhale 1.0 s, stream 3.0 s, recover
 * 0.8 s). The mouth is where the rendered model's mouth is in that pose (measured in game: the renderer's
 * procedural neck bends move it from the keyframed pose). The stream sweeps: it starts steep, landing
 * ~13.5 blocks ahead, and lifts until it lands ~19 blocks ahead. The client aims the flames from the
 * model's mouth at the point this axis hits, so what burns is where the flames land.
 */
public final class BreathAttack {
	public static final int WINDUP_TICKS = 20;
	public static final int STREAM_TICKS = 60;
	public static final int RECOVER_TICKS = 16;
	public static final int TOTAL_TICKS = WINDUP_TICKS + STREAM_TICKS + RECOVER_TICKS;

	/** Mouth in the stream pose, blocks from the entity's origin: up, and forward along its facing. */
	public static final double MOUTH_UP = 5.1, MOUTH_FORWARD = 7.4;
	/** The stream's pitch below level at the start and at the end of the stream: it sweeps outward. */
	public static final double PITCH_START = 40.0, PITCH_END = 24.0;

	/** How far the flames carry along their axis before they die out. */
	public static final double RANGE = 18.0;
	/** The stream's radius at the mouth, and how much it widens per block. */
	public static final double MOUTH_RADIUS = 0.6, SPREAD = 0.16;
	/** Where the stream meets a block it splashes: everything this close to the impact burns too. */
	public static final double SPLASH_RADIUS = 3.0;
	public static final float DAMAGE = 5.0F;
	public static final int DAMAGE_INTERVAL = 10;

	/** Chance that a perched attack is the stream instead of vanilla's lingering breath cloud. */
	public static final double STREAM_CHANCE = 0.5;
	/** Streams per landing; after the last one the dragon takes off. */
	public static final int MAX_STREAMS = 2;
	/** Degrees per tick the dragon turns after its target: brisk while it inhales, slow while it pours. */
	public static final float WINDUP_TURN = 3.0F, STREAM_TURN = 1.2F;
	/** Blocks per tick the stream's aim follows its target while it pours (a sprinting player makes 0.28). */
	public static final double AIM_SPEED = 0.2;

	private BreathAttack() {}

	public static boolean streaming(int tick) {
		return tick >= WINDUP_TICKS && tick < WINDUP_TICKS + STREAM_TICKS;
	}

	/** The last part of the inhale, when the mouth starts to glow. */
	public static boolean glowing(int tick) {
		return tick >= WINDUP_TICKS / 2 && tick < WINDUP_TICKS;
	}

	/** Whether a perched dragon about to attack picks the stream; {@code roll} is uniform in [0, 1). */
	public static boolean chooseStream(double roll, int streamsThisLanding) {
		return streamsThisLanding < MAX_STREAMS && roll < STREAM_CHANCE;
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

	/** {@code yaw} turned toward (dx, dz) by at most {@code maxStep} degrees, the short way round. */
	public static float turnToward(float yaw, double dx, double dz, float maxStep) {
		float delta = wrapDegrees(yawToward(dx, dz) - yaw);
		return yaw + Math.max(-maxStep, Math.min(maxStep, delta));
	}

	static float wrapDegrees(float degrees) {
		float d = degrees % 360.0F;
		if (d >= 180.0F) d -= 360.0F;
		if (d < -180.0F) d += 360.0F;
		return d;
	}
}
