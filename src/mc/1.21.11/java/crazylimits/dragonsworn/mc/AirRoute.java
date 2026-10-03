package crazylimits.dragonsworn.mc;

import crazylimits.dragonsworn.nav.AirPlanner;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Where a flying dragon steers on its way to a target (server): straight at it when the way is clear,
 * else along a planned route round what is in the way ({@link AirPlanner}), swerving at once from what its
 * momentum is about to carry it into.
 */
public final class AirRoute {
	/** Height of the flying hull's center above the dragon's position (blocks). */
	static final double BODY_CENTER = 3.6;
	/** Flight: how far ahead (ticks of its velocity) it looks for what it is about to fly into. */
	private static final double LOOKAHEAD_TICKS = 14.0;
	/** Ticks between checks that the way ahead is still clear, and between full replans of a route. */
	private static final int CHECK_TICKS = 5, REPLAN_TICKS = 40;
	/** How far ahead the straight way is checked (blocks), and the planner's node budget. */
	private static final double PROBE = 48.0;
	private static final int PLAN_BUDGET = 1500;
	/** A waypoint this close (squared blocks) is reached; a target that moved this far is planned for anew. */
	private static final double REACHED_SQ = 36.0, MOVED_SQ = 36.0;
	/** With no route found it climbs: this far up, a little behind where it looks. */
	private static final double CLIMB = 12.0, CLIMB_BACK = 6.0;

	private final DragonBrain brain;
	private List<double[]> route = List.of();
	private int routeIndex;
	private boolean routeClimb;
	private Vec3 routeGoal;
	private long routeCheckedAt = Long.MIN_VALUE, replanAt = Long.MIN_VALUE;
	/** A swerve away from what is straight ahead, held until {@link #evadeUntil}. */
	private Vec3 evade;
	private long evadeUntil = Long.MIN_VALUE;

	AirRoute(DragonBrain brain) {
		this.brain = brain;
	}

	/** Plans again on the next tick (it bumped into something). */
	void replanNow() {
		replanAt = Long.MIN_VALUE;
	}

	/**
	 * Where to steer for {@code target}: straight at it when the way is clear (checked {@link #PROBE} blocks
	 * ahead), else the next waypoint of a planned route around what is in the way; climbing when no route is
	 * found. Between replans the route is kept while its next leg is clear, waypoints already in straight view
	 * are skipped, and whatever its momentum is carrying it into within {@link #LOOKAHEAD_TICKS} makes it
	 * swerve at once ({@link #swerve}) and plan again.
	 */
	Vec3 steer(Vec3 target, long tick) {
		EnderDragon dragon = brain.dragon();
		Vec3 center = dragon.position().add(0.0, BODY_CENTER, 0.0);
		double[] from = {center.x, center.y, center.z};
		AirPlanner planner = null;
		boolean replan = routeGoal == null || routeGoal.distanceToSqr(target) > MOVED_SQ || tick >= replanAt;
		if (!replan && tick - routeCheckedAt >= CHECK_TICKS) {
			routeCheckedAt = tick;
			planner = new AirPlanner(brain.grid());
			Vec3 ahead = center.add(dragon.getDeltaMovement().scale(LOOKAHEAD_TICKS));
			if (!planner.lineClear(from, new double[]{ahead.x, ahead.y, ahead.z})) {
				replan = true;
			} else if (routeIndex < route.size() && !planner.lineClear(from, route.get(routeIndex))) {
				replan = true;
			}
		}
		if (replan) {
			routeCheckedAt = tick;
			routeGoal = target;
			if (planner == null) planner = new AirPlanner(brain.grid());
			Vec3 goal = target.add(0.0, BODY_CENTER, 0.0);
			Vec3 toGoal = goal.subtract(center);
			Vec3 probe = toGoal.length() > PROBE ? center.add(toGoal.normalize().scale(PROBE)) : goal;
			if (planner.lineClear(from, new double[]{probe.x, probe.y, probe.z})) {
				route = List.of();
				routeClimb = false;
				replanAt = tick + CHECK_TICKS * 2;
			} else {
				route = planner.plan(from, new double[]{goal.x, goal.y, goal.z}, PLAN_BUDGET);
				routeIndex = 0;
				routeClimb = route.isEmpty();
				replanAt = tick + REPLAN_TICKS;
			}
			// still carried toward a wall the new plan steers away from: swerve until it can turn
			Vec3 v = dragon.getDeltaMovement();
			Vec3 ahead = center.add(v.scale(LOOKAHEAD_TICKS));
			if (v.lengthSqr() > 0.01 && !planner.lineClear(from, new double[]{ahead.x, ahead.y, ahead.z})) {
				Vec3 next = routeClimb ? center.add(0.0, CLIMB, 0.0)
						: routeIndex < route.size() ? new Vec3(route.get(routeIndex)[0], route.get(routeIndex)[1], route.get(routeIndex)[2]) : goal;
				evade = swerve(planner, center, v, next);
				evadeUntil = evade == null ? Long.MIN_VALUE : tick + CHECK_TICKS * 2;
			}
		}
		if (evade != null && tick < evadeUntil) return evade.subtract(0.0, BODY_CENTER, 0.0);
		evade = null;
		if (routeClimb) return dragon.position().add(dragon.getLookAngle().scale(-CLIMB_BACK)).add(0.0, CLIMB, 0.0);
		while (routeIndex < route.size() && distanceSq(center, route.get(routeIndex)) < REACHED_SQ) routeIndex++;
		// a later waypoint already in straight view: cut the corner
		if (planner != null) {
			while (routeIndex + 1 < route.size() && planner.lineClear(from, route.get(routeIndex + 1))) routeIndex++;
		}
		if (routeIndex >= route.size()) return target;
		double[] p = route.get(routeIndex);
		return new Vec3(p[0], p[1] - BODY_CENTER, p[2]);
	}

	/**
	 * A point to swerve to when its momentum carries it into terrain: the clear direction (turned up to
	 * 90 degrees either way, pitched up or down, or straight up) nearest to where it wants to go next,
	 * or null when none is clear.
	 */
	private static Vec3 swerve(AirPlanner planner, Vec3 center, Vec3 velocity, Vec3 next) {
		double reach = Math.max(10.0, velocity.length() * LOOKAHEAD_TICKS);
		Vec3 forward = velocity.normalize();
		Vec3 want = next.subtract(center).normalize();
		double[] from = {center.x, center.y, center.z};
		Vec3 best = null;
		double bestScore = -Double.MAX_VALUE;
		for (int yaw = -90; yaw <= 90; yaw += 30) {
			for (int pitch : new int[]{0, 35, -25, 70}) {
				Vec3 dir = forward.yRot(yaw * Mth.DEG_TO_RAD);
				double horizontal = Math.max(1e-3, dir.horizontalDistance());
				double up = Math.tan(pitch * Mth.DEG_TO_RAD) * horizontal;
				dir = new Vec3(dir.x, Mth.clamp(dir.y + up, -0.9, 3.0), dir.z).normalize();
				Vec3 to = center.add(dir.scale(reach));
				if (!planner.lineClear(from, new double[]{to.x, to.y, to.z})) continue;
				// toward the next waypoint, and as little of a swerve as will do
				double score = dir.dot(want) + 0.5 * dir.dot(forward);
				if (score > bestScore) {
					bestScore = score;
					best = to;
				}
			}
		}
		if (best != null) return best;
		Vec3 up = center.add(0.0, reach, 0.0);
		return planner.lineClear(from, new double[]{up.x, up.y, up.z}) ? up : null;
	}

	private static double distanceSq(Vec3 a, double[] b) {
		double dx = a.x - b[0], dy = a.y - b[1], dz = a.z - b[2];
		return dx * dx + dy * dy + dz * dz;
	}
}
