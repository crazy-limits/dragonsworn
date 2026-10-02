package crazylimits.dragonfall.body;

/**
 * The dragon's two holds on its prey, game-free (the game rides the prey on the dragon, see
 * {@code mc/Grip}):
 * <ul>
 *   <li><b>Talon</b> (in flight, the snatch): the right hind foot reaches down and closes round the prey's
 *       middle, an eagle's catch: the prey lies flat along the dragon's length, head forward, its middle
 *       {@link #TALON_BELOW} under the ankle ({@link #TALON_ANKLE}), the toes curled down round it
 *       ({@link #TALON_CURL}). While it swoops ({@link Hold#REACH}) the leg already reaches for the prey.</li>
 *   <li><b>Jaw</b> (on the ground, the seize): the prey lies crosswise in the jaws, held by its middle
 *       ({@link #JAW_BELOW} under the head's center), the jaw a little open round it, and the neck shakes it
 *       from side to side ({@link #shakeYaw}), a terrier's shake.</li>
 * </ul>
 * Held prey is drawn lying flat, turned about its middle; its middle is where the hold is.
 * Model space as {@link PoseTrack}: pixels, y up, head toward -z, +x the dragon's right.
 */
public final class Grip {
	public enum Hold { NONE, REACH, TALON, JAW }

	/** Where the right ankle holds its prey: in the right leg's plane, under the hip and a little ahead of it. */
	public static final double[] TALON_ANKLE = {16.0, 8.0, 20.0};
	/** The held prey's middle is this far below the gripping ankle, blocks: the toes close round it. */
	public static final double TALON_BELOW = 0.45;
	/** Degrees added to the gripping foot's X: the toes curl down round the prey (-X tips the front down). */
	public static final double TALON_CURL = -70.0;
	/** The held prey's middle is this far below the head's center, blocks: between the jaws. */
	public static final double JAW_BELOW = 0.35;
	/** How far the jaw stands open round the prey, degrees. */
	public static final double JAW_OPEN = 16.0;
	/** One side-to-side shake, ticks. */
	public static final double SHAKE_TICKS = 9.0;
	/** Shake per neck segment, base to head, degrees: the head end throws furthest. */
	private static final double[] SHAKE_YAW = {5.0, 8.0, 11.0, 13.0};
	private static final double SHAKE_PITCH = 4.0, SHAKE_ROLL = 18.0;

	private Grip() {}

	/** One synced int: 0 for no hold, else the prey's entity id and the hold. */
	public static int encode(Hold hold, int entityId) {
		if (hold == Hold.NONE || entityId < 0) return 0;
		return (entityId + 1) << 2 | hold.ordinal();
	}

	public static Hold hold(int bits) {
		return bits == 0 ? Hold.NONE : Hold.values()[bits & 3];
	}

	/** The prey's entity id, -1 for none. */
	public static int entity(int bits) {
		return bits == 0 ? -1 : (bits >>> 2) - 1;
	}

	/**
	 * The shake's turn: a jerk to one side and back, not a smooth sway (a third harmonic sharpens the
	 * swings), in bursts that come and go. {@code ticks}: since the hold began; {@code weight} 0..1.
	 */
	static double wave(double ticks) {
		double a = 2.0 * Math.PI * ticks / SHAKE_TICKS;
		double burst = 0.7 + 0.3 * Math.sin(2.0 * Math.PI * ticks / 47.0);
		return burst * (Math.sin(a) + 0.25 * Math.sin(3.0 * a)) / 0.89;
	}

	/** Yaw of neck segment {@code segment} (0..3, base to head), degrees. */
	public static double shakeYaw(int segment, double ticks, double weight) {
		return weight * SHAKE_YAW[Math.min(segment, SHAKE_YAW.length - 1)] * wave(ticks);
	}

	/** The head bobs as it throws: twice per shake, degrees on every neck segment. */
	public static double shakePitch(double ticks, double weight) {
		return weight * SHAKE_PITCH * Math.sin(4.0 * Math.PI * ticks / SHAKE_TICKS);
	}

	/** The head rolls into each throw, degrees. */
	public static double shakeRoll(double ticks, double weight) {
		return weight * SHAKE_ROLL * wave(ticks - 1.0);
	}
}
