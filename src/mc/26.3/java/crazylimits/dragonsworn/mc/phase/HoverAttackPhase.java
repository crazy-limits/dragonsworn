package crazylimits.dragonsworn.mc.phase;

import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.attack.HoverAttack;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The hover attacks ({@link HoverAttack}): where it cannot come down by its prey (on a wall, up a spire,
 * in the air) the dragon flies in close and stands in the air beside it, facing it, then bites
 * ({@link DragonAnim#HOVER_BITE}, up to {@link HoverAttack#BITES} times while the prey stays in reach) or
 * pours the stream breath at it ({@link DragonAnim#HOVER_BREATH}, once). Each attack starts on a beat
 * boundary of the hover. Prey that keeps away for {@link HoverAttack#APPROACH_TICKS}, or the spell's end,
 * sends it on its way.
 *
 * <p>The bite is aimed by body/Strike's IK at where the prey is a moment before the jaws close; whatever
 * is at them then is hit (a dodge is a miss). The breath's aim (synced as the pass's,
 * {@code DragonData#STRIKE}) swings after the prey inside a cone ahead of the hovering dragon; the dragon
 * turns after it, and the client pours the flames from the model's mouth ({@code BreathRender}, via
 * {@link BreathPassPhase#breathTicks}). What the stream touches burns (server).
 */
public class HoverAttackPhase extends AirAttackPhase {
	public enum Mode { BITE, BREATH }

	private enum Stage { APPROACH, HOLD, ACT, AWAY }

	static final int AWAY_TICKS = 40;

	private Mode mode = Mode.BITE;
	private Stage stage = Stage.APPROACH;
	/** Ticks since the spell began, attacks made, and the spell tick the next one may start at. */
	private int spellTicks, attacks, readyAt;
	/** The side (horizontal unit direction) it hovers on, from the prey toward it. */
	private Vec3 side = Vec3.ZERO;
	/** The bite's aim (world), or the breath's angles (off the facing, below level). */
	@Nullable
	private Vec3 aim;
	private double[] angles = {0.0, 20.0};
	private boolean hit;
	private final Strike probe = new Strike();

	public HoverAttackPhase(EnderDragon dragon) {
		super(dragon);
	}

	/** Starts a hover attack at {@code target} when it is within reach of a flight. */
	public static boolean start(EnderDragon dragon, LivingEntity target, Mode mode) {
		if (target.distanceToSqr(dragon) > HoverAttack.MAX_RANGE * HoverAttack.MAX_RANGE) return false;
		begin(dragon, DragonPhases.HOVER_ATTACK, target).mode = mode;
		return true;
	}

	@Override
	public EnderDragonPhase<HoverAttackPhase> getPhase() {
		return DragonPhases.HOVER_ATTACK;
	}

	@Override
	public void begin() {
		super.begin();
		mode = Mode.BITE;
		stage = Stage.APPROACH;
		spellTicks = attacks = readyAt = 0;
		side = Vec3.ZERO;
		aim = null;
		hit = false;
	}

	@Override
	protected boolean leaving() {
		return stage == Stage.AWAY;
	}

	@Override
	public void end() {
		if (dragon.level().isClientSide()) return;
		DragonBrain brain = brain();
		DragonAnim action = brain.action();
		if (action == DragonAnim.HOVER_BITE || action == DragonAnim.HOVER_BREATH) brain.clearAction();
		brain.setLookTarget(null);
	}

	@Override
	public void doServerTick(ServerLevel serverLevel) {
		ticks++;
		spellTicks++;
		if (stage != Stage.AWAY && stage != Stage.ACT && (lost() || spellTicks > HoverAttack.MAX_TICKS)) {
			away();
			return;
		}
		switch (stage) {
			case APPROACH -> approach();
			case HOLD -> hold();
			case ACT -> act();
			case AWAY -> leaveAfter(AWAY_TICKS);
		}
	}

	private boolean lost() {
		return lostBeyond(HoverAttack.LOST_RANGE);
	}

	private Vec3 middle() {
		return target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
	}

	/**
	 * Where it hovers to attack from the side {@code from} (unit, prey toward dragon): for the bite, where
	 * its jaws rest on the prey's middle; for the breath, off and over it.
	 */
	private Vec3 spot(Vec3 from) {
		Vec3 facing = from.scale(-1.0);
		if (mode == Mode.BITE) {
			Vec3 m = middle();
			double[] s = HoverAttack.biteSpot(m.x, m.y, m.z, facing.x, facing.z, Strike.rest(DragonAnim.HOVER_BITE, brain().body, 1.0F));
			return new Vec3(s[0], s[1], s[2]);
		}
		double[] s = HoverAttack.breathSpot(target.getX(), target.getY(), target.getZ(), facing.x, facing.z);
		return new Vec3(s[0], s[1], s[2]);
	}

	/**
	 * The side to hover on: the one it comes from, or the nearest round the prey where the body is in the
	 * clear and it sees the prey (a wall's face, a spire's flank are no place to hover).
	 */
	private Vec3 pickSide() {
		Vec3 out = dragon.position().subtract(target.position()).multiply(1, 0, 1);
		double base = out.lengthSqr() > 1e-6 ? Math.atan2(out.z, out.x) : 0.0;
		BlockGrid grid = brain().grid();
		for (int k = 0; k < 12; k++) {
			double a = base + Math.toRadians(30.0 * ((k + 1) / 2) * (k % 2 == 0 ? 1 : -1));
			Vec3 from = new Vec3(Math.cos(a), 0.0, Math.sin(a));
			Vec3 at = spot(from);
			if (clear(grid, at) && sees(at)) return from;
		}
		return out.lengthSqr() > 1e-6 ? out.normalize() : new Vec3(1.0, 0.0, 0.0);
	}

	/** Room for the hovering body (a 5 x 5 x 4 block box over {@code at}): no solid block in it. */
	private static boolean clear(BlockGrid grid, Vec3 at) {
		int x0 = Mth.floor(at.x), y0 = Mth.floor(at.y), z0 = Mth.floor(at.z);
		for (int x = x0 - 2; x <= x0 + 2; x++) {
			for (int z = z0 - 2; z <= z0 + 2; z++) {
				for (int y = y0; y <= y0 + 3; y++) {
					if (grid.blocked(x, y, z)) return false;
				}
			}
		}
		return true;
	}

	/** Whether the prey's middle is in sight from a little over {@code at} (the head's height in the hover). */
	private boolean sees(Vec3 at) {
		Vec3 eye = at.add(0.0, 3.0, 0.0);
		HitResult hit = dragon.level().clip(new ClipContext(eye, middle(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dragon));
		return hit.getType() == HitResult.Type.MISS;
	}

	/** Flies in to the spot beside the prey; close to it, it stands in the air. */
	private void approach() {
		brain().setLookTarget(target);
		if (side == Vec3.ZERO || ticks % 20 == 0) side = pickSide();
		waypoint = spot(side);
		if (waypoint.distanceToSqr(dragon.position()) < HoverAttack.IN_PLACE * HoverAttack.IN_PLACE * 4.0) {
			stage = Stage.HOLD;
			ticks = 0;
		} else if (ticks > HoverAttack.APPROACH_TICKS) {
			away();
		}
	}

	/** Standing in the air beside the prey, facing it: on the next beat boundary with the prey in reach, it attacks. */
	private void hold() {
		DragonBrain brain = brain();
		brain.setLookTarget(target);
		// the prey moved off: follow it round (a new side when this one is no good any more)
		if (ticks % 20 == 0 && !(clear(brain.grid(), spot(side)) && sees(spot(side)))) side = pickSide();
		waypoint = spot(side);
		if (waypoint.distanceToSqr(dragon.position()) > HoverAttack.HOVER_RANGE * HoverAttack.HOVER_RANGE) {
			stage = Stage.APPROACH;
			ticks = 0;
			return;
		}
		if (ticks > HoverAttack.APPROACH_TICKS) {
			away();
			return;
		}
		if (spellTicks < readyAt || !HoverAttack.onBeat(brain.beatPhase()) || !facing(25.0)) return;
		if (mode == Mode.BITE) {
			Vec3 m = middle();
			probe.aim(DragonAnim.HOVER_BITE, m.x - dragon.getX(), m.y - dragon.getY(), m.z - dragon.getZ());
			if (probe.solve(brain.body, 1.0F) > Strike.BITE_RADIUS * 0.5) return;
			aim = m;
			brain.startAction(DragonAnim.HOVER_BITE);
		} else {
			if (!sees(dragon.position())) return;
			aim = null;
			angles = new double[] {0.0, 20.0};
			brain.startAction(DragonAnim.HOVER_BREATH);
		}
		stage = Stage.ACT;
		ticks = 0;
		hit = false;
		attacks++;
	}

	/** Whether the prey is within {@code degrees} of the facing. */
	private boolean facing(double degrees) {
		double dx = target.getX() - dragon.getX(), dz = target.getZ() - dragon.getZ();
		float want = (float) Math.toDegrees(Math.atan2(dx, -dz));
		return Math.abs(Mth.wrapDegrees(want - dragon.getYRot())) <= degrees;
	}

	private void act() {
		DragonBrain brain = brain();
		boolean bite = mode == Mode.BITE;
		DragonAnim anim = bite ? DragonAnim.HOVER_BITE : DragonAnim.HOVER_BREATH;
		int length = bite ? HoverAttack.BITE_TICKS : HoverAttack.BREATH_TICKS;
		if (brain.action() != anim || ticks > length) {
			if (brain.action() == anim) brain.clearAction();
			brain.aimStrike(null);
			boolean more = bite && attacks < DragonConfig.HOVER_BITES.get() && !lost();
			if (!more) {
				away();
				return;
			}
			stage = Stage.HOLD;
			ticks = 0;
			readyAt = spellTicks + HoverAttack.BITE_RECOVERY;
			return;
		}
		waypoint = lost() ? dragon.position() : spot(side);
		if (bite) bite();
		else breathe();
	}

	/** The bite: the aim follows the prey until a moment before the jaws close; then whatever is at them is hit. */
	private void bite() {
		int toHit = HoverAttack.HIT_TICKS - ticks;
		if (!lost() && toHit > HoverAttack.REACTION_TICKS) aim = middle();
		if (!hit) brain().aimStrike(aim);
		if (hit || toHit > 0 || aim == null) return;
		hit = true;
		Vec3 jaws = JawBlow.landing(dragon, probe, DragonAnim.HOVER_BITE, aim);
		dragon.playSound(DragonSounds.BITE, 3.0F, 0.6F);
		DamageSource source = dragon.damageSources().mobAttack(dragon);
		for (LivingEntity living : JawBlow.struck(dragon, jaws, DragonConfig.BITE_RADIUS.get())) {
			if (!living.hurtServer(((ServerLevel) dragon.level()), source, DragonConfig.HOVER_BITE_DAMAGE.f())) continue;
			Vec3 push = living.position().subtract(dragon.position()).multiply(1, 0, 1).normalize().scale(HoverAttack.BITE_PUSH);
			living.push(push.x, 0.3, push.z);
			living.needsSync = true;
		}
	}

	/** The breath: the aim swings after the prey inside the cone, the stream burning what it touches. */
	private void breathe() {
		DragonBrain brain = brain();
		float yaw = dragon.getYRot();
		double[] b = new double[3];
		Strike.neckBase(DragonAnim.HOVER_BREATH, Strike.hitSeconds(DragonAnim.HOVER_BREATH), brain.body, 1.0F, b);
		Vec3 base = dragon.position().add(b[0], b[1], b[2]);
		double[] want = angles;
		if (!lost()) {
			Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.4, 0.0).subtract(base);
			want = HoverAttack.angles(yaw, at.x, at.y, at.z);
		}
		angles = FlyingBreath.tick(dragon, ticks, base, angles, want, HoverAttack.WINDUP_TURN, HoverAttack.STREAM_TURN,
				HoverAttack.RANGE, DragonConfig.HOVER_BREATH_DAMAGE.f());
	}

	private void away() {
		DragonBrain brain = brain();
		DragonAnim action = brain.action();
		if (action == DragonAnim.HOVER_BITE || action == DragonAnim.HOVER_BREATH) brain.clearAction();
		brain.setLookTarget(null);
		stage = Stage.AWAY;
		ticks = 0;
		Vec3 out = target != null ? dragon.position().subtract(target.position()).multiply(1, 0, 1) : Vec3.ZERO;
		Vec3 dir = out.lengthSqr() > 1e-6 ? out.normalize() : new Vec3(1.0, 0.0, 0.0);
		waypoint = dragon.position().add(dir.scale(40.0)).add(0.0, 10.0, 0.0);
	}

	/** Client: the breath's sounds, as the breath pass's (the same timing). */
	@Override
	public void doClientTick() {
		FlyingBreath.sounds(dragon);
	}

	@Override
	public FlightModel.Force flightForce() {
		boolean near = waypoint != null && waypoint.distanceToSqr(dragon.position()) < HoverAttack.HOVER_RANGE * HoverAttack.HOVER_RANGE;
		return stage == Stage.HOLD || stage == Stage.ACT || stage == Stage.APPROACH && near ? FlightModel.Force.HOVER : FlightModel.Force.NONE;
	}

	@Nullable
	@Override
	public Vec3 hoverLook() {
		return stage == Stage.AWAY || target == null ? null : target.position();
	}

	@Override
	public float getFlySpeed() {
		return 1.0F;
	}
}
