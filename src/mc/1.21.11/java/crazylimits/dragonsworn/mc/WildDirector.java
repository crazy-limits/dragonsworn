package crazylimits.dragonsworn.mc;

import crazylimits.dragonsworn.ai.AirTactics;
import crazylimits.dragonsworn.ai.Roaming;
import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.mc.phase.GroundApproachPhase;
import crazylimits.dragonsworn.mc.phase.RoamPhase;
import crazylimits.dragonsworn.nav.BlockGrid;
import crazylimits.dragonsworn.nav.LandingSite;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A wild dragon's will (server): any dragon outside the End fight. Dragons are lazy: it lives on foot
 * ({@link Roaming}: long spells walking and resting, short flights to come down somewhere else) and fights
 * on the ground, landing beside its target; it attacks from the air on a break ({@code CombatStance}) or
 * where it cannot come down by the target (on a wall, up a pillar, in the air).
 */
final class WildDirector {
	/** Summoned dragons start hovering where they appeared: after this many ticks they get going. */
	private static final int START_TICKS = 40;
	/** Ticks between looks round for a target, and between moves of the fight's origin to where it is. */
	private static final int SCAN_TICKS = 20, ORIGIN_TICKS = 100;
	/** A first flight (ticks) before it lands, and a look further on when there was nowhere to land. */
	private static final int FIRST_FLIGHT = 300, LOOK_FURTHER = 100;
	/** Coming down along its way: that far ahead it looks for a site, within that radius (blocks). */
	private static final double LAND_AHEAD = 24.0;
	/** Below the dragon (blocks) the fight's origin is put where there is no ground under it. */
	private static final int NO_GROUND_DROP = 20;

	private final DragonBrain brain;
	private int scanCooldown, landCooldown;
	/** Ticks before the next air attack, counted down at the crowd's pace ({@code Crowd#pace}: more players, sooner). */
	private double attackCooldown = DragonConfig.FIRST_ATTACK.get();
	/** Ticks of roaming flight left before it looks for somewhere to land (a first short one). */
	private int airLeft = FIRST_FLIGHT;
	private boolean landed;
	private LivingEntity target;

	WildDirector(DragonBrain brain) {
		this.brain = brain;
	}

	void tick() {
		EnderDragon dragon = brain.dragon();
		DragonPhaseInstance phase = dragon.getPhaseManager().getCurrentPhase();
		// summoned dragons start hovering where they appeared: get going
		if (phase.getPhase() == EnderDragonPhase.HOVERING && dragon.tickCount > START_TICKS) {
			dragon.getPhaseManager().setPhase(DragonPhases.ROAM);
			return;
		}
		if (attackCooldown > 0) attackCooldown -= brain.crowd.pace();
		if (--scanCooldown <= 0) {
			scanCooldown = SCAN_TICKS;
			target = findTarget();
		}
		// vanilla phases (the charge, death) head back to the fight origin: keep it where the dragon is
		if (dragon.tickCount % ORIGIN_TICKS == 0) dragon.setFightOrigin(groundAt(dragon.blockPosition()));
		brain.stance.tick(target != null);
		if (brain.onGround()) landed = true;
		if (landCooldown > 0) landCooldown--;
		if (!(phase instanceof RoamPhase roam)) return;
		if (landed) {
			// back in the air: a short flight before it comes down again
			landed = false;
			airLeft = Roaming.airSpell(ThreadLocalRandom.current());
		}
		airLeft--;
		if (!roam.idle()) return;
		Tactics tactics = brain.tactics;
		// a fight is the ground's (lazy dragons): come down beside the target whenever there is room
		if (target != null && DragonConfig.LAND_TO_FIGHT.get() && brain.stance.grounded() && landCooldown <= 0 && !tactics.airborne(target)
				&& !tactics.isWalled(target)) {
			landCooldown = DragonConfig.LANDING_RETRY.get();
			if (target.onGround()) {
				if (tactics.tryGroundAssault(target)) return;
				tactics.walled(target);
			}
		}
		if (target != null && attackCooldown <= 0) {
			AirTactics.Reach reach = tactics.reach(target);
			tactics.attack(roam, target, reach);
			// on a break in the air, or at a target it cannot land by, it attacks from there in earnest
			boolean earnest = !brain.stance.grounded() || AirTactics.airborne(reach);
			attackCooldown = earnest ? DragonConfig.between(DragonConfig.EARNEST_COOLDOWN_MIN, DragonConfig.EARNEST_COOLDOWN_MAX, ThreadLocalRandom.current())
					: DragonConfig.between(DragonConfig.CASUAL_COOLDOWN_MIN, DragonConfig.CASUAL_COOLDOWN_MAX, ThreadLocalRandom.current());
		} else if (target == null && airLeft <= 0) {
			airLeft = LOOK_FURTHER;   // nowhere to land here: look again a little further on
			landAhead();
		}
	}

	/** Comes down somewhere along its way, to roam on foot for a while (it rests: no target). */
	private void landAhead() {
		EnderDragon dragon = brain.dragon();
		Vec3 facing = Targets.facing(dragon.getYRot());
		double x = dragon.getX() + facing.x * LAND_AHEAD, z = dragon.getZ() + facing.z * LAND_AHEAD;
		int[] site = new LandingSite(brain.grid()).find(x, z, 0, LAND_AHEAD, 0, dragon.getX(), dragon.getZ());
		if (site != null && brain.ticking(site[0], site[2])) GroundApproachPhase.start(dragon, site, null);
	}

	/** Whoever last attacked it while still worth chasing, else the nearest survival player in hunting range. */
	private LivingEntity findTarget() {
		LivingEntity attacker = brain.combat.lastAttacker(DragonConfig.FORGET_RANGE.get());
		if (attacker != null) return attacker;
		double range = DragonConfig.HUNT_RANGE.get();
		EnderDragon dragon = brain.dragon();
		return range <= 0.0 || !(dragon.level() instanceof ServerLevel level) ? null
				: level.getNearestPlayer(TargetingConditions.forCombat().range(range), dragon);
	}

	private BlockPos groundAt(BlockPos pos) {
		int y = brain.grid().ground(pos.getX(), pos.getZ());
		return new BlockPos(pos.getX(), y == BlockGrid.NO_GROUND ? pos.getY() - NO_GROUND_DROP : y, pos.getZ());
	}
}
