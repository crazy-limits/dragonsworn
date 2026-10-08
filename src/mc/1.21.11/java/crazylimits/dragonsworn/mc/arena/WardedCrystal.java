package crazylimits.dragonsworn.mc.arena;

/** An End crystal's rune ward ({@code CrystalWard}), synced and saved: mixed into {@code EndCrystal} by {@code EndCrystalMixin}. */
public interface WardedCrystal {
	boolean dragonsworn$warded();

	/** Sets the ward (server), which also marks it {@link #dragonsworn$wardSet}. */
	void dragonsworn$setWarded(boolean warded);

	/** Whether its ward was ever set, warded or not (server; saved): a crystal not set yet is one another mod's spire put there. */
	boolean dragonsworn$wardSet();
}
