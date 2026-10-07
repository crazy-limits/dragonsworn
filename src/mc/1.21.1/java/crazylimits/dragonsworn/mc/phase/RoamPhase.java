package crazylimits.dragonsworn.mc.phase;

import crazylimits.dragonsworn.ai.Roaming;
import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.flight.FlightModel;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonPhases;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.Targets;
import crazylimits.dragonsworn.nav.BlockGrid;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * A wild dragon's flight: it wanders across the land ({@link Roaming}), leg after leg at a height above
 * the terrain, each leg bending the last a little, keeping to where the world is loaded and ticking.
 * It runs the attacks the brain starts:
 * <ul>
 *   <li><b>pass</b>: a low run at the target, one fireball when lined up, then on past it;</li>
 *   <li><b>barrage</b>: hovers off to one side of the target, facing it, and fires a few fireballs ({@code [fireballs]}).</li>
 * </ul>
 * Each holds its course while a fireball heats up, and turns away only once it has flown. A target that
 * hides from it ({@link #BLIND_TICKS}) or that it overflies without a shot is left.
 */
public class RoamPhase extends AbstractDragonPhaseInstance implements DragonswornPhase {
	private enum Hunt { NONE, PASS, BARRAGE }

	/** Ticks the target may stay out of sight of a barrage's standoff before it gives up. */
	private static final int BLIND_TICKS = 60;
	/** A pass that comes this close across the ground (blocks) without a shot has missed its chance. */
	private static final double OVERFLOWN = 8.0;

	@Nullable
	private Vec3 waypoint;
	private double heading;
	private Hunt hunt = Hunt.NONE;
	@Nullable
	private LivingEntity target;
	private int huntTicks, charge, shots, blindTicks;
	/** The pass's fireball is charging: it flies on at the target until it is out. */
	private boolean fired;
	@Nullable
	private Vec3 standoff;

	public RoamPhase(EnderDragon dragon) {
		super(dragon);
	}

	@Override
	public EnderDragonPhase<RoamPhase> getPhase() {
		return DragonPhases.ROAM;
	}

	@Override
	public void begin() {
		waypoint = null;
		hunt = Hunt.NONE;
		target = null;
		Vec3 v = dragon.getDeltaMovement();
		heading = v.horizontalDistanceSqr() > 0.01 ? Math.atan2(v.z, v.x) : Roaming.headingOfYaw(dragon.getYRot());
	}

	private DragonBrain brain() {
		return DragonswornDragon.brain(dragon);
	}

	@Override
	public void end() {
		brain().setLookTarget(null);
	}

	/** The attack it is running (none, pass, barrage), for the showcase. */
	public String hunt() {
		return hunt.name();
	}

	/** Free to start an attack. */
	public boolean idle() {
		return hunt == Hunt.NONE;
	}

	public void startPass(LivingEntity target) {
		this.target = target;
		hunt = Hunt.PASS;
		huntTicks = charge = 0;
		fired = false;
	}

	public void startBarrage(LivingEntity target) {
		this.target = target;
		hunt = Hunt.BARRAGE;
		huntTicks = shots = blindTicks = 0;
		// hover 22 blocks from the target, on the side the dragon comes from, 12 up
		Vec3 away = dragon.position().subtract(target.position()).multiply(1, 0, 1).normalize();
		standoff = target.position().add(away.scale(22.0)).add(0.0, 12.0, 0.0);
	}

	@Nullable
	@Override
	public LivingEntity attackTarget() {
		return hunt != Hunt.NONE ? target : null;
	}

	/** What a hovering dragon keeps its head toward. */
	@Nullable
	@Override
	public Vec3 hoverLook() {
		return hunt == Hunt.BARRAGE && target != null ? target.position() : null;
	}

	@Override
	public FlightModel.Force flightForce() {
		if (hunt == Hunt.BARRAGE && standoff != null && standoff.distanceToSqr(dragon.position()) < 24 * 24) return FlightModel.Force.HOVER;
		return FlightModel.Force.NONE;
	}

	@Override
	public float getFlySpeed() {
		return hunt == Hunt.PASS ? 1.2F : 0.8F;
	}

	@Override
	public void doServerTick() {
		if (hunt != Hunt.NONE && (target == null || !target.isAlive() || ++huntTicks > 600
				|| Targets.untouchable(target))) {
			hunt = Hunt.NONE;
			waypoint = null;
		}
		brain().setLookTarget(hunt != Hunt.NONE ? target : null);
		switch (hunt) {
			case PASS -> pass();
			case BARRAGE -> barrage();
			case NONE -> wander();
		}
	}

	/**
	 * The next leg once the last is nearly flown (or ran into something: then it swings wider). Legs
	 * ending where entities stop ticking are refused, so it turns back at the edge of the loaded world;
	 * over water it cruises above the sea.
	 */
	private void wander() {
		boolean blocked = dragon.horizontalCollision || dragon.verticalCollision;
		if (waypoint != null && waypoint.distanceToSqr(dragon.position()) > 12 * 12 && !blocked) return;
		DragonBrain brain = brain();
		BlockGrid grid = brain.grid();
		RandomGenerator random = ThreadLocalRandom.current();
		int here = grid.ground(dragon.getBlockX(), dragon.getBlockZ());
		if (here == BlockGrid.NO_GROUND) here = dragon.level().getSeaLevel();
		for (int attempt = blocked ? 2 : 0; attempt < 8; attempt++) {
			double h = Roaming.nextHeading(heading, attempt, random);
			double leg = Roaming.flyLeg(random);
			int x = Mth.floor(dragon.getX() + Math.cos(h) * leg), z = Mth.floor(dragon.getZ() + Math.sin(h) * leg);
			if (!brain.ticking(x, z)) continue;
			int ground = grid.ground(x, z);
			if (ground == BlockGrid.NO_GROUND) ground = dragon.level().getSeaLevel();
			heading = h;
			waypoint = new Vec3(x + 0.5, Math.max(ground, here) + Roaming.cruise(random), z + 0.5);
			return;
		}
		// boxed in by unloaded world: head for the nearest player, where the world is loaded
		Player player = dragon.level().getNearestPlayer(dragon, 512.0);
		if (player != null) {
			heading = Math.atan2(player.getZ() - dragon.getZ(), player.getX() - dragon.getX());
			waypoint = new Vec3(player.getX(), Math.max(player.getY(), here) + DragonConfig.CRUISE_MIN.get(), player.getZ());
		}
	}

	private void pass() {
		double dx = target.getX() - dragon.getX(), dz = target.getZ() - dragon.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		// vanilla strafe's line: lower as it gets closer
		double up = Math.min(0.4 + distance / 80.0 - 1.0, 10.0) + 6.0;
		waypoint = new Vec3(target.getX(), target.getY() + up, target.getZ());
		Vec3 facing = Targets.facing(dragon.getYRot());
		if (fired) {
			// on at the target while the fireball heats up; once it is out, on past the target
			if (!brain().fireballs.charging()) passOn(facing);
			return;
		}
		if (distance < OVERFLOWN) {
			passOn(facing);
			return;
		}
		if (distance < 64 && dragon.hasLineOfSight(target)) {
			charge++;
			Vec3 to = new Vec3(dx, 0, dz).normalize();
			if (charge >= 5 && facing.dot(to) > Math.cos(Math.toRadians(10))) {
				fireball();
				fired = true;
			}
		} else if (charge > 0) {
			charge--;
		}
	}

	/** The pass is over: carry on past the target the way it faces, and wander on from there. */
	private void passOn(Vec3 facing) {
		hunt = Hunt.NONE;
		waypoint = dragon.position().add(facing.scale(40.0)).add(0.0, 10.0, 0.0);
		heading = Math.atan2(facing.z, facing.x);
	}

	private void barrage() {
		waypoint = standoff;
		// the last shot heats up facing the target: away only once it is out
		if (shots >= DragonConfig.BARRAGE_SHOTS.get()) {
			if (!brain().fireballs.charging()) {
				hunt = Hunt.NONE;
				waypoint = null;
			}
			return;
		}
		if (standoff.distanceToSqr(dragon.position()) > 10 * 10) return;
		// the target hides behind something: no hovering there staring at the cover
		if (!dragon.hasLineOfSight(target)) {
			if (++blindTicks > BLIND_TICKS && !brain().fireballs.charging()) {
				hunt = Hunt.NONE;
				waypoint = null;
			}
			return;
		}
		blindTicks = 0;
		if (huntTicks % DragonConfig.BARRAGE_INTERVAL.get() == 0 && !brain().fireballs.charging()) {
			Vec3 to = target.position().subtract(dragon.position()).multiply(1, 0, 1).normalize();
			if (Targets.facing(dragon.getYRot()).dot(to) > Math.cos(Math.toRadians(25))) {
				fireball();
				shots++;
			}
		}
	}

	/** Heats up, then fires (the brain times the shot). */
	private void fireball() {
		brain().fireballs.charge(target);
	}

	@Nullable
	@Override
	public Vec3 getFlyTargetLocation() {
		return waypoint;
	}
}
