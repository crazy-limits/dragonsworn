package crazylimits.dragonsworn.mc.arena;

/** An End crystal's rune ward ({@code CrystalWard}), synced and saved: mixed into {@code EndCrystal} by {@code EndCrystalMixin}. */
public interface WardedCrystal {
	boolean dragonsworn$warded();

	void dragonsworn$setWarded(boolean warded);
}
