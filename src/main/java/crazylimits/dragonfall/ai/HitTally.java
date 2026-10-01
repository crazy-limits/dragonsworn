package crazylimits.dragonfall.ai;

/**
 * The hits a landed dragon takes, to know when it is being worn down from where it cannot answer (its
 * blind spots: under the chin, between the wings, behind the hips) and should take to the air: more than
 * {@link #TOO_MANY} - 1 hits within {@link #WINDOW} ticks.
 */
public final class HitTally {
	public static final int TOO_MANY = 4, WINDOW = 50;

	private final int[] ticks = new int[TOO_MANY];
	private int count, next;

	/** A hit that hurt, at game tick {@code tick}. */
	public void hit(int tick) {
		ticks[next] = tick;
		next = (next + 1) % TOO_MANY;
		count = Math.min(TOO_MANY, count + 1);
	}

	/** Whether the last {@link #TOO_MANY} hits all came within the last {@link #WINDOW} ticks. */
	public boolean overwhelmed(int tick) {
		if (count < TOO_MANY) return false;
		int oldest = ticks[next];      // the ring is full: the next slot holds the oldest
		return tick - oldest <= WINDOW;
	}

	public void clear() {
		count = next = 0;
	}
}
