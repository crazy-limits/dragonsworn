package crazylimits.dragonsworn.mc;

import crazylimits.dragonsworn.ai.AirTactics;
import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.mc.phase.BreathPassPhase;
import crazylimits.dragonsworn.mc.phase.GroundApproachPhase;
import crazylimits.dragonsworn.mc.phase.SnatchPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The End fight's dragon (server). Vanilla's fight stays (pillar circuit, strafes), but it never lands on
 * the exit portal: where vanilla's would, it perches next to a player on the island instead (scans, roars,
 * breathes, takes off). Now and then it also lands next to one to fight on the ground, more often once the
 * crystals are gone, or snatches or breath-passes them; a player it cannot land by is fought in the air.
 */
final class ArenaDirector {
	private static final TargetingConditions TARGET = TargetingConditions.forCombat().range(150.0);

	private final DragonBrain brain;
	private int groundCooldown = DragonConfig.ARENA_FIRST_ASSAULT.get();
	/** Vanilla's dragon chose to land on the exit portal; it perches by a player instead. */
	private boolean perchWanted;

	ArenaDirector(DragonBrain brain) {
		this.brain = brain;
	}

	/** Vanilla's phases asked to land on the portal: it perches by a player on its next tick instead. */
	void wantPerch() {
		perchWanted = true;
	}

	void tick() {
		EnderDragon dragon = brain.dragon();
		if (perchWanted) {
			perchWanted = false;
			if (dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.HOLDING_PATTERN && perchByPlayer()) return;
		}
		if (groundCooldown > 0) {
			groundCooldown--;
			return;
		}
		if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.HOLDING_PATTERN) return;
		groundCooldown = DragonConfig.ARENA_RETRY.get();     // retry soon when nobody is on open ground
		BlockPos origin = dragon.getFightOrigin();
		Player player = ((ServerLevel) dragon.level()).getNearestPlayer(TARGET, dragon, origin.getX(), origin.getY(), origin.getZ());
		if (player == null || player.distanceToSqr(Vec3.atCenterOf(origin)) > island() * island()) return;
		Tactics tactics = brain.tactics;
		// in the air (elytra): fought there
		if (tactics.airborne(player)) {
			tactics.attack(null, player, AirTactics.Reach.AIR);
			groundCooldown = DragonConfig.between(DragonConfig.ARENA_AFTER_AIR_MIN, DragonConfig.ARENA_AFTER_AIR_MAX, ThreadLocalRandom.current());
			return;
		}
		if (!player.onGround()) return;
		// now and then a snatch or a breath pass instead of a landing
		double r = ThreadLocalRandom.current().nextDouble(), snatch = DragonConfig.ARENA_SNATCH_CHANCE.get();
		boolean pass = r < snatch ? DragonConfig.SNATCH.get() && SnatchPhase.start(dragon, player)
				: r < snatch + DragonConfig.ARENA_BREATH_PASS_CHANCE.get() && DragonConfig.BREATH_PASS.get() && BreathPassPhase.start(dragon, player);
		if (pass) {
			groundCooldown = DragonConfig.between(DragonConfig.ARENA_AFTER_PASS_MIN, DragonConfig.ARENA_AFTER_PASS_MAX, ThreadLocalRandom.current());
			return;
		}
		if (!DragonConfig.ARENA_GROUND_ASSAULT.get()) return;
		if (tactics.tryGroundAssault(player)) {
			boolean crystals = dragon.getDragonFight() != null && dragon.getDragonFight().aliveCrystals() > 0;
			groundCooldown = crystals
					? DragonConfig.between(DragonConfig.ARENA_AFTER_LANDING_MIN, DragonConfig.ARENA_AFTER_LANDING_MAX, ThreadLocalRandom.current())
					: DragonConfig.between(DragonConfig.ARENA_NO_CRYSTALS_MIN, DragonConfig.ARENA_NO_CRYSTALS_MAX, ThreadLocalRandom.current());
			return;
		}
		// nowhere to land by it (up a spire, pillaring up to a crystal): it comes to fight it in the air there
		tactics.attack(null, player, AirTactics.Reach.WALL);
		groundCooldown = DragonConfig.between(DragonConfig.ARENA_AFTER_AIR_MIN, DragonConfig.ARENA_AFTER_AIR_MAX, ThreadLocalRandom.current());
	}

	/** How far from the fight's origin a player counts as on the island (blocks). */
	private static double island() {
		return DragonConfig.ISLAND.get();
	}

	/**
	 * Perches on the island beside the player nearest the dragon (on the ground, within {@link #island} of
	 * the fight's origin), if there is room to land there; else it flies on, and vanilla's holding pattern
	 * decides to land again later.
	 */
	private boolean perchByPlayer() {
		EnderDragon dragon = brain.dragon();
		BlockPos origin = dragon.getFightOrigin();
		Vec3 center = Vec3.atCenterOf(origin);
		Player player = ((ServerLevel) dragon.level()).getNearestPlayer(TARGET.copy().selector((p, level) -> p.distanceToSqr(center) < island() * island()),
				dragon, dragon.getX(), dragon.getY(), dragon.getZ());
		if (player == null) return false;
		int[] site = brain.tactics.landingSiteBy(player);
		if (site == null) return false;
		GroundApproachPhase.perch(dragon, site);
		return true;
	}
}
