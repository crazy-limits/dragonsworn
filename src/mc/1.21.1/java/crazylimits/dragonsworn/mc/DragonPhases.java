package crazylimits.dragonsworn.mc;

import crazylimits.dragonsworn.mc.mixin.EnderDragonPhaseInvoker;
import crazylimits.dragonsworn.mc.phase.BreathPassPhase;
import crazylimits.dragonsworn.mc.phase.FlybyBitePhase;
import crazylimits.dragonsworn.mc.phase.GroundApproachPhase;
import crazylimits.dragonsworn.mc.phase.GroundFightPhase;
import crazylimits.dragonsworn.mc.phase.HoverAttackPhase;
import crazylimits.dragonsworn.mc.phase.LiftoffPhase;
import crazylimits.dragonsworn.mc.phase.RoamPhase;
import crazylimits.dragonsworn.mc.phase.SnatchPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;

/**
 * Dragonsworn's dragon phases, appended to vanilla's phase registry so they save, load and sync like
 * vanilla ones. {@link #register()} must run on both sides before any dragon exists (mod init).
 */
public final class DragonPhases {
	/** Away from the End fight: circles its home, hunts players near it. */
	public static EnderDragonPhase<RoamPhase> ROAM;
	/** Flies to a landing site and comes down on it, hovering. */
	public static EnderDragonPhase<GroundApproachPhase> GROUND_APPROACH;
	/** On the ground: fights (walks, bites, sweeps, roars) or rests. */
	public static EnderDragonPhase<GroundFightPhase> GROUND_FIGHT;
	/** Leaves the ground: crouch, jump with legs and wings, climb on the wings. */
	public static EnderDragonPhase<LiftoffPhase> LIFTOFF;
	/** Dives at its prey, takes it in its talons, climbs and drops it. */
	public static EnderDragonPhase<SnatchPhase> SNATCH;
	/** Glides over its prey head down, pouring the stream breath on the ground under it. */
	public static EnderDragonPhase<BreathPassPhase> BREATH_PASS;
	/** Glides past prey it cannot land by, biting it in passing. */
	public static EnderDragonPhase<FlybyBitePhase> FLYBY_BITE;
	/** Stands in the air beside prey it cannot land by, biting it or pouring the breath at it. */
	public static EnderDragonPhase<HoverAttackPhase> HOVER_ATTACK;

	private DragonPhases() {}

	public static synchronized void register() {
		if (ROAM != null) return;
		ROAM = EnderDragonPhaseInvoker.dragonsworn$createPhase(RoamPhase.class, "DragonswornRoam");
		GROUND_APPROACH = EnderDragonPhaseInvoker.dragonsworn$createPhase(GroundApproachPhase.class, "DragonswornGroundApproach");
		GROUND_FIGHT = EnderDragonPhaseInvoker.dragonsworn$createPhase(GroundFightPhase.class, "DragonswornGroundFight");
		LIFTOFF = EnderDragonPhaseInvoker.dragonsworn$createPhase(LiftoffPhase.class, "DragonswornLiftoff");
	}

	/** Phases added later: registered after every earlier one, so saved phase ids keep their meaning. */
	public static synchronized void registerLate() {
		if (SNATCH != null) return;
		SNATCH = EnderDragonPhaseInvoker.dragonsworn$createPhase(SnatchPhase.class, "DragonswornSnatch");
		BREATH_PASS = EnderDragonPhaseInvoker.dragonsworn$createPhase(BreathPassPhase.class, "DragonswornBreathPass");
		FLYBY_BITE = EnderDragonPhaseInvoker.dragonsworn$createPhase(FlybyBitePhase.class, "DragonswornFlybyBite");
		HOVER_ATTACK = EnderDragonPhaseInvoker.dragonsworn$createPhase(HoverAttackPhase.class, "DragonswornHoverAttack");
	}
}
