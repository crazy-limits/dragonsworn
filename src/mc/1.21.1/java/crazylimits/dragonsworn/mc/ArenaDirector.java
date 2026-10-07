package crazylimits.dragonsworn.mc;

import crazylimits.dragonsworn.ai.AirTactics;
import crazylimits.dragonsworn.ai.Crowd;
import crazylimits.dragonsworn.ai.CrystalGuard;
import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.mc.phase.BreathPassPhase;
import crazylimits.dragonsworn.mc.phase.GroundApproachPhase;
import crazylimits.dragonsworn.mc.phase.GroundFightPhase;
import crazylimits.dragonsworn.mc.phase.SnatchPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The End fight's dragon (server). Vanilla's fight stays (pillar circuit, strafes), but it never lands on
 * the exit portal: where vanilla's would, it perches next to a player on the island instead (scans, roars,
 * breathes, takes off). Now and then it also lands next to one to fight on the ground, more often once the
 * crystals are gone, or snatches or breath-passes them; a player it cannot land by is fought in the air.
 *
 * <p>It guards its crystals ({@link CrystalGuard}): a player near one is gone after first and sooner
 * ({@code guard_retry}), one at a time, by this director and by vanilla's strafe ({@link #guardTarget}).
 * Perched while someone is at them, it leaves the perch for them. Perched and hit by someone close, it gets
 * up and fights them on foot ({@code perch_fight_back}).
 *
 * <p>The more players on the island, the sooner it comes again ({@link Crowd#pace}; at its fastest while
 * its crystals are threatened), and players bunched together draw the breath pass ({@link Crowd#areaBias}).
 */
final class ArenaDirector {
	private static final TargetingConditions TARGET = TargetingConditions.forCombat().range(150.0);

	private final DragonBrain brain;
	/** Ticks before it goes for a player again, counted down at the crowd's pace. */
	private double groundCooldown = DragonConfig.ARENA_FIRST_ASSAULT.get();
	/** Vanilla's dragon chose to land on the exit portal; it perches by a player instead. */
	private boolean perchWanted;
	/** Ticks between looks round the crystals for who threatens them. */
	private static final int GUARD_SCAN_TICKS = 10;
	/** Perched, someone hitting it this close (blocks) within {@link #PERCH_HIT_TICKS} gets it up to fight them. */
	private static final double PERCH_HIT_RANGE = 14.0;
	private static final int PERCH_HIT_TICKS = 10;
	private final CrystalGuard guard = new CrystalGuard();
	/** The player threatening its crystals it is after; null for none. */
	@Nullable
	private Player guarded;

	ArenaDirector(DragonBrain brain) {
		this.brain = brain;
	}

	/** Vanilla's phases asked to land on the portal: it perches by a player on its next tick instead. */
	void wantPerch() {
		perchWanted = true;
	}

	void tick() {
		EnderDragon dragon = brain.dragon();
		if (dragon.tickCount % GUARD_SCAN_TICKS == 0) guarded = DragonConfig.GUARD_CRYSTALS.get() ? findThreat() : null;
		if (perchWanted) {
			perchWanted = false;
			if (dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.HOLDING_PATTERN && perchByPlayer()) return;
		}
		if (leavePerchForCrystals() || fightBackOnPerch()) return;
		// someone at its crystals: it comes for them soon, whatever it was waiting for
		if (guarded != null) groundCooldown = Math.min(groundCooldown, DragonConfig.GUARD_RETRY.get());
		if (groundCooldown > 0) {
			groundCooldown -= brain.crowd.pace();
			return;
		}
		if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.HOLDING_PATTERN) return;
		groundCooldown = DragonConfig.ARENA_RETRY.get();     // retry soon when nobody is on open ground
		BlockPos origin = dragon.getFightOrigin();
		Player player = guarded != null ? guarded
				: ((ServerLevel) dragon.level()).getNearestPlayer(TARGET, dragon, origin.getX(), origin.getY(), origin.getZ());
		if (player == null || player.distanceToSqr(Vec3.atCenterOf(origin)) > island() * island()) return;
		Tactics tactics = brain.tactics;
		// in the air (elytra): fought there
		if (tactics.airborne(player)) {
			tactics.attack(null, player, AirTactics.Reach.AIR);
			groundCooldown = DragonConfig.between(DragonConfig.ARENA_AFTER_AIR_MIN, DragonConfig.ARENA_AFTER_AIR_MAX, ThreadLocalRandom.current());
			return;
		}
		if (!player.onGround()) return;
		// now and then a snatch or a breath pass instead of a landing (the breath pass likelier on players bunched together)
		double[] odds = Crowd.odds(DragonConfig.ARENA_SNATCH_CHANCE.get(), DragonConfig.ARENA_BREATH_PASS_CHANCE.get(),
				brain.crowd.areaBias(player.getX(), player.getY(), player.getZ()));
		double r = ThreadLocalRandom.current().nextDouble();
		boolean pass = r < odds[0] ? DragonConfig.SNATCH.get() && SnatchPhase.start(dragon, player)
				: r < odds[0] + odds[1] && DragonConfig.BREATH_PASS.get() && BreathPassPhase.start(dragon, player);
		if (pass) {
			groundCooldown = DragonConfig.between(DragonConfig.ARENA_AFTER_PASS_MIN, DragonConfig.ARENA_AFTER_PASS_MAX, ThreadLocalRandom.current());
			return;
		}
		if (!DragonConfig.ARENA_GROUND_ASSAULT.get()) return;
		// lately out of reach on the ground (it landed by it and could not get at it): fought in the air
		if (!tactics.isWalled(player) && tactics.tryGroundAssault(player)) {
			boolean crystals = dragon.getDragonFight() != null && dragon.getDragonFight().getCrystalsAlive() > 0;
			groundCooldown = crystals
					? DragonConfig.between(DragonConfig.ARENA_AFTER_LANDING_MIN, DragonConfig.ARENA_AFTER_LANDING_MAX, ThreadLocalRandom.current())
					: DragonConfig.between(DragonConfig.ARENA_NO_CRYSTALS_MIN, DragonConfig.ARENA_NO_CRYSTALS_MAX, ThreadLocalRandom.current());
			return;
		}
		// nowhere to land by it (up a spire, pillaring up to a crystal): it comes to fight it in the air there
		tactics.attack(null, player, AirTactics.Reach.WALL);
		groundCooldown = DragonConfig.between(DragonConfig.ARENA_AFTER_AIR_MIN, DragonConfig.ARENA_AFTER_AIR_MAX, ThreadLocalRandom.current());
	}

	/** The player threatening its crystals it is after (vanilla's strafe goes for them too); null for none. */
	@Nullable
	Player guardTarget() {
		return guarded != null && guarded.isAlive() && !Targets.untouchable(guarded) ? guarded : null;
	}

	/** Of the players on the island, the one {@link CrystalGuard} picks among those near a standing crystal; null for none. */
	@Nullable
	private Player findThreat() {
		EnderDragon dragon = brain.dragon();
		ServerLevel level = (ServerLevel) dragon.level();
		Vec3 center = Vec3.atCenterOf(dragon.getFightOrigin());
		double island = island();
		List<EndCrystal> crystals = level.getEntitiesOfClass(EndCrystal.class, new AABB(center, center).inflate(island, 128.0, island), EndCrystal::isAlive);
		if (crystals.isEmpty()) return null;
		List<CrystalGuard.Suspect> suspects = new ArrayList<>();
		List<Player> players = new ArrayList<>();
		for (Player player : level.players()) {
			if (!player.isAlive() || Targets.untouchable(player) || player.distanceToSqr(center) > island * island) continue;
			double nearest = Double.MAX_VALUE;
			for (EndCrystal crystal : crystals) nearest = Math.min(nearest, crystal.distanceTo(player));
			suspects.add(new CrystalGuard.Suspect(player.getId(), nearest, player.distanceTo(dragon)));
			players.add(player);
		}
		int id = guard.pick(suspects);
		for (Player player : players) {
			if (player.getId() == id) return player;
		}
		return null;
	}

	/**
	 * Perched (scanning or roaring: not breathing) while someone is at its crystals: they come first. Close
	 * by ({@link GroundFightPhase#GUARD_NEAR}, on the ground) it gets up and fights them on foot; else it takes
	 * off, and its next attack goes at them.
	 */
	private boolean leavePerchForCrystals() {
		EnderDragon dragon = brain.dragon();
		EnderDragonPhase<?> phase = dragon.getPhaseManager().getCurrentPhase().getPhase();
		Player threat = guardTarget();
		if (threat == null || phase != EnderDragonPhase.SITTING_SCANNING && phase != EnderDragonPhase.SITTING_ATTACKING) return false;
		if (threat.onGround() && threat.distanceToSqr(dragon) < GroundFightPhase.GUARD_NEAR * GroundFightPhase.GUARD_NEAR) {
			GroundFightPhase.start(dragon, threat, false);
		} else {
			dragon.getPhaseManager().setPhase(EnderDragonPhase.TAKEOFF);
		}
		return true;
	}

	/**
	 * Perched (scanning or roaring: not breathing) and hit by someone close: it gets up and fights them on
	 * foot, turning to them, rather than sitting still while they hit it from where it does not look.
	 */
	private boolean fightBackOnPerch() {
		EnderDragon dragon = brain.dragon();
		EnderDragonPhase<?> phase = dragon.getPhaseManager().getCurrentPhase().getPhase();
		if (!DragonConfig.PERCH_FIGHT_BACK.get() || phase != EnderDragonPhase.SITTING_SCANNING && phase != EnderDragonPhase.SITTING_ATTACKING) return false;
		LivingEntity attacker = brain.combat.recentAttacker(PERCH_HIT_TICKS);
		if (attacker == null || attacker.distanceToSqr(dragon) > PERCH_HIT_RANGE * PERCH_HIT_RANGE || !attacker.onGround()) return false;
		GroundFightPhase.start(dragon, attacker, false);
		return true;
	}

	/** How far from the fight's origin a player counts as on the island (blocks). */
	private static double island() {
		return DragonConfig.ISLAND.get();
	}

	/**
	 * Perches on the island beside the player at its crystals ({@link #guardTarget}), else the one nearest
	 * the dragon (on the ground, within {@link #island} of the fight's origin), if there is room to land
	 * there; else it flies on, and vanilla's holding pattern decides to land again later.
	 */
	private boolean perchByPlayer() {
		EnderDragon dragon = brain.dragon();
		BlockPos origin = dragon.getFightOrigin();
		Vec3 center = Vec3.atCenterOf(origin);
		// the one at its crystals first
		Player player = guardTarget() != null ? guardTarget() : dragon.level().getNearestPlayer(TARGET.copy().selector(p -> p.distanceToSqr(center) < island() * island()),
				dragon, dragon.getX(), dragon.getY(), dragon.getZ());
		if (player == null) return false;
		int[] site = brain.tactics.landingSiteBy(player);
		if (site == null) return false;
		GroundApproachPhase.perch(dragon, site);
		return true;
	}
}
