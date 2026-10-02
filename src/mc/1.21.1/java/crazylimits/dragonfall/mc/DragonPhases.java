package crazylimits.dragonfall.mc;

import crazylimits.dragonfall.mc.mixin.EnderDragonPhaseInvoker;
import crazylimits.dragonfall.mc.phase.BreathPassPhase;
import crazylimits.dragonfall.mc.phase.FlybyBitePhase;
import crazylimits.dragonfall.mc.phase.GroundApproachPhase;
import crazylimits.dragonfall.mc.phase.GroundFightPhase;
import crazylimits.dragonfall.mc.phase.HoverAttackPhase;
import crazylimits.dragonfall.mc.phase.LiftoffPhase;
import crazylimits.dragonfall.mc.phase.RoamPhase;
import crazylimits.dragonfall.mc.phase.SnatchPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;

/**
 * Dragonfall's dragon phases, appended to vanilla's phase registry so they save, load and sync like
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
		ROAM = EnderDragonPhaseInvoker.dragonfall$createPhase(RoamPhase.class, "DragonfallRoam");
		GROUND_APPROACH = EnderDragonPhaseInvoker.dragonfall$createPhase(GroundApproachPhase.class, "DragonfallGroundApproach");
		GROUND_FIGHT = EnderDragonPhaseInvoker.dragonfall$createPhase(GroundFightPhase.class, "DragonfallGroundFight");
		LIFTOFF = EnderDragonPhaseInvoker.dragonfall$createPhase(LiftoffPhase.class, "DragonfallLiftoff");
	}

	/** Phases added later: registered after every earlier one, so saved phase ids keep their meaning. */
	public static synchronized void registerLate() {
		if (SNATCH != null) return;
		SNATCH = EnderDragonPhaseInvoker.dragonfall$createPhase(SnatchPhase.class, "DragonfallSnatch");
		BREATH_PASS = EnderDragonPhaseInvoker.dragonfall$createPhase(BreathPassPhase.class, "DragonfallBreathPass");
		FLYBY_BITE = EnderDragonPhaseInvoker.dragonfall$createPhase(FlybyBitePhase.class, "DragonfallFlybyBite");
		HOVER_ATTACK = EnderDragonPhaseInvoker.dragonfall$createPhase(HoverAttackPhase.class, "DragonfallHoverAttack");
	}
}
