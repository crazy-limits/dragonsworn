package crazylimits.dragonsworn.mc;

import crazylimits.dragonsworn.mc.breath.BreathStreamPhase;
import crazylimits.dragonsworn.mc.mixin.EnderDragonPhaseInvoker;
import crazylimits.dragonsworn.mc.phase.BreathPassPhase;
import crazylimits.dragonsworn.mc.phase.FlybyBitePhase;
import crazylimits.dragonsworn.mc.phase.GroundApproachPhase;
import crazylimits.dragonsworn.mc.phase.GroundFightPhase;
import crazylimits.dragonsworn.mc.phase.HoverAttackPhase;
import crazylimits.dragonsworn.mc.phase.LiftoffPhase;
import crazylimits.dragonsworn.mc.phase.RoamPhase;
import crazylimits.dragonsworn.mc.phase.SnatchPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;

/**
 * Dragonsworn's dragon phases, appended to vanilla's phase registry so they save, load and sync like
 * vanilla ones. {@link #register()} must run on both sides before any dragon exists (mod init).
 *
 * <p>Vanilla hands out phase ids in registration order, and a dragon saves its phase by id: <b>add a new
 * phase at the end of {@link #register()}, never reorder or remove one</b>, or saved dragons (and a client
 * and server of different versions) disagree on what each id means.
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
	/** Perched: pours the void-flame stream instead of vanilla's breath cloud. */
	public static EnderDragonPhase<BreathStreamPhase> BREATH_STREAM;
	/** Dives at its prey, takes it in its talons, climbs and drops it. */
	public static EnderDragonPhase<SnatchPhase> SNATCH;
	/** Glides over its prey head down, pouring the stream breath on the ground under it. */
	public static EnderDragonPhase<BreathPassPhase> BREATH_PASS;
	/** Glides past prey it cannot land by, biting it in passing. */
	public static EnderDragonPhase<FlybyBitePhase> FLYBY_BITE;
	/** Stands in the air beside prey it cannot land by, biting it or pouring the breath at it. */
	public static EnderDragonPhase<HoverAttackPhase> HOVER_ATTACK;

	private DragonPhases() {}

	/** Registers every phase, in id order (append only: see above). Idempotent. */
	public static synchronized void register() {
		if (ROAM != null) return;
		ROAM = create(RoamPhase.class, "DragonswornRoam");
		GROUND_APPROACH = create(GroundApproachPhase.class, "DragonswornGroundApproach");
		GROUND_FIGHT = create(GroundFightPhase.class, "DragonswornGroundFight");
		LIFTOFF = create(LiftoffPhase.class, "DragonswornLiftoff");
		BREATH_STREAM = create(BreathStreamPhase.class, "DragonswornBreathStream");
		SNATCH = create(SnatchPhase.class, "DragonswornSnatch");
		BREATH_PASS = create(BreathPassPhase.class, "DragonswornBreathPass");
		FLYBY_BITE = create(FlybyBitePhase.class, "DragonswornFlybyBite");
		HOVER_ATTACK = create(HoverAttackPhase.class, "DragonswornHoverAttack");
	}

	private static <T extends DragonPhaseInstance> EnderDragonPhase<T> create(Class<T> phase, String name) {
		return EnderDragonPhaseInvoker.dragonsworn$createPhase(phase, name);
	}
}
