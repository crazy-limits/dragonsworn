package crazylimits.dragonsworn.mc;

/** Implemented on every {@code EnderDragon} by a mixin: the Dragonsworn state attached to it. */
public interface DragonswornDragon {
	DragonBrain dragonsworn$brain();

	static DragonBrain brain(Object dragon) {
		return ((DragonswornDragon) dragon).dragonsworn$brain();
	}
}
