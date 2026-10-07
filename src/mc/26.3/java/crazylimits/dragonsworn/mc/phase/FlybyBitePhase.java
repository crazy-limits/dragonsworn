package crazylimits.dragonsworn.mc.phase;

import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.attack.BreathAttack;
import crazylimits.dragonsworn.attack.FlybyBite;
import crazylimits.dragonsworn.body.Strike;
import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.flight.FlightModel;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonPhases;
import crazylimits.dragonsworn.mc.DragonSounds;
import crazylimits.dragonsworn.nav.BlockGrid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The fly-by bite ({@link FlybyBite}): at prey it cannot land by (on a wall, a pillar, in the air) the
 * dragon swings out for a run, comes back gliding fast on the line that takes its jaws through the prey's
 * middle, the body {@link FlybyBite#CLEARANCE} over (and {@link FlybyBite#PULL} behind) where the bite's pose would carry it, and bites in
 * passing ({@link DragonAnim#GLIDE_BITE}). The jaws are aimed by body/Strike's IK at where the prey will be
 * when they close (prey in the air is led by its velocity), until a moment before; whatever is at the
 * jaws then is hit, harder and flung further the faster the dragon flies. Then it flies on.
 */
public class FlybyBitePhase extends AirAttackPhase {
	private enum Stage { RUN_UP, APPROACH, BITE, AWAY }

	static final int RUN_UP_TICKS = 200, APPROACH_TICKS = 300, AWAY_TICKS = 50;
	/**
	 * From this close (horizontal blocks) it settles to the meeting point's height and the pass's speed
	 * (so it comes in level: no stoop at the end), and from {@link #HOME_IN} it homes in on its line (the
	 * steering alone lags a turn behind).
	 */
	static final double LEVEL_OFF = 70.0, HOME_IN = 30.0;

	private Stage stage = Stage.RUN_UP;
	/** The run's direction across the ground (unit), fixed as it comes in. */
	private Vec3 heading = Vec3.ZERO;
	/** Where the jaws are aimed (world): the prey's middle when they close, frozen a moment before. */
	@Nullable
	private Vec3 aim;
	private boolean hit;
	/** The speed that brings it to the meeting point as the jaws close (blocks/tick across the ground): held through the bite. */
	private double passSpeed;
	/** Asks where the jaws would close, without touching the dragon's own aim. */
	private final Strike probe = new Strike();

	public FlybyBitePhase(EnderDragon dragon) {
		super(dragon);
	}

	/** Starts a fly-by bite at {@code target} when it is in range. */
	public static boolean start(EnderDragon dragon, LivingEntity target) {
		if (target.distanceToSqr(dragon) > FlybyBite.MAX_RANGE * FlybyBite.MAX_RANGE) return false;
		begin(dragon, DragonPhases.FLYBY_BITE, target);
		return true;
	}

	@Override
	public EnderDragonPhase<FlybyBitePhase> getPhase() {
		return DragonPhases.FLYBY_BITE;
	}

	@Override
	public void begin() {
		super.begin();
		stage = Stage.RUN_UP;
		aim = null;
		hit = false;
		heading = Vec3.ZERO;
	}

	@Override
	protected boolean leaving() {
		return stage == Stage.AWAY;
	}

	@Override
	public void end() {
		if (dragon.level().isClientSide()) return;
		DragonBrain brain = brain();
		if (brain.action() == DragonAnim.GLIDE_BITE) brain.clearAction();
		brain.setLookTarget(null);
	}

	@Override
	public void doServerTick(ServerLevel serverLevel) {
		ticks++;
		switch (stage) {
			case RUN_UP -> runUp();
			case APPROACH -> approach();
			case BITE -> bite();
			case AWAY -> leaveAfter(AWAY_TICKS);
		}
	}

	private boolean lost() {
		return lostBeyond(2 * FlybyBite.MAX_RANGE);
	}

	/** The prey's middle {@code ticks} from now: where it is, led by how it moves (prey in the air keeps going). */
	private Vec3 preyAt(double ticks) {
		Vec3 v = target.getDeltaMovement();
		// on its feet a jump or a step is no flight path: only what flies is led
		if (target.onGround()) v = Vec3.ZERO;
		return target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).add(v.scale(Math.min(ticks, 30.0)));
	}

	/**
	 * Where the dragon must be when the jaws close on {@code prey}, flying along {@link #heading}: the
	 * bite's resting jaws put on it, the body {@link FlybyBite#CLEARANCE} higher (and higher still, a
	 * little at a time, while that leaves the hull in a block: a wall's parapet, the pillar it stands on).
	 */
	private Vec3 meet(Vec3 prey) {
		double[] rest = Strike.rest(DragonAnim.GLIDE_BITE, brain().body, 1.0F);
		double hx = heading.x, hz = heading.z, rx = -hz, rz = hx, ahead = rest[0] - FlybyBite.PULL;
		Vec3 at = new Vec3(prey.x - hx * ahead - rx * rest[2], prey.y - rest[1] + FlybyBite.CLEARANCE, prey.z - hz * ahead - rz * rest[2]);
		BlockGrid grid = brain().grid();
		for (int i = 0; i < 6 && blocked(grid, at); i++) at = at.add(0.0, 1.0, 0.0);
		return at;
	}

	/** Whether the body (a 3 x 3 x 2 block box about {@code at}) would be in a solid block. */
	private static boolean blocked(BlockGrid grid, Vec3 at) {
		int x0 = Mth.floor(at.x), y0 = Mth.floor(at.y), z0 = Mth.floor(at.z);
		for (int x = x0 - 1; x <= x0 + 1; x++) {
			for (int z = z0 - 1; z <= z0 + 1; z++) {
				for (int y = y0; y <= y0 + 1; y++) {
					if (grid.blocked(x, y, z)) return true;
				}
			}
		}
		return false;
	}

	/** Too close for a run: swing out away from the prey first, a little over it. */
	private void runUp() {
		if (lost() || ticks > RUN_UP_TICKS) {
			away();
			return;
		}
		brain().setLookTarget(target);
		if (runUp(FlybyBite.RUN_UP, 6.0)) {
			stage = Stage.APPROACH;
			ticks = 0;
			heading = Vec3.ZERO;
		}
	}

	/** Back in at the prey on the line through it; the bite starts once lined up, its length short of the meeting point. */
	private void approach() {
		if (lost() || ticks > APPROACH_TICKS) {
			away();
			return;
		}
		brain().setLookTarget(target);
		Vec3 v = dragon.getDeltaMovement();
		double speed = Mth.clamp(v.horizontalDistance(), FlybyBite.MIN_SPEED, FlybyBite.MAX_SPEED);
		Vec3 prey = preyAt(FlybyBite.HIT_TICKS);
		double dx = prey.x - dragon.getX(), dz = prey.z - dragon.getZ();
		double distance = Math.hypot(dx, dz);
		// the line is fixed once it is close: no swinging round onto a prey that sidesteps
		if (distance > 1e-3 && (distance > FlybyBite.startDistance(speed) + 12.0 || heading == Vec3.ZERO)) heading = new Vec3(dx / distance, 0.0, dz / distance);
		Vec3 meet = meet(prey);
		double[] line = FlybyBite.alongLine(meet.x - dragon.getX(), meet.z - dragon.getZ(), heading.x, heading.z);
		// overshot without lining up: a fresh run
		if (line[0] < -2.0) {
			stage = Stage.RUN_UP;
			ticks = 0;
			waypoint = null;
			return;
		}
		waypoint = meet.add(heading.scale(16.0));
		if (distance < LEVEL_OFF) steer(meet, speed, distance < HOME_IN);
		double off = BreathAttack.offFacing(dragon.getYRot(), heading.x, heading.z);
		if (FlybyBite.due(line[0], line[1], off, speed)) {
			stage = Stage.BITE;
			ticks = 0;
			hit = false;
			// exactly there as the jaws close (it may be a tick's flight short of the start)
			passSpeed = Mth.clamp(line[0] / FlybyBite.HIT_TICKS, FlybyBite.MIN_SPEED * 0.8, FlybyBite.MAX_SPEED);
			aim = preyAt(FlybyBite.HIT_TICKS);
			brain().startAction(DragonAnim.GLIDE_BITE);
			brain().aimStrike(aim);
		}
	}

	/**
	 * The last stretch, at {@code speed} across the ground: settled to the meeting point's height (gently:
	 * it stays level, a glide and not a stoop) and, when {@code home}, bent onto the line through it.
	 */
	private void steer(Vec3 meet, double speed, boolean home) {
		Vec3 v = dragon.getDeltaMovement();
		double horizontal = speed;
		double mx = meet.x - dragon.getX(), mz = meet.z - dragon.getZ(), m = Math.hypot(mx, mz);
		double vx = v.x, vz = v.z;
		if (home && m > 1.0 && (mx * v.x + mz * v.z) > 0.0) {
			double k = 0.3;
			vx = vx * (1.0 - k) + mx / m * horizontal * k;
			vz = vz * (1.0 - k) + mz / m * horizontal * k;
		}
		double h = Math.hypot(vx, vz);
		if (h > 1e-6) {
			vx *= horizontal / h;
			vz *= horizontal / h;
		}
		double want = Mth.clamp((meet.y - dragon.getY()) * 0.08, -0.3, 0.3);
		dragon.setDeltaMovement(vx, v.y + (want - v.y) * 0.4, vz);
	}

	/** The bite in passing: the aim follows the prey until a moment before the jaws close, then they close on whatever is there. */
	private void bite() {
		DragonBrain brain = brain();
		if (brain.action() != DragonAnim.GLIDE_BITE || ticks > FlybyBite.LENGTH_TICKS) {
			away();
			return;
		}
		int toHit = FlybyBite.HIT_TICKS - ticks;
		if (!lost() && toHit > FlybyBite.REACTION_TICKS) aim = preyAt(toHit);
		// the aim is a point in the world: relative to the dragon it changes as it flies
		if (!hit) brain.aimStrike(aim);
		if (!hit && toHit > 0) steer(meet(aim), passSpeed, true);
		waypoint = dragon.position().add(heading.scale(24.0));
		if (!hit && toHit <= 0) {
			hit = true;
			blow();
		}
	}

	/** The jaws close: whatever is at them is hit, by the dragon's speed. */
	private void blow() {
		if (aim == null) return;
		Vec3 v = dragon.getDeltaMovement();
		Vec3 jaws = JawBlow.landing(dragon, probe, DragonAnim.GLIDE_BITE, aim);
		dragon.playSound(DragonSounds.BITE, 3.0F, 0.6F);
		DamageSource source = dragon.damageSources().mobAttack(dragon);
		float damage = FlybyBite.damage(v.length());
		double[] push = FlybyBite.knockback(v.x, v.y, v.z);
		for (LivingEntity living : JawBlow.struck(dragon, jaws, DragonConfig.FLYBY_RADIUS.get())) {
			if (!living.hurtServer(((ServerLevel) dragon.level()), source, damage)) continue;
			living.push(push[0], push[1], push[2]);
			living.needsSync = true;
		}
	}

	private void away() {
		DragonBrain brain = brain();
		if (brain.action() == DragonAnim.GLIDE_BITE) brain.clearAction();
		brain.setLookTarget(null);
		stage = Stage.AWAY;
		ticks = 0;
		waypoint = onAhead(40.0, 10.0);
	}

	@Override
	public FlightModel.Force flightForce() {
		boolean close = stage == Stage.APPROACH && target != null && target.distanceToSqr(dragon) < HOME_IN * HOME_IN;
		return close || stage == Stage.BITE ? FlightModel.Force.GLIDE : FlightModel.Force.NONE;
	}

	@Override
	public float getFlySpeed() {
		return stage == Stage.APPROACH ? 1.3F : 1.0F;
	}
}
