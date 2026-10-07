package crazylimits.dragonsworn.mc.phase;

import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.nav.BlockGrid;
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
 *
 * <p>Everything here is in the frame of the face it stands on ({@code DragonBrain#local}): on a wall the
 * wall is the ground, so it climbs exactly as it walks. Points given are in that frame too. On a wall it
 * stays upright: it never turns off heading straight up the face, and only climbs straight up it (going
 * anywhere else on a wall is a hop, {@code HopPhase}).
 */
final class GroundWalker {
	/** Walking speed, blocks per tick (the walk animation speeds up to match). */
	static final double WALK_SPEED = 0.11;
	/** Degrees per tick it can turn: slowly enough for its feet to step round after it ({@code TurnSteps}). */
	static final float TURN_SPEED = 3.0F;
	/** Degrees off its next leg beyond which it stops to turn on the spot before walking on. */
	static final float WALK_TURN = 45.0F;
	/** On a wall, how far ahead of its middle the face must go on for it to climb on (blocks: its front feet). */
	static final double CLIMB_LOOK = 4.5;
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
		return turn(x, z) < 20.0F;
	}

	/** {@link #turnToward}; returns the degrees still left to turn. */
	private float turn(double x, double z) {
		DragonBrain brain = DragonswornDragon.brain(dragon);
		Vec3 at = brain.local();
		// upright on a wall, whatever it looks at
		float want = brain.face().wall() ? brain.face().upYaw() : (float) Math.toDegrees(Math.atan2(x - at.x, -(z - at.z)));
		float turn = Mth.clamp(Mth.wrapDegrees(want - dragon.getYRot()), -TURN_SPEED, TURN_SPEED);
		dragon.setYRot(dragon.getYRot() + turn);
		dragon.yRotA = 0.0F;
		return Math.abs(Mth.wrapDegrees(want - dragon.getYRot()));
	}

	/**
	 * One step along a path toward x, z, stopping {@code reach} blocks short; {@code ticks}: the phase's
	 * clock (the path is planned again every {@link #REPATH_TICKS}). Returns false when there is nowhere to
	 * go (arrived, or no path).
	 */
	boolean walkToward(double x, double z, double reach, int ticks) {
		DragonBrain brain = DragonswornDragon.brain(dragon);
		Vec3 at = brain.local();
		if (Math.hypot(x - at.x, z - at.z) <= reach) return false;
		if (brain.face().wall()) return climb(brain, at, x, z, reach);
		if (path.isEmpty() || pathIndex >= path.size() || ticks >= repathAt) {
			repathAt = ticks + REPATH_TICKS;
			path = new GroundPlanner(DragonswornDragon.brain(dragon).localGrid()).plan(Mth.floor(at.x), Mth.floor(at.z), Mth.floor(x), Mth.floor(z), reach, PLAN_BUDGET);
			pathIndex = 0;
			detour = path.size() > 1;
			if (path.isEmpty()) {
				turnToward(x, z);
				return false;
			}
		}
		// past the nodes it has reached, on to the next in the same tick: a tick without a step would stall the walk
		while (Math.hypot(path.get(pathIndex)[0] + 0.5 - at.x, path.get(pathIndex)[2] + 0.5 - at.z) < 1.0) {
			if (++pathIndex >= path.size()) return false;
		}
		int[] next = path.get(pathIndex);
		double nx = next[0] + 0.5, nz = next[2] + 0.5;
		// a sharp turn is made on the spot first; a gentle one is walked round (stopping for it would stall the walk)
		if (turn(nx, nz) > WALK_TURN) return true;
		Vec3 facing = Targets.facing(dragon.getYRot());
		double step = Math.min(WALK_SPEED, Math.hypot(nx - at.x, nz - at.z));
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

	/**
	 * On a wall: a step straight up the face while (x, z) is still above it (it climbs no other way), as
	 * long as there is wall to grip ahead. False when there is nowhere to climb.
	 */
	private boolean climb(DragonBrain brain, Vec3 at, double x, double z, double reach) {
		if (turn(x, z) > WALK_TURN) return true;
		Vec3 up = Targets.facing(brain.face().upYaw());
		double ahead = (x - at.x) * up.x + (z - at.z) * up.z;
		if (ahead < Math.max(1.0, reach * 0.5)) return false;
		// the face must go on under its front feet a little way up
		BlockGrid grid = brain.localGrid();
		int here = grid.ground(Mth.floor(at.x), Mth.floor(at.z));
		int there = grid.ground(Mth.floor(at.x + up.x * CLIMB_LOOK), Mth.floor(at.z + up.z * CLIMB_LOOK));
		if (there == BlockGrid.NO_GROUND || Math.abs(there - here) > GroundPlanner.STEP_UP) return false;
		double step = Math.min(WALK_SPEED, ahead);
		if (brain.hull.walk(up.x * step, up.z * step)) return true;
		return ++blockedTicks % 60 != 0;
	}

	/** Walking a path round something, its last leg not yet begun. */
	boolean detouring() {
		return detour && pathIndex < path.size() - 1;
	}
}
