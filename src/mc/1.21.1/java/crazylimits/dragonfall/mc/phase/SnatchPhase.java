package crazylimits.dragonfall.mc.phase;

import crazylimits.dragonfall.body.Grip;
import crazylimits.dragonfall.flight.FlightModel;
import crazylimits.dragonfall.mc.DragonBrain;
import crazylimits.dragonfall.mc.DragonPhases;
import crazylimits.dragonfall.mc.DragonfallDragon;
import crazylimits.dragonfall.mc.PreyHold;
import crazylimits.dragonfall.nav.BlockGrid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The snatch, an eagle's: the dragon swings out to get a run at its prey, dives at it with both hind legs
 * thrown forward and the right foot reaching out for it at the last, and takes it in its talons ({@link Grip.Hold#TALON}) as it sweeps past. Then
 * it climbs hard, beating, and drops the prey from {@link #DROP_MIN}..{@link #DROP_MAX} blocks up.
 *
 * <p>Held, the prey cannot move but can use items ({@link PreyHold}): an ender pearl gets it out, and
 * so does killing the dragon (it lets go as it dies); either way the fall is the prey's problem. A dodge
 * is possible too: the talons close only on what is under them as the dragon passes.
 */
public class SnatchPhase extends AbstractDragonPhaseInstance implements DragonfallPhase {
	private enum Stage { RUN_UP, SWOOP, CARRY, AWAY }

	/** How close (blocks) the talons must pass to the prey's feet: across, and up or down. */
	static final double CATCH_RADIUS = 2.0, CATCH_HEIGHT = 2.0;
	/** Distance it swings out to before the dive, and from where the dive aims down at the prey. */
	static final double RUN_UP = 40.0, DIVE_FROM = 32.0;
	/** The swoop's glide path: height over the prey per block out, and its top speed (blocks per tick). */
	static final double GLIDE_SLOPE = 0.4, MAX_SWOOP_SPEED = 1.4;
	/** From this close the flight homes in on the prey. */
	static final double HOME_IN = 24.0;
	static final double DROP_MIN = 26.0, DROP_MAX = 40.0;
	static final int RUN_UP_TICKS = 200, SWOOP_TICKS = 300, CARRY_TICKS = 240, AWAY_TICKS = 50;
	/** It never starts a snatch at prey further than this. */
	static final double MAX_RANGE = 96.0;

	@Nullable
	private LivingEntity target;
	private Stage stage = Stage.RUN_UP;
	private int ticks;
	/** The dive's direction across the ground (unit), and its run-up point. */
	private Vec3 heading = Vec3.ZERO;
	@Nullable
	private Vec3 waypoint;
	private double dropAt;

	public SnatchPhase(EnderDragon dragon) {
		super(dragon);
	}

	/** Starts a snatch at {@code target} when it can be held and there is open sky over it. */
	public static boolean start(EnderDragon dragon, LivingEntity target) {
		if (!PreyHold.holdable(target) || target.isPassenger() || target.distanceToSqr(dragon) > MAX_RANGE * MAX_RANGE
				|| !openAbove(DragonfallDragon.brain(dragon).grid(), target)) return false;
		dragon.getPhaseManager().setPhase(DragonPhases.SNATCH);
		dragon.getPhaseManager().getPhase(DragonPhases.SNATCH).target = target;
		return true;
	}

	/** Nothing solid over the prey for the foot to come down through (a roof, a tree's trunk, a cave). */
	static boolean openAbove(BlockGrid grid, LivingEntity target) {
		int x0 = target.getBlockX(), y0 = Mth.floor(target.getY() + target.getBbHeight()), z0 = target.getBlockZ();
		for (int x = x0 - 1; x <= x0 + 1; x++) {
			for (int z = z0 - 1; z <= z0 + 1; z++) {
				for (int y = y0; y < y0 + 10; y++) {
					if (grid.blocked(x, y, z)) return false;
				}
			}
		}
		return true;
	}

	@Override
	public EnderDragonPhase<SnatchPhase> getPhase() {
		return DragonPhases.SNATCH;
	}

	private DragonBrain brain() {
		return DragonfallDragon.brain(dragon);
	}

	@Override
	public void begin() {
		target = null;
		stage = Stage.RUN_UP;
		ticks = 0;
		waypoint = null;
		heading = Vec3.ZERO;
	}

	@Override
	public void end() {
		if (dragon.level().isClientSide) return;
		if (brain().prey.hold() != Grip.Hold.NONE) brain().prey.release(null);
		brain().setLookTarget(null);
	}

	@Override
	public void doServerTick() {
		ticks++;
		switch (stage) {
			case RUN_UP -> runUp();
			case SWOOP -> swoop();
			case CARRY -> carry();
			case AWAY -> {
				if (ticks > AWAY_TICKS) dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
			}
		}
	}

	private boolean lost() {
		return target == null || !target.isAlive() || target.isPassenger() || !PreyHold.holdable(target)
				|| target.distanceToSqr(dragon) > 2 * MAX_RANGE * MAX_RANGE;
	}

	/** Too close to dive at it: swing out away from the prey first, then turn in. */
	private void runUp() {
		if (lost() || ticks > RUN_UP_TICKS) {
			away();
			return;
		}
		brain().setLookTarget(target);
		Vec3 out = dragon.position().subtract(target.position()).multiply(1, 0, 1);
		double distance = out.length();
		if (distance > RUN_UP - 4.0) {
			stage = Stage.SWOOP;
			ticks = 0;
			return;
		}
		if (waypoint == null) {
			Vec3 dir = distance > 1e-3 ? out.scale(1.0 / distance) : dragon.getLookAngle().multiply(-1, 0, -1).normalize();
			waypoint = target.position().add(dir.scale(RUN_UP + 8.0)).add(0.0, 14.0, 0.0);
		}
	}

	/**
	 * The dive: it steers so its talons pass through the prey's feet, past it along the line it came in
	 * on, and sinks at the rate that brings them down to it on arrival. Closes on whatever is under them.
	 */
	private void swoop() {
		if (lost() || ticks > SWOOP_TICKS) {
			away();
			return;
		}
		DragonBrain brain = brain();
		brain.setLookTarget(target);
		Vec3 talons = brain.prey.talonPoint(target);
		Vec3 prey = target.position();
		if (Math.hypot(prey.x - talons.x, prey.z - talons.z) < CATCH_RADIUS && Math.abs(prey.y - talons.y) < CATCH_HEIGHT) {
			seize();
			return;
		}
		double dx = prey.x - dragon.getX(), dz = prey.z - dragon.getZ();
		double distance = Math.hypot(dx, dz);
		if (distance > 1e-3 && (distance > 20.0 || heading == Vec3.ZERO)) heading = new Vec3(dx / distance, 0.0, dz / distance);
		// passed it by (it dodged, or the dive came in wrong): no second try
		if (dx * heading.x + dz * heading.z < -6.0) {
			away();
			return;
		}
		if (distance < 26.0) brain.prey.reach(target);
		// where the dragon must be for its talons to be at the prey's feet, and on past it; down a glide
		// slope onto it, so it comes in low and level instead of dropping out of the sky at the end
		Vec3 meet = prey.subtract(talons.subtract(dragon.position()));
		double height = meet.y + Math.max(0.0, distance - 4.0) * GLIDE_SLOPE;
		waypoint = new Vec3(meet.x + heading.x * 12.0, height, meet.z + heading.z * 12.0);
		Vec3 v = dragon.getDeltaMovement();
		double horizontal = v.horizontalDistance();
		// down the slope at the speed it flies, and back onto it when above or below
		double want = Mth.clamp(-horizontal * GLIDE_SLOPE * (distance > 4.0 ? 1.0 : 0.0) + (height - dragon.getY()) * 0.3, -0.9, 0.4);
		double vx = v.x, vz = v.z;
		// the last stretch: home in, the flight bent onto the prey (the steering alone lags a turn behind)
		double mx = meet.x - dragon.getX(), mz = meet.z - dragon.getZ(), m = Math.hypot(mx, mz);
		if (distance < HOME_IN && m > 1.0 && horizontal > 0.1 && (mx * v.x + mz * v.z) > 0.0) {
			double k = 0.35;
			vx = vx * (1.0 - k) + mx / m * horizontal * k;
			vz = vz * (1.0 - k) + mz / m * horizontal * k;
			double h = Math.hypot(vx, vz);
			vx *= horizontal / h;
			vz *= horizontal / h;
		}
		double keep = horizontal > MAX_SWOOP_SPEED ? MAX_SWOOP_SPEED / horizontal : 1.0;
		dragon.setDeltaMovement(vx * keep, v.y + (want - v.y) * 0.4, vz * keep);
	}

	private void seize() {
		DragonBrain brain = brain();
		if (!brain.prey.seize(target, Grip.Hold.TALON)) {
			away();
			return;
		}
		stage = Stage.CARRY;
		ticks = 0;
		dropAt = target.getY() + DROP_MIN + ThreadLocalRandom.current().nextDouble() * (DROP_MAX - DROP_MIN);
		dragon.playSound(SoundEvents.RAVAGER_ATTACK, 3.0F, 0.5F);
	}

	/** Climbing hard with the prey; at the height it drops it, with a roar, and flies on. */
	private void carry() {
		DragonBrain brain = brain();
		if (!brain.prey.holding()) {      // it got away (an ender pearl), or died
			away();
			return;
		}
		// the action bar first says the vanilla mount line: say what happened instead, a moment later
		if (ticks == 3 && target instanceof ServerPlayer player) player.displayClientMessage(Component.translatable("dragonfall.snatched"), true);
		float yaw = dragon.getYRot() * Mth.DEG_TO_RAD;
		Vec3 facing = new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw));
		waypoint = dragon.position().add(facing.scale(30.0)).add(0.0, dropAt + 6.0 - dragon.getY(), 0.0);
		// hard beats: it climbs steeper than its cruise would
		Vec3 v = dragon.getDeltaMovement();
		dragon.setDeltaMovement(v.x, v.y + (0.35 - v.y) * 0.12, v.z);
		if (dragon.getY() >= dropAt || ticks > CARRY_TICKS || dragon.verticalCollision) {
			brain.prey.release(null);
			brain.roar();
			away();
		}
	}

	private void away() {
		DragonBrain brain = brain();
		if (brain.prey.hold() != Grip.Hold.NONE) brain.prey.release(null);
		brain.setLookTarget(null);
		stage = Stage.AWAY;
		ticks = 0;
		float yaw = dragon.getYRot() * Mth.DEG_TO_RAD;
		waypoint = dragon.position().add(Mth.sin(yaw) * 40.0, 8.0, -Mth.cos(yaw) * 40.0);
	}

	@Override
	public FlightModel.Force flightForce() {
		return switch (stage) {
			case CARRY -> FlightModel.Force.FLY;
			case SWOOP -> heading != Vec3.ZERO && target != null && target.distanceToSqr(dragon) < DIVE_FROM * DIVE_FROM
					? FlightModel.Force.GLIDE : FlightModel.Force.NONE;
			default -> FlightModel.Force.NONE;
		};
	}

	@Override
	public float getFlySpeed() {
		return stage == Stage.SWOOP ? 1.4F : 1.0F;
	}

	@Nullable
	@Override
	public Vec3 getFlyTargetLocation() {
		return waypoint;
	}
}
