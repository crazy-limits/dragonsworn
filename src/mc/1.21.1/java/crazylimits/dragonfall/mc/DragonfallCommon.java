package crazylimits.dragonfall.mc;

import crazylimits.dragonfall.mc.breath.BreathStreamPhase;

/** Loader-independent setup, run on both sides at mod init (before any dragon exists). */
public final class DragonfallCommon {
	private DragonfallCommon() {}

	public static void init() {
		// Entities size their synced data when they are created, before defineSynchedData runs: the
		// dragon's extra data must be defined before the first dragon exists.
		DragonData.init();
		// phase ids are handed out in registration order: keep this order identical on every side
		DragonPhases.register();
		BreathStreamPhase.register();
		DragonPhases.registerLate();
	}
}
