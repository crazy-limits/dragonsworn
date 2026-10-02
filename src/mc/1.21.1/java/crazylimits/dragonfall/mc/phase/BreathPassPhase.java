package crazylimits.dragonfall.mc.phase;

import crazylimits.dragonfall.anim.BreathPass;
import crazylimits.dragonfall.anim.DragonAnim;
import crazylimits.dragonfall.body.Strike;
import crazylimits.dragonfall.flight.FlightModel;
import crazylimits.dragonfall.mc.DragonBrain;
import crazylimits.dragonfall.mc.DragonData;
import crazylimits.dragonfall.mc.DragonPhases;
import crazylimits.dragonfall.mc.DragonfallDragon;
import crazylimits.dragonfall.mc.breath.BreathStreamPhase;
import crazylimits.dragonfall.nav.BlockGrid;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import org.jetbrains.annotations.Nullable;

/**
 * The breath pass ({@link BreathPass}): the dragon swings out to get a run at its prey, comes back in
 * {@link BreathPass#HEIGHT} blocks over it, and from {@link BreathPass#START_DISTANCE} short of it glides,
 * inhaling ({@link DragonAnim#GLIDE_BREATH}); then the neck swings down and it pours void flame onto the
 * ground under its flight path, the aim raking through the prey and on ahead of it. Then it flies on.
 *
 * <p>The aim is synced like the perched breath's ({@link DragonData#STRIKE}): the neck is straightened
 * onto it ({@code body/Strike}) and the client pours the flames from the model's mouth at it
 * ({@code BreathRender}). What the stream touches burns (server).
 */
public class BreathPassPhase extends AbstractDragonPhaseInstance implements DragonfallPhase {
	private enum Stage { RUN_UP, APPROACH, PASS, AWAY }

	static final int RUN_UP_TICKS = 200, APPROACH_TICKS = 300, AWAY_TICKS = 50;

	@Nullable
	private LivingEntity target;
	private Stage stage = Stage.RUN_UP;
	private int ticks;
	/** The run's direction across the ground (unit), fixed as it comes in. */
	private Vec3 heading = Vec3.ZERO;
	@Nullable
	private Vec3 waypoint;
	/** The aim's angles (off the facing, below level), degrees. */
	private double[] aim = {0.0, BreathPass.PITCH_REST};
	/** The height it passes at (world y), set as it comes in. */
	private double passY;

	public BreathPassPhase(EnderDragon dragon) {
		super(dragon);
	}

	/** Starts a pass at {@code target} when it is in range and there is open sky over it to pour through. */
	public static boolean start(EnderDragon dragon, LivingEntity target) {
		if (target.distanceToSqr(dragon) > BreathPass.MAX_RANGE * BreathPass.MAX_RANGE
				|| !openAbove(DragonfallDragon.brain(dragon).grid(), target)) return false;
		dragon.getPhaseManager().setPhase(DragonPhases.BREATH_PASS);
		dragon.getPhaseManager().getPhase(DragonPhases.BREATH_PASS).target = target;
		return true;
	}

	/** Nothing solid over the prey up to the pass's height: the flames would only splash on a roof. */
	static boolean openAbove(BlockGrid grid, LivingEntity target) {
		int x0 = target.getBlockX(), y0 = Mth.floor(target.getY() + target.getBbHeight()), z0 = target.getBlockZ();
		for (int x = x0 - 1; x <= x0 + 1; x++) {
			for (int z = z0 - 1; z <= z0 + 1; z++) {
				for (int y = y0; y < y0 + BreathPass.HEIGHT; y++) {
					if (grid.blocked(x, y, z)) return false;
				}
			}
		}
		return true;
	}

	@Override
	public EnderDragonPhase<BreathPassPhase> getPhase() {
		return DragonPhases.BREATH_PASS;
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
		aim = new double[] {0.0, BreathPass.PITCH_REST};
	}

	@Override
	public void end() {
		if (dragon.level().isClientSide) return;
		DragonBrain brain = brain();
		if (brain.action() == DragonAnim.GLIDE_BREATH) brain.clearAction();
		brain.setLookTarget(null);
	}

	@Override
	public void doServerTick() {
		ticks++;
		switch (stage) {
			case RUN_UP -> runUp();
			case APPROACH -> approach();
			case PASS -> pass();
			case AWAY -> {
				if (ticks > AWAY_TICKS) dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
			}
		}
	}

	private boolean lost() {
		return target == null || !target.isAlive() || target.level() != dragon.level()
				|| target.distanceToSqr(dragon) > 4 * BreathPass.MAX_RANGE * BreathPass.MAX_RANGE
				|| target instanceof Player p && (p.isCreative() || p.isSpectator());
	}

	/** The height it passes over {@code at}: over the prey's feet, or the ground there when that is higher. */
	private double passHeight(Vec3 at) {
		int ground = brain().grid().ground(Mth.floor(at.x), Mth.floor(at.z));
		double base = target == null ? at.y : target.getY();
		return Math.max(base, ground == BlockGrid.NO_GROUND ? base : ground) + BreathPass.HEIGHT;
	}

	/** Too close for a run: swing out away from the prey first. */
	private void runUp() {
		if (lost() || ticks > RUN_UP_TICKS) {
			away();
			return;
		}
		brain().setLookTarget(target);
		Vec3 out = dragon.position().subtract(target.position()).multiply(1, 0, 1);
		double distance = out.length();
		if (distance > BreathPass.RUN_UP - 4.0) {
			stage = Stage.APPROACH;
			ticks = 0;
			return;
		}
		if (waypoint == null) {
			Vec3 dir = distance > 1e-3 ? out.scale(1.0 / distance) : dragon.getLookAngle().multiply(-1, 0, -1).normalize();
			waypoint = target.position().add(dir.scale(BreathPass.RUN_UP + 8.0)).add(0.0, BreathPass.HEIGHT + 4.0, 0.0);
		}
	}

	/** Back in at the prey, at the pass's height, aiming past it; the inhale starts once lined up close enough. */
	private void approach() {
		if (lost() || ticks > APPROACH_TICKS) {
			away();
			return;
		}
		brain().setLookTarget(target);
		double dx = target.getX() - dragon.getX(), dz = target.getZ() - dragon.getZ();
		double distance = Math.hypot(dx, dz);
		if (distance > 1e-3 && (distance > BreathPass.START_DISTANCE + 8.0 || heading == Vec3.ZERO)) heading = new Vec3(dx / distance, 0.0, dz / distance);
		// overshot without lining up: a fresh run
		if (dx * heading.x + dz * heading.z < 0.0) {
			stage = Stage.RUN_UP;
			ticks = 0;
			waypoint = null;
			return;
		}
		passY = passHeight(target.position());
		waypoint = new Vec3(target.getX() + heading.x * 16.0, passY, target.getZ() + heading.z * 16.0);
		if (BreathPass.linedUp(dragon.getYRot(), dx, dz)) {
			stage = Stage.PASS;
			ticks = 0;
			aim = new double[] {0.0, BreathPass.PITCH_REST};
			brain().startAction(DragonAnim.GLIDE_BREATH);
		}
	}

	/** The glide over the prey: level at the pass's height, the aim raking after it, everything in the stream burning. */
	private void pass() {
		DragonBrain brain = brain();
		if (ticks >= BreathPass.TOTAL_TICKS || brain.action() != DragonAnim.GLIDE_BREATH) {
			away();
			return;
		}
		boolean lost = lost();
		// on along the line it came in on, over the ground at the pass's height
		Vec3 ahead = dragon.position().add(heading.scale(24.0));
		passY = Math.max(passY - 0.05, passHeight(dragon.position()));
		waypoint = new Vec3(ahead.x, passY, ahead.z);
		Vec3 v = dragon.getDeltaMovement();
		double horizontal = v.horizontalDistance();
		double keep = horizontal > BreathPass.MAX_SPEED ? BreathPass.MAX_SPEED / horizontal
				: horizontal > 1e-3 && horizontal < BreathPass.MIN_SPEED ? BreathPass.MIN_SPEED / horizontal : 1.0;
		double want = Mth.clamp((passY - dragon.getY()) * 0.1, -0.3, 0.2);
		dragon.setDeltaMovement(v.x * keep, v.y + (want - v.y) * 0.3, v.z * keep);

		// the aim: from the neck's base, after the prey's body within the cone (straight ahead and down without one)
		float yaw = dragon.getYRot();
		double[] b = BreathPass.neckBase(yaw);
		Vec3 base = dragon.position().add(b[0], b[1], b[2]);
		double[] prey = {0.0, BreathPass.PITCH_REST};
		if (!lost) {
			Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.3, 0.0).subtract(base);
			prey = BreathPass.angles(yaw, at.x, at.y, at.z);
		}
		aim = BreathPass.chase(aim, prey, ticks < BreathPass.WINDUP_TICKS ? BreathPass.WINDUP_TURN : BreathPass.STREAM_TURN);
		double[] d = BreathPass.direction(yaw, aim);
		Vec3 dir = new Vec3(d[0], d[1], d[2]);
		Vec3 point = ticks < BreathPass.WINDUP_TICKS + BreathPass.STREAM_TICKS
				? BreathStreamPhase.stream(dragon, base, dir, BreathPass.RANGE).getLocation() : null;
		brain.aimStrike(point);
		if (BreathPass.streaming(ticks) && (ticks - BreathPass.WINDUP_TICKS) % BreathPass.DAMAGE_INTERVAL == 0) {
			Vec3 mouth = brain.partCenter(Strike.HEAD_PART);
			Vec3 to = point.subtract(mouth);
			if (to.lengthSqr() > 1e-4) BreathStreamPhase.burn(dragon, mouth, to.normalize(), BreathPass.RANGE, BreathPass.DAMAGE);
		}
	}

	private void away() {
		DragonBrain brain = brain();
		if (brain.action() == DragonAnim.GLIDE_BREATH) brain.clearAction();
		brain.setLookTarget(null);
		stage = Stage.AWAY;
		ticks = 0;
		float yaw = dragon.getYRot() * Mth.DEG_TO_RAD;
		waypoint = dragon.position().add(Mth.sin(yaw) * 40.0, 10.0, -Mth.cos(yaw) * 40.0);
	}

	/** Server: whether it is gliding over the prey with the breath playing. */
	public boolean passing() {
		return stage == Stage.PASS;
	}

	// ---------------------------------------------------------------- both sides: what the breath shows

	/**
	 * Ticks (fractional) into a flying breath's animation (the pass's, or the hover's: the same timing),
	 * from the animation clock (both sides: the client has no phase state of its own); NaN when neither
	 * is playing.
	 */
	public static double breathTicks(EnderDragon dragon, float partialTick) {
		DragonBrain brain = DragonfallDragon.brain(dragon);
		DragonAnim action = brain.action();
		if (action == null || !action.breathesInFlight() || brain.clock.anim() != action) return Double.NaN;
		return brain.clock.seconds() * 20.0 + partialTick;
	}

	/** Where the flames land (world), from the synced aim; null without one. */
	public static Vec3 aimPoint(EnderDragon dragon) {
		Vector3f a = dragon.getEntityData().get(DragonData.STRIKE);
		return Float.isFinite(a.x()) ? dragon.position().add(a.x(), a.y(), a.z()) : null;
	}

	@Override
	public void doClientTick() {
		double ticks = breathTicks(dragon, 0.0F);
		if (Double.isNaN(ticks)) return;
		int tick = (int) Math.round(ticks);
		if (tick == BreathPass.WINDUP_TICKS) {
			dragon.level().playLocalSound(dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_SHOOT,
					dragon.getSoundSource(), 4.0F, 0.7F, false);
		}
		if (BreathPass.streaming(tick) && tick % 5 == 0) {
			dragon.level().playLocalSound(dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.BLAZE_SHOOT,
					dragon.getSoundSource(), 3.0F, 0.45F + dragon.getRandom().nextFloat() * 0.1F, false);
		}
	}

	@Override
	public FlightModel.Force flightForce() {
		return stage == Stage.PASS ? FlightModel.Force.GLIDE : FlightModel.Force.NONE;
	}

	@Override
	public float getFlySpeed() {
		return stage == Stage.PASS ? 0.9F : 1.1F;
	}

	@Nullable
	@Override
	public Vec3 getFlyTargetLocation() {
		return waypoint;
	}
}
