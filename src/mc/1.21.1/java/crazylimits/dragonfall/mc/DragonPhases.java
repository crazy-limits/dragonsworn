package crazylimits.dragonfall.mc;

import crazylimits.dragonfall.mc.mixin.EnderDragonPhaseInvoker;
import crazylimits.dragonfall.mc.phase.GroundApproachPhase;
import crazylimits.dragonfall.mc.phase.GroundFightPhase;
import crazylimits.dragonfall.mc.phase.LiftoffPhase;
import crazylimits.dragonfall.mc.phase.RoamPhase;
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

	private DragonPhases() {}

	public static synchronized void register() {
		if (ROAM != null) return;
		ROAM = EnderDragonPhaseInvoker.dragonfall$createPhase(RoamPhase.class, "DragonfallRoam");
		GROUND_APPROACH = EnderDragonPhaseInvoker.dragonfall$createPhase(GroundApproachPhase.class, "DragonfallGroundApproach");
		GROUND_FIGHT = EnderDragonPhaseInvoker.dragonfall$createPhase(GroundFightPhase.class, "DragonfallGroundFight");
		LIFTOFF = EnderDragonPhaseInvoker.dragonfall$createPhase(LiftoffPhase.class, "DragonfallLiftoff");
	}
}
