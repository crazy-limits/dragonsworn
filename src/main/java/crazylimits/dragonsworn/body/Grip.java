package crazylimits.dragonsworn.body;

/**
 * The dragon's two holds on its prey, game-free (the game carries the prey, see
 * {@code mc/PreyHold}):
 * <ul>
 *   <li><b>Talon</b> (in flight, the snatch): the right hind foot reaches down and closes round the prey's
 *       middle, an eagle's catch: the prey lies flat along the dragon's length, head forward, its back
 *       against the sole ({@link #talonPad}) of the foot (ankle at {@link #TALON_ANKLE}), which is held level
 *       and turned across it ({@link #TALON_YAW}), the toes curled down round it. While it swoops
 *       ({@link Hold#REACH}) both hind legs are thrown forward under the chest, toes spread
 *       ({@link #REACH_ANKLE}), as an eagle's are before the strike, and the right foot
 *       reaches out for the prey over the last {@link #REACH_NEAR} blocks.</li>
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
	/** Where the right ankle is thrown forward to as the dragon swoops (the left's is its mirror): ahead of the hips, under the chest. */
	public static final double[] REACH_ANKLE = {16.0, 3.0, -4.0};
	/** Blocks from its prey at which the right foot starts reaching out for it from {@link #REACH_ANKLE}. */
	public static final double REACH_NEAR = 8.0;
	/** Degrees added to the reaching feet's X: toes spread up, open for the catch. */
	public static final double REACH_SPREAD = 25.0;
	/**
	 * The gripping foot is held level (its sole flat in the world, whatever the body's pitch and roll) and turned
	 * this far about Y (degrees; negative turns its toes out to the dragon's right), so its
	 * toes point across the prey lying along the dragon and curl down round it, the back toe round its other side.
	 */
	public static final double TALON_YAW = -80.0;
	/**
	 * The middle of the hind foot's sole between its front and back knuckles, from the ankle (pixels, the
	 * foot's own frame: toes toward -z): where the prey's back is held (see {@code tools/build_wings.py}).
	 */
	public static final double[] TALON_PAD = {0.0, -3.0, -5.5};
	/** The held prey's middle is this far below the head's center, blocks: between the jaws. */
	public static final double JAW_BELOW = 0.35;
	/** How far the jaw stands open round the prey, degrees. */
	public static final double JAW_OPEN = 16.0;
	/** One side-to-side shake, ticks. */
	public static final double SHAKE_TICKS = 9.0;
	/** Shake per neck segment, base to head, degrees: the head end throws furthest. */
	private static final double[] SHAKE_YAW = {5.0, 8.0, 11.0, 13.0};
	private static final double SHAKE_PITCH = 4.0, SHAKE_ROLL = 18.0;
	/**
	 * The biggest prey (blocks): no wider than {@code MAX_WIDTH}, and no bulkier (width x width x height) than
	 * {@code MAX_BULK}. Players and every humanoid (an enderman is 1.04), cows (1.13), sheep and goats fit; a warden
	 * (0.9 wide but 2.9 tall: 2.35), a llama, a horse, an iron golem, a spider do not.
	 */
	public static final double MAX_WIDTH = 1.0, MAX_BULK = 1.5;

	private Grip() {}

	/** Whether a prey of this size (its full standing size, blocks) can be held in the talons or the jaws. */
	public static boolean fits(double width, double height) {
		return width <= MAX_WIDTH && width * width * height <= MAX_BULK;
	}

	/**
	 * Where the gripping foot holds the prey's back, from the ankle (pixels, model axes with the body level):
	 * {@link #TALON_PAD} turned by {@link #TALON_YAW}. The prey's middle is half its width below that.
	 */
	public static double[] talonPad() {
		double b = Math.toRadians(TALON_YAW), x = TALON_PAD[0], z = TALON_PAD[2];
		return new double[]{x * Math.cos(b) + z * Math.sin(b), TALON_PAD[1], -x * Math.sin(b) + z * Math.cos(b)};
	}

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
