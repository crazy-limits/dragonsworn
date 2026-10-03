package crazylimits.dragonsworn.mc.phase;

import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.Targets;
import crazylimits.dragonsworn.nav.BlockGrid;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What every attack from the air on one target shares ({@link SnatchPhase}, {@link BreathPassPhase},
 * {@link FlybyBitePhase}, {@link HoverAttackPhase}): the target, a tick count per stage, the waypoint the
 * flight code steers to, the run-up out away from the prey, and the way out (flying on, then vanilla's
 * holding pattern picks what comes next).
 *
 * <p>A new air attack: subclass this, register its phase in {@code DragonPhases} and give it a
 * {@code start(dragon, target)} that calls {@link #begin(EnderDragon, EnderDragonPhase, LivingEntity)}.
 */
public abstract class AirAttackPhase extends AbstractDragonPhaseInstance implements DragonswornPhase {
	@Nullable
	protected LivingEntity target;
	/** Ticks into the current stage. */
	protected int ticks;
	@Nullable
	protected Vec3 waypoint;

	protected AirAttackPhase(EnderDragon dragon) {
		super(dragon);
	}

	/** Switches {@code dragon} to {@code phase} against {@code target}; the phase instance, to set more on. */
	protected static <T extends AirAttackPhase> T begin(EnderDragon dragon, EnderDragonPhase<T> phase, LivingEntity target) {
		dragon.getPhaseManager().setPhase(phase);
		T instance = dragon.getPhaseManager().getPhase(phase);
		instance.target = target;
		return instance;
	}

	/** Resets the shared state; subclasses reset their own after calling this. */
	@Override
	public void begin() {
		target = null;
		ticks = 0;
		waypoint = null;
	}

	protected DragonBrain brain() {
		return DragonswornDragon.brain(dragon);
	}

	/** The target is gone: dead, elsewhere, further than {@code range}, or a player out of the game. */
	protected boolean lostBeyond(double range) {
		return target == null || !target.isAlive() || target.level() != dragon.level()
				|| target.distanceToSqr(dragon) > range * range || Targets.untouchable(target);
	}

	/** Nothing solid in the 3 x 3 columns over {@code target}'s head, up to {@code height} blocks. */
	protected static boolean openAbove(BlockGrid grid, LivingEntity target, double height) {
		int x0 = target.getBlockX(), y0 = Mth.floor(target.getY() + target.getBbHeight()), z0 = target.getBlockZ();
		for (int x = x0 - 1; x <= x0 + 1; x++) {
			for (int z = z0 - 1; z <= z0 + 1; z++) {
				for (int y = y0; y < y0 + height; y++) {
					if (grid.blocked(x, y, z)) return false;
				}
			}
		}
		return true;
	}

	/** The dragon's offset from the target across the ground. */
	protected Vec3 outFromTarget() {
		return dragon.position().subtract(target.position()).multiply(1, 0, 1);
	}

	/**
	 * The run-up: true once the dragon is {@code runUp} - 4 blocks out from the prey (time to turn in);
	 * until then it steers for a point {@code runUp} + 8 out on its side of the prey, {@code rise} over it.
	 */
	protected boolean runUp(double runUp, double rise) {
		Vec3 out = outFromTarget();
		double distance = out.length();
		if (distance > runUp - 4.0) return true;
		if (waypoint == null) {
			Vec3 dir = distance > 1e-3 ? out.scale(1.0 / distance) : dragon.getLookAngle().multiply(-1, 0, -1).normalize();
			waypoint = target.position().add(dir.scale(runUp + 8.0)).add(0.0, rise, 0.0);
		}
		return false;
	}

	/** The way out: on ahead {@code distance} blocks the way it faces, {@code rise} up. */
	protected Vec3 onAhead(double distance, double rise) {
		Vec3 facing = Targets.facing(dragon.getYRot());
		return dragon.position().add(facing.x * distance, rise, facing.z * distance);
	}

	/** Flying away: after {@code awayTicks} the holding pattern takes over (and picks what comes next). */
	protected void leaveAfter(int awayTicks) {
		if (ticks > awayTicks) dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
	}

	@Override
	public boolean attacks() {
		return true;
	}

	@Nullable
	@Override
	public Vec3 getFlyTargetLocation() {
		return waypoint;
	}
}
