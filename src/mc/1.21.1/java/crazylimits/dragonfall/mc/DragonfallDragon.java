package crazylimits.dragonfall.mc;

/** Implemented on every {@code EnderDragon} by a mixin: the Dragonfall state attached to it. */
public interface DragonfallDragon {
	DragonBrain dragonfall$brain();

	static DragonBrain brain(Object dragon) {
		return ((DragonfallDragon) dragon).dragonfall$brain();
	}
}
