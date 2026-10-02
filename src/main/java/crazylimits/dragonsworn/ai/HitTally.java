package crazylimits.dragonsworn.ai;

import crazylimits.dragonsworn.config.DragonConfig;

/**
 * The hits a landed dragon takes, to know when it is being worn down from where it cannot answer (its
 * blind spots: under the chin, between the wings, behind the hips) and should take to the air:
 * {@link DragonConfig#OVERWHELM_HITS} hits within {@link DragonConfig#OVERWHELM_WINDOW} ticks.
 */
public final class HitTally {
	/** The most recent hits remembered: the most {@link DragonConfig#OVERWHELM_HITS} can be. */
	public static final int CAPACITY = DragonConfig.OVERWHELM_HITS.max();

	private final int[] ticks = new int[CAPACITY];
	private int count, next;

	/** A hit that hurt, at game tick {@code tick}. */
	public void hit(int tick) {
		ticks[next] = tick;
		next = (next + 1) % CAPACITY;
		count = Math.min(CAPACITY, count + 1);
	}

	/** Whether the last {@code overwhelm_hits} hits all came within the last {@code overwhelm_window} ticks. */
	public boolean overwhelmed(int tick) {
		int many = Math.min(CAPACITY, DragonConfig.OVERWHELM_HITS.get());
		if (count < many) return false;
		int oldest = ticks[Math.floorMod(next - many, CAPACITY)];     // the many-th most recent hit
		return tick - oldest <= DragonConfig.OVERWHELM_WINDOW.get();
	}

	public void clear() {
		count = next = 0;
	}
}
