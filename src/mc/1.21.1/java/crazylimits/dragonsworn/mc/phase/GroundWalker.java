package crazylimits.dragonsworn.mc.phase;

import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.Targets;
import crazylimits.dragonsworn.nav.GroundPlanner;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A landed dragon's steps (server): turning on the spot ({@link #turnToward}) and walking a path round
 * what is in the way ({@link GroundPlanner}), its body stopped by walls ({@code HullCollision#walk}). A bent
 * path (a detour) is walked even where it leads away from the target ({@link #detouring}).
 */
final class GroundWalker {
	/** Walking speed, blocks per tick (the walk animation speeds up to match). */
	static final double WALK_SPEED = 0.11;
	/** Degrees per tick it can turn: slowly enough for its feet to step round after it ({@code TurnSteps}). */
	static final float TURN_SPEED = 3.0F;
	/** Ticks between plans of the path, and the planner's node budget. */
	private static final int REPATH_TICKS = 20, PLAN_BUDGET = 1500;

	private final EnderDragon dragon;
	private List<int[]> path = List.of();
	private int pathIndex, repathAt, blockedTicks;
	/** The path bends (more than one straight leg): a way round something. */
	private boolean detour;

	GroundWalker(EnderDragon dragon) {
		this.dragon = dragon;
	}

	/** Drops the path walked (a new landing): the next walk plans afresh. */
	void forgetPath() {
		path = List.of();
	}

	/** Turns toward x, z by at most {@link #TURN_SPEED}; the head leads (the procedural body). */
	boolean turnToward(double x, double z) {
		float want = (float) Math.toDegrees(Math.atan2(x - dragon.getX(), -(z - dragon.getZ())));
		float turn = Mth.clamp(Mth.wrapDegrees(want - dragon.getYRot()), -TURN_SPEED, TURN_SPEED);
		dragon.setYRot(dragon.getYRot() + turn);
		dragon.yRotA = 0.0F;
		return Math.abs(Mth.wrapDegrees(want - dragon.getYRot())) < 20.0F;
	}

	/**
	 * One step along a path toward x, z, stopping {@code reach} blocks short; {@code ticks}: the phase's
	 * clock (the path is planned again every {@link #REPATH_TICKS}). Returns false when there is nowhere to
	 * go (arrived, or no path).
	 */
	boolean walkToward(double x, double z, double reach, int ticks) {
		if (Math.hypot(x - dragon.getX(), z - dragon.getZ()) <= reach) return false;
		if (path.isEmpty() || pathIndex >= path.size() || ticks >= repathAt) {
			repathAt = ticks + REPATH_TICKS;
			path = new GroundPlanner(DragonswornDragon.brain(dragon).grid()).plan(Mth.floor(dragon.getX()), Mth.floor(dragon.getZ()), Mth.floor(x), Mth.floor(z), reach, PLAN_BUDGET);
			pathIndex = 0;
			detour = path.size() > 1;
			if (path.isEmpty()) {
				turnToward(x, z);
				return false;
			}
		}
		int[] next = path.get(pathIndex);
		double nx = next[0] + 0.5, nz = next[2] + 0.5;
		if (Math.hypot(nx - dragon.getX(), nz - dragon.getZ()) < 1.0) {
			pathIndex++;
			return pathIndex < path.size();
		}
		if (!turnToward(nx, nz)) return true;          // face the way first, then walk
		Vec3 facing = Targets.facing(dragon.getYRot());
		double step = Math.min(WALK_SPEED, Math.hypot(nx - dragon.getX(), nz - dragon.getZ()));
		// the body bumps into what the path squeezed past: slide along it and look for another way
		if (DragonswornDragon.brain(dragon).hull.walk(facing.x * step, facing.z * step)) {
			blockedTicks = 0;
		} else if (++blockedTicks % 10 == 0) {
			repathAt = ticks;
			if (blockedTicks >= 60) {
				blockedTicks = 0;
				path = List.of();
				return false;
			}
		}
		return true;
	}

	/** Walking a path round something, its last leg not yet begun. */
	boolean detouring() {
		return detour && pathIndex < path.size() - 1;
	}
}
