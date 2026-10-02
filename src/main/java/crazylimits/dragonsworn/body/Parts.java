package crazylimits.dragonsworn.body;

/**
 * Indices of the hitbox parts (the dragon's sub-entities, {@link PoseTrack}'s anchors), in the order
 * {@code tools/parts.py} lists them: vanilla's eight first, in vanilla's order (its code addresses them by
 * field), then the ones added after. Only the parts the code singles out are named here.
 */
public final class Parts {
	private Parts() {
	}

	/** The head (on jaw_upper): what bites, breathes, roars. */
	public static final int HEAD = 0;
	/** The neck: its upper part (neck_4) and its lower part (neck_2). */
	public static final int NECK_UPPER = 1, NECK_LOWER = 8;
	/** The torso: the chest (front) and the hips (back). */
	public static final int CHEST = 2, HIPS = 9;
	/** The tail: its root (tail_2) and its tip (tail_9). */
	public static final int TAIL_ROOT = 3, TAIL_TIP = 12;

	/** Whether {@code part} is the head or the neck. */
	public static boolean headOrNeck(int part) {
		return part == HEAD || part == NECK_UPPER || part == NECK_LOWER;
	}
}
