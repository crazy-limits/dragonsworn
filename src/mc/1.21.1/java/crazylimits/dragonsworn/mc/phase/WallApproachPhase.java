package crazylimits.dragonsworn.mc.phase;

import crazylimits.dragonsworn.flight.FlightModel;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonPhases;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.nav.BlockGrid;
import crazylimits.dragonsworn.nav.SurfaceGrid;
import crazylimits.dragonsworn.nav.SurfaceSites;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Coming in to grip a wall ({@code nav/SurfaceSites#near}): beside prey on a cliff or a spire's flank, in a
 * tunnel dug into it, on a ledge with no room to stand. It flies to a point in the air {@link #OUT} blocks
 * out from the site, hovers there facing the wall, checks the site still fits and hops onto it
 * ({@link HopPhase}). If it cannot get there, or the site no longer fits, it gives up and stays in the air.
 */
public class WallApproachPhase extends AbstractDragonPhaseInstance implements DragonswornPhase {
	/** How far out from the face it hovers before the hop, and how far above the site (blocks). */
	static final double OUT = 7.0, UP = 2.0;
	/** Close enough to hover (blocks), and to hop. */
	static final double HOVER_NEAR = 14.0, HOP_NEAR = 2.5;
	private static final int GIVE_UP = 600;

	@Nullable
	private SurfaceSites.Site site;
	@Nullable
	private LivingEntity target;
	private int ticks;

	public WallApproachPhase(EnderDragon dragon) {
		super(dragon);
	}

	/** Flies in to grip {@code site} (a wall's), then fights {@code target} there. */
	public static void start(EnderDragon dragon, SurfaceSites.Site site, @Nullable LivingEntity target) {
		dragon.getPhaseManager().setPhase(DragonPhases.WALL_APPROACH);
		WallApproachPhase phase = dragon.getPhaseManager().getPhase(DragonPhases.WALL_APPROACH);
		phase.site = site;
		phase.target = target;
	}

	@Override
	public EnderDragonPhase<WallApproachPhase> getPhase() {
		return DragonPhases.WALL_APPROACH;
	}

	private DragonBrain brain() {
		return DragonswornDragon.brain(dragon);
	}

	@Override
	public void begin() {
		site = null;
		target = null;
		ticks = 0;
	}

	/** The site's point on the face (world). */
	private Vec3 onFace() {
		double[] w = site.world();
		return new Vec3(w[0], w[1], w[2]);
	}

	/** Where it hovers before the hop: out in front of the site, a little above it. */
	private Vec3 front() {
		return onFace().add(site.face().nx * OUT, UP, site.face().nz * OUT);
	}

	@Override
	public void doServerTick() {
		if (site == null || ++ticks > GIVE_UP || brain().hull.stuckTicks() > 80) {
			giveUp();
			return;
		}
		if (target != null && target.isAlive()) brain().setLookTarget(target);
		if (front().distanceTo(dragon.position()) > HOP_NEAR) return;
		BlockGrid grid = SurfaceGrid.around(brain().grid(), site.face(), site.y());
		if (SurfaceSites.fits(grid, site.face(), site.x(), site.z(), site.yaw(), 0) == BlockGrid.NO_GROUND) {
			giveUp();
			return;
		}
		HopPhase.start(dragon, site, target);
	}

	private void giveUp() {
		dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
	}

	@Nullable
	@Override
	public Vec3 getFlyTargetLocation() {
		return site == null ? null : front();
	}

	@Override
	public FlightModel.Force flightForce() {
		return site != null && front().distanceTo(dragon.position()) < HOVER_NEAR ? FlightModel.Force.HOVER : FlightModel.Force.NONE;
	}

	/** Hovering in, it faces the wall it is about to grip. */
	@Nullable
	@Override
	public Vec3 hoverLook() {
		return site == null ? null : onFace();
	}

	@Nullable
	@Override
	public LivingEntity attackTarget() {
		return target;
	}

	@Override
	public float getFlySpeed() {
		return 1.0F;
	}
}
