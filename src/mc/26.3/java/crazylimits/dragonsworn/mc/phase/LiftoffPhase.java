package crazylimits.dragonsworn.mc.phase;

import crazylimits.dragonsworn.ai.Foothold;
import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.flight.FlightModel;
import crazylimits.dragonsworn.flight.Wingbeat;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonPhases;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.nav.Surface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Leaving the ground ({@link DragonAnim#TAKEOFF}): it crouches on all fours, then all four limbs throw it
 * up together and the wings take over; from there it climbs on the wings alone, standing up in the air
 * (the hover), until it is {@link #CLEAR} blocks up or blocked above. Then it carries on into normal flight, where
 * the body leans forward as it picks up speed.
 *
 * <p>From a narrow foothold ({@link Foothold#narrow}: sat up, or clinging with its wings already beating)
 * there is no crouch on all fours: it springs off its hind feet at once, wings beating, into the hover.
 *
 * <p>Off a wall ({@code nav/Surface}) there is no crouch either: it springs out from the face at once, wings
 * beating, the frame turning back to the floor's as the wall pose blends into the hover (the landing on a
 * wall backwards: the body standing up in the air, facing the wall), and climbs away from the wall until it
 * is {@link #WALL_CLEAR} blocks off.
 *
 * <p>Its footing gone under it ({@link #fall}: the blocks it stood on removed) there is no crouch and no
 * jump: it drops, and the wings take over as it falls.
 */
public class LiftoffPhase extends AbstractDragonPhaseInstance implements DragonswornPhase {
	static final double CLEAR = 12.0;
	/** Off a wall: how far it gets from where it pushed off before it flies on (blocks). */
	static final double WALL_CLEAR = 10.0;
	private static final int JUMP_TICK = (int) Math.round(DragonAnim.TAKEOFF_JUMP_SECONDS * 20);
	private static final int ANIM_TICKS = (int) Math.round(DragonAnim.TAKEOFF_SECONDS * 20);
	private static final int GIVE_UP = 200;
	/** Off a wall: ticks after the push-off the flare starts to ease out (it then takes Surface.LEAN_TICKS). */
	private static final int UNLEAN_TICK = 8;

	private int ticks, jumpTick;
	private double startY;
	/** Where it pushed off from, and the wall's face it left (the floor when it took off from the ground). */
	private Vec3 start = Vec3.ZERO;
	private Surface.Face from = Surface.Face.FLOOR;
	/** Off a narrow foothold: no takeoff animation, the hover's beat at once. */
	private boolean narrow;
	/** Its footing gone ({@link #fall}): it drops instead of jumping. */
	private boolean falling;

	public LiftoffPhase(EnderDragon dragon) {
		super(dragon);
	}

	/** What it stood on is gone: it falls off (a wall: out from the face) and flies, no crouch, no jump. */
	public static void fall(EnderDragon dragon) {
		dragon.getPhaseManager().setPhase(DragonPhases.LIFTOFF);
		LiftoffPhase phase = dragon.getPhaseManager().getPhase(DragonPhases.LIFTOFF);
		phase.falling = true;
		phase.jumpTick = 1;
		if (phase.brain().action() == DragonAnim.TAKEOFF) phase.brain().clearAction();
	}

	@Override
	public EnderDragonPhase<LiftoffPhase> getPhase() {
		return DragonPhases.LIFTOFF;
	}

	private DragonBrain brain() {
		return DragonswornDragon.brain(dragon);
	}

	@Override
	public void begin() {
		ticks = 0;
		startY = dragon.getY();
		start = dragon.position();
		from = Surface.Face.FLOOR;
		dragon.setDeltaMovement(Vec3.ZERO);
		if (dragon.level().isClientSide()) return;
		// off a narrow foothold or a wall: no crouch on all fours
		narrow = brain().foothold().narrow() || brain().face().wall();
		falling = false;
		from = brain().face();
		jumpTick = narrow ? 1 : JUMP_TICK;
		brain().setFoothold(Foothold.STAND);
		if (!narrow) brain().startAction(DragonAnim.TAKEOFF);
	}

	@Override
	public void end() {
		if (!dragon.level().isClientSide() && brain().leaning().wall()) brain().setFace(Surface.Face.FLOOR);
		if (!dragon.level().isClientSide() && brain().action() == DragonAnim.TAKEOFF) brain().clearAction();
	}

	@Override
	public void doServerTick(ServerLevel serverLevel) {
		ticks++;
		if (ticks == jumpTick) jump();
		if (ticks == jumpTick + UNLEAN_TICK) brain().setFace(Surface.Face.FLOOR);
		if (ticks == ANIM_TICKS && !narrow) brain().clearAction();
		boolean clear = from.wall() ? dragon.position().distanceTo(start) > WALL_CLEAR : dragon.getY() - startY > CLEAR;
		if (ticks > jumpTick + 10 && (clear || dragon.verticalCollision || ticks > GIVE_UP)) {
			dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
		}
	}

	/**
	 * All four limbs push off together: a strong throw up, and the hover's beat starts where the
	 * animation's wings are ({@link DragonAnim#TAKEOFF_PHASE}), so beats and lift stay in step.
	 */
	private void jump() {
		// off a perch: the next downstroke at once (clinging, the wings were beating already)
		brain().flight.forceFlight(FlightModel.Force.HOVER, narrow || falling ? Wingbeat.DOWNSTROKE_START : DragonAnim.TAKEOFF_PHASE);
		if (falling) {
			brain().setFace(Surface.Face.FLOOR, from);
			dragon.setDeltaMovement(from.nx * 0.15, -0.5, from.nz * 0.15);
			return;
		}
		if (from.wall()) {
			// out from the face into the flare (the body still nearly upright, wings beating), which then eases out
			brain().setFace(Surface.Face.FLOOR, from);
			dragon.setDeltaMovement(from.nx * 0.5, 0.3, from.nz * 0.5);
			return;
		}
		dragon.setDeltaMovement(0.0, narrow ? 0.4 : 0.55, 0.0);
		if (dragon.level() instanceof ServerLevel level) {
			BlockPos below = BlockPos.containing(dragon.getX(), dragon.getY() - 0.5, dragon.getZ());
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)),
					dragon.getX(), dragon.getY() + 0.2, dragon.getZ(), 60, 3.5, 0.2, 3.5, 0.2);
			level.sendParticles(ParticleTypes.CLOUD, dragon.getX(), dragon.getY() + 0.5, dragon.getZ(), 30, 5.0, 0.3, 5.0, 0.05);
		}
	}

	@Override
	public FlightModel.Force flightForce() {
		return FlightModel.Force.HOVER;
	}

	@Override
	public float getFlySpeed() {
		return 1.0F;
	}

	/** Crouched (before the jump) it does not move; then it climbs straight up. */
	@Nullable
	@Override
	public Vec3 getFlyTargetLocation() {
		if (ticks < jumpTick) return null;
		if (from.wall()) return start.add(from.nx * (WALL_CLEAR + 4.0), 4.0, from.nz * (WALL_CLEAR + 4.0));
		return new Vec3(dragon.getX(), startY + CLEAR + 2.0, dragon.getZ());
	}
}
