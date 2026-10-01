package crazylimits.dragonfall.mc.phase;

import crazylimits.dragonfall.flight.FlightModel;
import crazylimits.dragonfall.mc.DragonBrain;
import crazylimits.dragonfall.mc.DragonPhases;
import crazylimits.dragonfall.mc.DragonfallDragon;
import crazylimits.dragonfall.nav.BlockGrid;
import crazylimits.dragonfall.nav.LandingSite;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

/**
 * Coming down on a landing site ({@link LandingSite}): fly to a point {@link #ABOVE} blocks over it,
 * check the site still fits, then hover straight down onto it. If it no longer fits (somebody built on
 * it) or the dragon cannot get there, it gives up and stays in the air.
 */
public class GroundApproachPhase extends AbstractDragonPhaseInstance implements DragonfallPhase {
	static final double ABOVE = 12.0;
	private static final int GIVE_UP = 900;

	@Nullable
	private int[] site;
	@Nullable
	private LivingEntity target;
	private boolean descending;
	private int ticks;

	public GroundApproachPhase(EnderDragon dragon) {
		super(dragon);
	}

	/** Lands on {@code site} ({x, y, z}), then fights {@code target} (or rests, when null). */
	public static void start(EnderDragon dragon, int[] site, @Nullable LivingEntity target) {
		dragon.getPhaseManager().setPhase(DragonPhases.GROUND_APPROACH);
		GroundApproachPhase phase = dragon.getPhaseManager().getPhase(DragonPhases.GROUND_APPROACH);
		phase.site = site;
		phase.target = target;
	}

	@Override
	public EnderDragonPhase<GroundApproachPhase> getPhase() {
		return DragonPhases.GROUND_APPROACH;
	}

	@Override
	public void begin() {
		site = null;
		target = null;
		descending = false;
		ticks = 0;
	}

	private DragonBrain brain() {
		return DragonfallDragon.brain(dragon);
	}

	private Vec3 above() {
		return new Vec3(site[0] + 0.5, site[1] + ABOVE, site[2] + 0.5);
	}

	@Override
	public void doServerTick() {
		if (site == null || ++ticks > GIVE_UP || brain().stuckTicks() > 80) {
			giveUp();
			return;
		}
		double horizontal = Math.hypot(site[0] + 0.5 - dragon.getX(), site[2] + 0.5 - dragon.getZ());
		if (!descending && horizontal < 5.0 && Math.abs(dragon.getY() - (site[1] + ABOVE)) < 6.0) {
			if (new LandingSite(brain().grid()).fits(site[0], site[2]) == BlockGrid.NO_GROUND) {
				giveUp();
				return;
			}
			descending = true;
		}
		if (descending && dragon.getY() - site[1] < 1.0 && horizontal < 2.0) {
			dragon.setPos(site[0] + 0.5, site[1], site[2] + 0.5);
			dragon.setDeltaMovement(Vec3.ZERO);
			GroundFightPhase.start(dragon, target);
		}
	}

	private void giveUp() {
		dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
	}

	@Override
	public FlightModel.Force flightForce() {
		if (site == null) return FlightModel.Force.NONE;
		if (descending) return FlightModel.Force.HOVER;
		// flare out of the approach a little before the point above the site
		return above().distanceToSqr(dragon.position()) < 18 * 18 ? FlightModel.Force.HOVER : FlightModel.Force.NONE;
	}

	/** The site was checked to fit; the last blocks down the hanging tail may brush the ground. */
	@Override
	public boolean collides() {
		return !(descending && site != null && dragon.getY() - site[1] < 4.0);
	}

	@Override
	public float getFlySpeed() {
		return 1.0F;
	}

	@Nullable
	@Override
	public Vec3 getFlyTargetLocation() {
		if (site == null) return null;
		if (!descending) return above();
		// the hover sinks toward the site, fast high up and gently over the last blocks (its height
		// control eases off as the error shrinks)
		return new Vec3(site[0] + 0.5, site[1], site[2] + 0.5);
	}
}
