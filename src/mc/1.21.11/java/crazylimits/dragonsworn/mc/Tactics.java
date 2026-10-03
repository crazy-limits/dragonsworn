package crazylimits.dragonsworn.mc;

import crazylimits.dragonsworn.ai.AirTactics;
import crazylimits.dragonsworn.ai.Foothold;
import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.mc.phase.BreathPassPhase;
import crazylimits.dragonsworn.mc.phase.FlybyBitePhase;
import crazylimits.dragonsworn.mc.phase.GroundApproachPhase;
import crazylimits.dragonsworn.mc.phase.HoverAttackPhase;
import crazylimits.dragonsworn.mc.phase.RoamPhase;
import crazylimits.dragonsworn.mc.phase.SnatchPhase;
import crazylimits.dragonsworn.nav.LandingSite;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * How the dragon goes at a target, wild or in the End fight (server): coming down beside it to fight on
 * foot ({@link #tryGroundAssault}), or one attack from the air chosen by where the target is
 * ({@link #reach}, {@link AirTactics}).
 */
public final class Tactics {
	/**
	 * A narrow foothold beside its prey: this far from it at least, at most and preferably (blocks). It
	 * cannot walk in from there, so within the bite's reach (the jaws strike ~6 blocks ahead).
	 */
	private static final double[] NARROW_RANGE = {4.0, 6.5, 5.0};
	/** A landing site beside the target: this far from it at least, at most and preferably (blocks). */
	private static final double SITE_MIN = 8, SITE_MAX = 17, SITE_PREFER = 12;
	/** ... and at most this far above or below it (blocks). */
	private static final double SITE_HEIGHT = 5;

	private final DragonBrain brain;
	/** The target last found with nowhere to land by it (on a wall, up a pillar), and when. */
	private LivingEntity walled;
	private int walledAt;

	Tactics(DragonBrain brain) {
		this.brain = brain;
	}

	/**
	 * Lands near the target to fight it on foot, if there is room to land there. With no room for all
	 * four limbs, it comes down on a narrow foothold ({@link Foothold}) within a bite of it instead: sat up
	 * on a ledge, or clinging to a pillar's top.
	 */
	public boolean tryGroundAssault(LivingEntity target) {
		EnderDragon dragon = brain.dragon();
		int[] site = landingSiteBy(target);
		if (site != null) {
			GroundApproachPhase.start(dragon, site, target, Foothold.STAND);
			return true;
		}
		if (!DragonConfig.NARROW_FOOTHOLDS.get()) return false;
		LandingSite sites = new LandingSite(brain.grid());
		for (Foothold foothold : new Foothold[]{Foothold.UPRIGHT, Foothold.CLING}) {
			site = sites.near(target.getX(), target.getY(), target.getZ(), NARROW_RANGE[0], NARROW_RANGE[1], NARROW_RANGE[2],
					dragon.getX(), dragon.getZ(), foothold);
			if (site == null || !brain.ticking(site[0], site[2])) continue;
			GroundApproachPhase.start(dragon, site, target, foothold);
			return true;
		}
		return false;
	}

	/** A landing site beside {@code target}, on its level; null when there is no room. */
	int[] landingSiteBy(LivingEntity target) {
		EnderDragon dragon = brain.dragon();
		int[] site = new LandingSite(brain.grid()).find(target.getX(), target.getZ(), SITE_MIN, SITE_MAX, SITE_PREFER, dragon.getX(), dragon.getZ());
		return site == null || Math.abs(site[1] - target.getY()) > SITE_HEIGHT ? null : site;
	}

	/**
	 * Whether {@code target} is in the air: gliding on elytra, flying, or with nothing under it for a few
	 * blocks (a jump is not). There is no landing beside it then: the dragon fights it in the air.
	 */
	public boolean airborne(LivingEntity target) {
		if (target.onGround()) return false;
		if (target.isFallFlying() || target instanceof Player p && p.getAbilities().flying) return true;
		return brain.dragon().level().noCollision(target.getBoundingBox().expandTowards(0.0, -DragonConfig.AIRBORNE_GAP.get(), 0.0));
	}

	/** Remembers that {@code target} stands where the dragon cannot come down beside it. */
	void walled(LivingEntity target) {
		walled = target;
		walledAt = brain.dragon().tickCount;
	}

	/** Where {@code target} is for an attack from the air: in it, on ground it cannot land by (lately found so), or on open ground. */
	AirTactics.Reach reach(LivingEntity target) {
		boolean walledNow = walled == target && brain.dragon().tickCount - walledAt < DragonConfig.WALLED_TICKS.get();
		return AirTactics.reach(airborne(target), !walledNow);
	}

	/**
	 * One attack on a target from the air (the landing to fight on foot is {@link #tryGroundAssault}): the
	 * first of {@link AirTactics#choices} that can start. In the End fight ({@code roam} null) vanilla's
	 * strafe makes the fireball attacks.
	 */
	void attack(RoamPhase roam, LivingEntity target, AirTactics.Reach reach) {
		for (AirTactics.Attack attack : AirTactics.choices(reach, ThreadLocalRandom.current().nextDouble())) {
			if (start(attack, roam, target)) return;
		}
	}

	/** Starts {@code attack} on {@code target}; false when it cannot start (out of range, no line of sight...). */
	private boolean start(AirTactics.Attack attack, RoamPhase roam, LivingEntity target) {
		EnderDragon dragon = brain.dragon();
		return switch (attack) {
			case SNATCH -> SnatchPhase.start(dragon, target);
			case BREATH_PASS -> BreathPassPhase.start(dragon, target);
			case FLYBY_BITE -> FlybyBitePhase.start(dragon, target);
			case HOVER_BITE -> HoverAttackPhase.start(dragon, target, HoverAttackPhase.Mode.BITE);
			case HOVER_BREATH -> HoverAttackPhase.start(dragon, target, HoverAttackPhase.Mode.BREATH);
			case CHARGE -> {
				if (!dragon.hasLineOfSight(target)) yield false;
				dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
				dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(target.position());
				yield true;
			}
			case FIREBALL_PASS, BARRAGE -> {
				// the roam's; vanilla's strafe in the End fight
				if (roam == null) {
					dragon.getPhaseManager().setPhase(EnderDragonPhase.STRAFE_PLAYER);
					dragon.getPhaseManager().getPhase(EnderDragonPhase.STRAFE_PLAYER).setTarget(target);
				} else if (attack == AirTactics.Attack.FIREBALL_PASS) {
					roam.startPass(target);
				} else {
					roam.startBarrage(target);
				}
				yield true;
			}
		};
	}
}
