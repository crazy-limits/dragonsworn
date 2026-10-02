package crazylimits.dragonsworn.nav;

/**
 * The world as the dragon's navigation sees it. Blocks it smashes through on its way (leaves, plants:
 * the {@code dragonsworn:dragon_breakable} tag) count as empty: it plans straight through them and
 * breaks them when it gets there.
 */
public interface BlockGrid {
	/** {@link #ground} of a column with nothing to stand on: liquid, void, or not loaded. */
	int NO_GROUND = Integer.MIN_VALUE;

	/** Whether the block at x, y, z stops the dragon (it has collision and is not breakable). */
	boolean blocked(int x, int y, int z);

	/** The y a foot rests at in column x, z (just above its top solid block), or {@link #NO_GROUND}. */
	int ground(int x, int z);
}
