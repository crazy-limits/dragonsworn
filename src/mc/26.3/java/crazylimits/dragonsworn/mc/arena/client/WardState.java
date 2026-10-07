package crazylimits.dragonsworn.mc.arena.client;

/** Carries a crystal's ward from the render state's extraction to the drawing: mixed into {@code EndCrystalRenderState}. */
public interface WardState {
	boolean dragonsworn$warded();

	void dragonsworn$setWarded(boolean warded);
}
