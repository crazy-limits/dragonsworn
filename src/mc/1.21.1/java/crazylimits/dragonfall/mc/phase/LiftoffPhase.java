package crazylimits.dragonfall.mc.phase;

import crazylimits.dragonfall.anim.DragonAnim;
import crazylimits.dragonfall.flight.FlightModel;
import crazylimits.dragonfall.mc.DragonBrain;
import crazylimits.dragonfall.mc.DragonPhases;
import crazylimits.dragonfall.mc.DragonfallDragon;
import crazylimits.dragonfall.nav.Foothold;
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
 */
public class LiftoffPhase extends AbstractDragonPhaseInstance implements DragonfallPhase {
	static final double CLEAR = 12.0;
	private static final int JUMP_TICK = (int) Math.round(DragonAnim.TAKEOFF_JUMP_SECONDS * 20);
	private static final int ANIM_TICKS = (int) Math.round(DragonAnim.TAKEOFF_SECONDS * 20);
	private static final int GIVE_UP = 200;

	private int ticks, jumpTick;
	private double startY;
	/** Off a narrow foothold: no takeoff animation, the hover's beat at once. */
	private boolean narrow;

	public LiftoffPhase(EnderDragon dragon) {
		super(dragon);
	}

	@Override
	public EnderDragonPhase<LiftoffPhase> getPhase() {
		return DragonPhases.LIFTOFF;
	}

	private DragonBrain brain() {
		return DragonfallDragon.brain(dragon);
	}

	@Override
	public void begin() {
		ticks = 0;
		startY = dragon.getY();
		dragon.setDeltaMovement(Vec3.ZERO);
		if (dragon.level().isClientSide) return;
		narrow = brain().foothold().narrow();
		jumpTick = narrow ? 1 : JUMP_TICK;
		brain().setFoothold(Foothold.STAND);
		if (!narrow) brain().startAction(DragonAnim.TAKEOFF);
	}

	@Override
	public void end() {
		if (!dragon.level().isClientSide && brain().action() == DragonAnim.TAKEOFF) brain().clearAction();
	}

	@Override
	public void doServerTick() {
		ticks++;
		if (ticks == jumpTick) jump();
		if (ticks == ANIM_TICKS && !narrow) brain().clearAction();
		if (ticks > jumpTick + 10 && (dragon.getY() - startY > CLEAR || dragon.verticalCollision || ticks > GIVE_UP)) {
			dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
		}
	}

	/**
	 * All four limbs push off together: a strong throw up, and the hover's beat starts where the
	 * animation's wings are ({@link DragonAnim#TAKEOFF_PHASE}), so beats and lift stay in step.
	 */
	private void jump() {
		// off a perch: the next downstroke at once (clinging, the wings were beating already)
		brain().forceFlight(FlightModel.Force.HOVER, narrow ? DragonAnim.DOWNSTROKE_START : DragonAnim.TAKEOFF_PHASE);
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
		return new Vec3(dragon.getX(), startY + CLEAR + 2.0, dragon.getZ());
	}
}
