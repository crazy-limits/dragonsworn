package crazylimits.dragonsworn.mc;

import crazylimits.dragonsworn.flight.FlightModel;
import crazylimits.dragonsworn.flight.HoverLift;
import crazylimits.dragonsworn.mc.phase.DragonswornPhase;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Flying, driven by the wings (server; replaces vanilla's steering): the {@link FlightModel} decides when
 * the wings beat, glide or hover, and every downstroke's thrust and lift move the dragon. Terrain is flown
 * round ({@link AirRoute}) and never through ({@link HullCollision#move}).
 */
public final class Flight {
	/** Rise over run past which flight cannot climb to a waypoint: it hovers up instead. */
	private static final double STEEP = 0.8;
	/** A waypoint less than this far above (blocks) is never too steep. */
	private static final double STEEP_MIN_RISE = 4.0;
	/** Vanilla's landing: hovering from this close to the portal (blocks below, across), gliding before. */
	private static final double LANDING_HOVER_BELOW = 14, LANDING_HOVER_ACROSS = 12;

	private final DragonBrain brain;
	private final AirRoute route;
	private final HullCollision hull;
	/** When the wings beat, and how hard (the synced plan's source). */
	final FlightModel model = new FlightModel();
	/** Hovering: how hard each stroke throws it up. */
	private final HoverLift hover = new HoverLift();

	Flight(DragonBrain brain, AirRoute route, HullCollision hull) {
		this.brain = brain;
		this.route = route;
		this.hull = hull;
	}

	/** One tick of flight toward {@code target}, replacing vanilla's (server only). */
	public void fly(DragonPhaseInstance phase, Vec3 target) {
		EnderDragon dragon = brain.dragon();
		long tick = dragon.level().getGameTime();
		boolean collide = collides(phase);
		Vec3 aim = collide ? route.steer(target, tick) : target;
		Vec3 v = dragon.getDeltaMovement();
		FlightModel.Plan before = model.plan();
		FlightModel.Force force = force(phase, target);
		// a way round terrain that goes steeply up (over a wall it is facing): it rises on its wings,
		// hovering; a steep climb the phase asked for itself (the snatch's) is flown
		if (collide && force == FlightModel.Force.NONE && (aim != target || dragon.horizontalCollision) && steep(aim)) {
			force = FlightModel.Force.HOVER;
		}
		FlightModel.Plan plan = model.update(tick, v.y, dragon.yRotA * 0.1, v.horizontalDistance(), force,
				ThreadLocalRandom.current());
		if (plan != before) {
			dragon.getEntityData().set(DragonData.FLIGHT, plan.encode());
			hover.restart();
		}
		if (plan.mode() == FlightModel.Mode.HOVER) hoverStep(phase, aim, tick, collide);
		else flightStep(phase, aim, tick, collide);
	}

	/**
	 * Forces the wings into a mode outside of {@link #fly} (the jump of a takeoff), its beat already at
	 * phase {@code u} (the takeoff's power stroke carries on into the hover's beat).
	 */
	public void forceFlight(FlightModel.Force force, double u) {
		EnderDragon dragon = brain.dragon();
		long tick = dragon.level().getGameTime();
		FlightModel.Plan before = model.plan();
		FlightModel.Plan plan = model.update(tick, 0, 0, 0, force, ThreadLocalRandom.current());
		if (plan != before) {
			model.startAtPhase(tick, u);
			dragon.getEntityData().set(DragonData.FLIGHT, plan.encode());
		}
		hover.restart();
	}

	/** Vanilla's portal landing and takeoff fly through the podium as before; so does a perched dragon. */
	static boolean collides(DragonPhaseInstance phase) {
		if (phase instanceof DragonswornPhase own) return own.collides();
		EnderDragonPhase<?> id = phase.getPhase();
		if (id == EnderDragonPhase.LANDING || id == EnderDragonPhase.TAKEOFF) return false;
		return !phase.isSitting() || id == EnderDragonPhase.HOVERING;
	}

	private FlightModel.Force force(DragonPhaseInstance phase, Vec3 target) {
		if (phase instanceof DragonswornPhase own) return own.flightForce();
		EnderDragon dragon = brain.dragon();
		EnderDragonPhase<?> id = phase.getPhase();
		if (id == EnderDragonPhase.HOVERING) return FlightModel.Force.HOVER;
		if (id == EnderDragonPhase.DYING) return brain.death.hovers(dragon.getX(), dragon.getZ()) ? FlightModel.Force.HOVER : FlightModel.Force.NONE;
		if (id == EnderDragonPhase.LANDING) {
			boolean close = dragon.getY() - target.y < LANDING_HOVER_BELOW
					&& Math.hypot(target.x - dragon.getX(), target.z - dragon.getZ()) < LANDING_HOVER_ACROSS;
			return close ? FlightModel.Force.HOVER : FlightModel.Force.GLIDE;
		}
		if (id == EnderDragonPhase.TAKEOFF || id == EnderDragonPhase.CHARGING_PLAYER) return FlightModel.Force.FLY;
		return FlightModel.Force.NONE;
	}

	/**
	 * Whether {@code aim} is too steeply above for flight to climb to ({@link #STEEP}), or the dragon is
	 * pressed against a wall below it: flying on, it would only scrape up the face.
	 */
	private boolean steep(Vec3 aim) {
		EnderDragon dragon = brain.dragon();
		double dy = aim.y - dragon.getY(), horizontal = Math.hypot(aim.x - dragon.getX(), aim.z - dragon.getZ());
		return dy > STEEP_MIN_RISE && (dy > horizontal * STEEP || dragon.horizontalCollision);
	}

	/**
	 * Level and climbing flight, like vanilla's steering, but driven by the wings: forward acceleration
	 * is the downstroke's thrust plus what the glide gives back, more in a dive, less in a climb. Turning
	 * bleeds speed; slow, it sinks unless it beats (the flight model then beats continuously). The
	 * steering's constants are vanilla's ({@code EnderDragon.aiStep}).
	 */
	private void flightStep(DragonPhaseInstance phase, Vec3 aim, long tick, boolean collide) {
		EnderDragon dragon = brain.dragon();
		double dx = aim.x - dragon.getX(), dy = aim.y - dragon.getY(), dz = aim.z - dragon.getZ();
		double distSq = dx * dx + dy * dy + dz * dz;
		float flySpeed = phase.getFlySpeed();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		double climb = horizontal > 0 ? Mth.clamp(dy / horizontal, -flySpeed, flySpeed) : dy;
		// beating, it climbs in surges on the downstrokes
		dragon.setDeltaMovement(dragon.getDeltaMovement().add(0.0, climb * 0.01 * model.climbPulse(tick), 0.0));
		dragon.setYRot(Mth.wrapDegrees(dragon.getYRot()));
		Vec3 toAim = aim.subtract(dragon.position()).normalize();
		float yawRad = dragon.getYRot() * Mth.DEG_TO_RAD;
		Vec3 facing = new Vec3(Mth.sin(yawRad), dragon.getDeltaMovement().y, -Mth.cos(yawRad)).normalize();
		float align = Math.max(((float) facing.dot(toAim) + 0.5F) / 1.5F, 0.0F);
		if (Math.abs(dx) > 1.0E-5 || Math.abs(dz) > 1.0E-5) {
			float turn = Mth.clamp(Mth.wrapDegrees(180.0F - (float) Mth.atan2(dx, dz) * Mth.RAD_TO_DEG - dragon.getYRot()), -50.0F, 50.0F);
			dragon.yRotA *= 0.8F;
			dragon.yRotA += turn * phase.getTurnSpeed();
			dragon.setYRot(dragon.getYRot() + dragon.yRotA * 0.1F);
		}
		float near = (float) (2.0 / (distSq + 1.0));
		double vy = dragon.getDeltaMovement().y;
		double accel = (FlightModel.GLIDE_ACCEL + model.thrust(tick)) * (align * near + (1.0F - near))
				+ FlightModel.DIVE_GAIN * Math.max(0.0, -vy) - FlightModel.CLIMB_COST * Math.max(0.0, vy);
		dragon.moveRelative((float) Math.max(0.0, accel), new Vec3(0.0, 0.0, -1.0));
		// each downstroke heaves the body up, a lot when slow; slow and not beating, it sinks
		dragon.setDeltaMovement(dragon.getDeltaMovement().add(0.0, model.lift(tick, dragon.getDeltaMovement().horizontalDistance()), 0.0));
		hull.move(dragon.getDeltaMovement(), collide);
		Vec3 dir = dragon.getDeltaMovement().normalize();
		double drag = (0.8 + 0.15 * (dir.dot(facing) + 1.0) / 2.0) * FlightModel.turnDrag(dragon.yRotA * 0.1);
		dragon.setDeltaMovement(dragon.getDeltaMovement().multiply(drag, 0.91F, drag));
	}

	/**
	 * Standing in the air: drifts slowly toward the aim, turns to face it (or the phase's look target),
	 * and bobs on its beats toward the aim's height: every downstroke throws it up, through every
	 * upstroke it nearly falls ({@link HoverLift}).
	 */
	private void hoverStep(DragonPhaseInstance phase, Vec3 aim, long tick, boolean collide) {
		EnderDragon dragon = brain.dragon();
		double dx = aim.x - dragon.getX(), dz = aim.z - dragon.getZ();
		double horizontal = Math.hypot(dx, dz);
		Vec3 look = phase instanceof DragonswornPhase own ? own.hoverLook() : null;
		double fx = look != null ? look.x - dragon.getX() : dx, fz = look != null ? look.z - dragon.getZ() : dz;
		// turning to face: up to 3 degrees a tick, unless it is right over what it faces
		if (Math.hypot(fx, fz) > 1.5) {
			float want = (float) Math.toDegrees(Math.atan2(fx, -fz));
			float turn = Mth.clamp(Mth.wrapDegrees(want - dragon.getYRot()), -3.0F, 3.0F);
			dragon.setYRot(dragon.getYRot() + turn);
			dragon.yRotA = turn * 10.0F;
		} else {
			dragon.yRotA = 0.0F;
		}
		// drifting: toward the aim at up to 0.3 blocks a tick, easing in
		Vec3 v = dragon.getDeltaMovement();
		double want = Math.min(0.3, horizontal * 0.05);
		double tx = horizontal > 1e-3 ? dx / horizontal * want : 0.0, tz = horizontal > 1e-3 ? dz / horizontal * want : 0.0;
		double vx = v.x + (tx - v.x) * 0.08, vz = v.z + (tz - v.z) * 0.08;
		double beat = Math.max(0.0, model.beatPhase(tick));
		dragon.setDeltaMovement(vx, hover.step(v.y, beat, aim.y - dragon.getY()), vz);
		hull.move(dragon.getDeltaMovement(), collide);
	}
}
