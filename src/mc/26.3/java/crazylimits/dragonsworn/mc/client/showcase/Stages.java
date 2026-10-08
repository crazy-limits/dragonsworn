package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.ai.Foothold;

import java.util.LinkedHashMap;
import java.util.Map;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/**
 * Every showcase stage: run alone by name ({@code -Pdragonsworn.showcase=<name>}), and the full run. Each
 * stage builds its own test site on a plot of the flat world, offset from the yard at x, y, z far enough
 * that the plots do not see each other.
 *
 * <p>A new stage: a class with a {@code static void build...(int x, int y, int z)} that queues its steps on
 * the {@link Script} and {@link Script#check}s what it sees; a line in {@link #SOLO} (and in {@link #full}
 * on a free plot, if the full run should include it); its name in CONTRIBUTING.md.
 */
final class Stages {
	/** Builds a stage, its plot given (the yard's corner, offset). */
	@FunctionalInterface
	interface Build {
		void at(int x, int y, int z);
	}

	/** A stage run alone: with the game's HUD hidden for its screenshots or not. */
	private record Solo(boolean hideGui, Build build) {
	}

	private static final Map<String, Solo> SOLO = new LinkedHashMap<>();

	static {
		SOLO.put("grabs", new Solo(false, GrabsStage::grabs));
		SOLO.put("config", new Solo(false, (x, y, z) -> ConfigStage.configScreen()));
		SOLO.put("air", new Solo(true, (x, y, z) -> AirStage.air(x, y, z + 150)));
		SOLO.put("pass", new Solo(true, BreathPassStage::breathPass));
		SOLO.put("walls", new Solo(true, (x, y, z) -> WallsStage.walls(x, y, z + 150)));
		SOLO.put("breath", new Solo(true, (x, y, z) -> {
			BreathStage.breath(x, y, z);
			BreathStage.breathMoving(x, y, z + 60);
		}));
		SOLO.put("footing", new Solo(true, (x, y, z) -> FootingStage.footing(x, y, z + 150)));
		SOLO.put("collision", new Solo(true, (x, y, z) -> CollisionStage.collision(x, y, z + 150)));
		SOLO.put("hitboxes", new Solo(true, (x, y, z) -> HitboxesStage.hitboxes(x, y, z + 150)));
		SOLO.put("narrow", new Solo(true, (x, y, z) -> {
			NarrowStage.narrow(x, y, z + 150, Foothold.UPRIGHT);
			NarrowStage.narrow(x + 150, y, z + 150, Foothold.CLING);
		}));
		SOLO.put("aim", new Solo(true, (x, y, z) -> AimStage.aim(x, y, z + 150)));
		SOLO.put("climb", new Solo(true, (x, y, z) -> ClimbStage.climb(x, y, z + 150)));
		SOLO.put("stance", new Solo(true, (x, y, z) -> StanceStage.stance(x, y, z + 150)));
		SOLO.put("death", new Solo(true, (x, y, z) -> DeathStage.death(x, y, z + 150)));
		SOLO.put("wards", new Solo(true, (x, y, z) -> WardsStage.wards(x, y, z + 150)));
		// the AI's ground assault and takeoff, and the running landing
		SOLO.put("landing", new Solo(true, (x, y, z) -> {
			LandingStage.liveDragon(x, y, z + 150);
			LandingStage.runningLanding(x, y, z + 600);
		}));
	}

	private Stages() {
	}

	/** Queues stage {@code name} alone at the yard x, y, z; false when there is no such stage. */
	static boolean solo(String name, int x, int y, int z) {
		Solo solo = SOLO.get(name);
		if (solo == null) return false;
		if (solo.hideGui()) STEPS.add(new Step(1, mc -> Script.hideGui(mc, true)));
		solo.build().at(x, y, z);
		return true;
	}

	/** Queues the full run at the yard x, y, z: every animation photographed, then the stages, each on its plot. */
	static void full(int x, int y, int z) {
		AnimationsStage.build(x, y, z);
		LandingStage.liveDragon(x, y, z + 150);
		TerrainStage.roaming(x, y, z + 300);
		TerrainStage.hills(x, y, z + 450);
		LandingStage.runningLanding(x, y, z + 600);
		WallsStage.walls(x + 300, y, z + 150);
		BreathStage.breath(x, y, z);
		BreathStage.breathMoving(x, y, z + 60);
		BreathPassStage.breathPass(x + 150, y, z);
		GrabsStage.grabs(x, y, z - 150);
		StanceStage.stance(x + 300, y, z + 450);
		HitboxesStage.hitboxes(x + 300, y, z + 600);
		CollisionStage.collision(x + 450, y, z + 600);
		NarrowStage.narrow(x + 450, y, z + 150, Foothold.UPRIGHT);
		NarrowStage.narrow(x + 450, y, z + 300, Foothold.CLING);
		ClimbStage.climb(x + 600, y, z + 150);
		WardsStage.wards(x + 600, y, z + 450);
		FootingStage.footing(x + 750, y, z + 150);
	}
}
