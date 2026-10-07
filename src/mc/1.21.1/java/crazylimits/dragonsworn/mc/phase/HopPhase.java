package crazylimits.dragonsworn.mc.phase;

import crazylimits.dragonsworn.ai.Foothold;
import crazylimits.dragonsworn.flight.FlightModel;
import crazylimits.dragonsworn.flight.Wingbeat;
import crazylimits.dragonsworn.math.Maths;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonPhases;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.nav.Surface;
import crazylimits.dragonsworn.nav.SurfaceSites;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A hop across the ground ({@code nav/SurfaceSites#toward}: onto a ledge, down off one), or the landing
 * on a wall from the hover in front of it ({@link WallApproachPhase}). Across the ground the wings beat once
 * or twice and carry it on an arc to the site; it comes down on all four limbs there and fights on
 * ({@link GroundFightPhase}), or rests. Onto a wall it flies: hovering where it is it first turns to face the
 * wall ({@link #TURN_RATE}: facing away, a half turn takes a second and more), then comes in straight, its
 * body tipping up in the air, slowing to the site, and only there grips it: the frame
 * turns onto the face as the hover blends into the wall pose ({@code nav/Surface}), wings onto the face,
 * hind feet onto the foothold, with no turn of the body beyond what the two poses differ by.
 */
public class HopPhase extends AbstractDragonPhaseInstance implements DragonswornPhase {
	/** Blocks a tick it covers, and how long a hop takes at least and at most (ticks). */
	static final double SPEED = 0.45;
	/** Flying in to a wall, slower (hovering, it slows to the grip). */
	static final double WALL_SPEED = 0.2;
	static final int MIN_TICKS = 12, MAX_TICKS = 40;
	/** How far the arc bulges out from the faces (blocks), plus a share of the hop's length. */
	static final double ARC = 1.5, ARC_SHARE = 0.2;

	@Nullable
	private SurfaceSites.Site site;
	@Nullable
	private LivingEntity target;
	private Vec3 from = Vec3.ZERO, to = Vec3.ZERO, arc = Vec3.ZERO;
	private float fromYaw, toYaw;
	private int ticks, length, turnTicks;
	/** Onto a wall: how fast it turns to face it before it comes in (degrees a tick), within how far it counts as facing it, and when it gives up. */
	static final float TURN_RATE = 7.0F, FACING = 8.0F;
	static final int TURN_GIVE_UP = 80;

	public HopPhase(EnderDragon dragon) {
		super(dragon);
	}

	/** Hops onto {@code site}, then fights {@code target} there (or rests, when null). */
	public static void start(EnderDragon dragon, SurfaceSites.Site site, @Nullable LivingEntity target) {
		dragon.getPhaseManager().setPhase(DragonPhases.HOP);
		HopPhase phase = dragon.getPhaseManager().getPhase(DragonPhases.HOP);
		phase.site = site;
		phase.target = target;
		phase.ticks = 0;
		phase.turnTicks = 0;
	}

	/** Facing the wall it is to grip (near enough to come in). */
	private boolean facing() {
		return Math.abs(Mth.wrapDegrees(site.yaw() - dragon.getYRot())) <= FACING;
	}

	@Override
	public EnderDragonPhase<HopPhase> getPhase() {
		return DragonPhases.HOP;
	}

	private DragonBrain brain() {
		return DragonswornDragon.brain(dragon);
	}

	@Override
	public void begin() {
		site = null;
		target = null;
		ticks = 0;
		turnTicks = 0;
	}

	@Override
	public void doServerTick() {
		if (site == null) {
			dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
			return;
		}
		DragonBrain brain = brain();
		// onto a wall: first, hovering where it is, it turns to face the wall, at a flier's pace
		if (ticks == 0 && site.face().wall() && !facing()) {
			dragon.setDeltaMovement(Vec3.ZERO);
			float off = Mth.wrapDegrees(site.yaw() - dragon.getYRot());
			dragon.setYRot(dragon.getYRot() + Mth.clamp(off, -TURN_RATE, TURN_RATE));
			dragon.yRotA = 0.0F;
			brain.flight.forceFlight(FlightModel.Force.HOVER, Wingbeat.DOWNSTROKE_START);
			if (++turnTicks > TURN_GIVE_UP) dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
			return;
		}
		if (ticks == 0) leap(brain);
		ticks++;
		double u = Math.min(1.0, (double) ticks / length), s = Maths.smoothstep(u);
		Vec3 at = from.lerp(to, s).add(arc.scale(4.0 * s * (1.0 - s)));
		dragon.setPos(at.x, at.y, at.z);
		dragon.setDeltaMovement(Vec3.ZERO);
		// onto a wall it faces it already (the last of the turn on the way in); across the ground it turns on the way
		float turn = site.face().wall() ? (float) Math.min(1.0, 4.0 * u) : (float) s;
		dragon.setYRot(fromYaw + Mth.wrapDegrees(toYaw - fromYaw) * turn);
		dragon.yRotA = 0.0F;
		if (ticks >= length) {
			// the grip: the frame turns onto the face as the hover blends into the wall pose
			if (site.face().wall()) brain.setFace(site.face());
			GroundFightPhase.thud(dragon);
			GroundFightPhase.start(dragon, target, false, Foothold.STAND);
		}
	}

	/** Off: the path there, the wings beating (the new face now, across the ground; on arrival, onto a wall). */
	private void leap(DragonBrain brain) {
		Surface.Face was = brain.face();
		from = dragon.position();
		double[] w = site.world();
		to = new Vec3(w[0], w[1], w[2]);
		fromYaw = dragon.getYRot();
		toYaw = site.yaw();
		double distance = to.distanceTo(from);
		if (site.face().wall()) {
			// flying in, straight: no arc, and the face only once it is there
			arc = Vec3.ZERO;
		} else {
			// up from the ground: over an edge, never through it
			Vec3 out = normal(was).add(normal(site.face()));
			if (out.lengthSqr() < 1e-6) out = normal(site.face());
			arc = out.normalize().scale(ARC + ARC_SHARE * distance);
		}
		length = Mth.clamp((int) Math.ceil(distance / (site.face().wall() ? WALL_SPEED : SPEED)), MIN_TICKS, MAX_TICKS);
		brain.clearAction();
		brain.setFoothold(Foothold.STAND);
		// across the ground the new face now; onto a wall, the flare toward it (the face itself on arrival)
		if (site.face().wall()) brain.setFace(Surface.Face.FLOOR, site.face());
		else brain.setFace(site.face());
		brain.flight.forceFlight(FlightModel.Force.HOVER, Wingbeat.DOWNSTROKE_START);
	}

	private static Vec3 normal(Surface.Face face) {
		return face.wall() ? new Vec3(face.nx, 0.0, face.nz) : new Vec3(0.0, 1.0, 0.0);
	}

	@Nullable
	@Override
	public LivingEntity attackTarget() {
		return target;
	}

	@Override
	public FlightModel.Force flightForce() {
		return FlightModel.Force.HOVER;
	}

	/** The path is its own: it never flies into the faces it leaves and grips. */
	@Override
	public boolean collides() {
		return false;
	}

	@Nullable
	@Override
	public Vec3 getFlyTargetLocation() {
		return null;
	}
}
