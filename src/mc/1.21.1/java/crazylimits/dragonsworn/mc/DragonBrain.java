package crazylimits.dragonsworn.mc;

import crazylimits.dragonsworn.ai.CombatStance;
import crazylimits.dragonsworn.ai.DeathFlight;
import crazylimits.dragonsworn.ai.Foothold;
import crazylimits.dragonsworn.ai.HitTally;
import crazylimits.dragonsworn.ai.Roaming;
import crazylimits.dragonsworn.anim.AnimClock;
import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.anim.DragonAnimSelector.Kind;
import crazylimits.dragonsworn.anim.DragonAnimSelector;
import crazylimits.dragonsworn.anim.DragonVoice;
import crazylimits.dragonsworn.anim.Gait;
import crazylimits.dragonsworn.body.DragonBody;
import crazylimits.dragonsworn.body.Grip;
import crazylimits.dragonsworn.body.PartSolver;
import crazylimits.dragonsworn.body.PoseTrack;
import crazylimits.dragonsworn.body.Strike;
import crazylimits.dragonsworn.body.Tail;
import crazylimits.dragonsworn.body.TailChain;
import crazylimits.dragonsworn.body.TailMotion;
import crazylimits.dragonsworn.flight.FlightModel;
import crazylimits.dragonsworn.limb.GroundFit;
import crazylimits.dragonsworn.limb.HeadLook;
import crazylimits.dragonsworn.mc.breath.BreathFlames;
import crazylimits.dragonsworn.mc.breath.BreathStreamPhase;
import crazylimits.dragonsworn.mc.phase.DragonswornPhase;
import crazylimits.dragonsworn.nav.BlockGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Everything Dragonsworn adds to one dragon. Lives on the entity (both sides); the server half decides
 * and moves, the client half only animates and places its own hitboxes the same way.
 *
 * <h2>Two kinds of dragon</h2>
 * <ul>
 *   <li><b>Arena</b>: the End fight's dragon. Vanilla's fight stays (pillar circuit, strafes), but it
 *       never lands on the exit portal: where vanilla's would, it perches next to a player on the island
 *       instead (scans, roars, breathes, takes off). Now and then it also lands next to one to fight on
 *       the ground; more often once the crystals are gone.</li>
 *   <li><b>Wild</b>: any other dragon (summoned with a command, anywhere). Nothing ties it to one place:
 *       it roams ({@link Roaming}), flying a while, landing somewhere along its way, walking about there
 *       and taking off again. It hunts players who come near (fireball passes, breath passes, hovering
 *       barrages, charges, snatches, ground assaults).</li>
 * </ul>
 *
 * <h2>Its parts</h2>
 * The brain keeps what plays (the synced action, look and aim; body, clock, hitboxes) and hands the rest
 * to its parts: {@link #flight} (wing-driven flight, {@link AirRoute} round terrain), {@link #hull}
 * (collision with blocks), {@link #fireballs}, {@link #combat} (who hurt it), {@link #tactics} (how it
 * goes at a target) and one director per context ({@link WildDirector}, {@link ArenaDirector}).
 */
public final class DragonBrain {
	public enum Context { UNKNOWN, ARENA, WILD }

	private final EnderDragon dragon;
	public final DragonBody body = new DragonBody();
	public final AnimClock clock = new AnimClock();
	/** Walking on the ground, through the walker's short stalls (else the walk would restart from its first frame). */
	private final Gait gait = new Gait();
	/** The roar sounding now (client). */
	public final DragonVoice.Roar roar = new DragonVoice.Roar();
	/** The bite, tail strike or stream breath aimed now (both sides, from {@link DragonData#STRIKE}). */
	public final Strike strike = new Strike();
	/** The head turned to what the dragon watches (both sides, so the head and neck hitboxes turn with it). */
	public final HeadLook look = new HeadLook();
	private final double[] lookWant = new double[2];
	/** Hits taken (server): too many at once and a landed dragon takes off. */
	public final HitTally hits = new HitTally();
	/** Wild: whether it fights on foot or takes a break in the air (server). */
	public final CombatStance stance = new CombatStance();
	/** The last flight once it is brought down (server): over the altar, up, then death ({@link DeathFlight}). */
	public final DeathFlight death = new DeathFlight();
	/** What its talons or jaws hold (both sides, from {@link DragonData#GRIP}). */
	public final PreyHold prey;
	/** Wing-driven flight, round terrain (server). */
	public final Flight flight;
	/** The hull against blocks: moving, walking, pushed out of walls (server). */
	public final HullCollision hull;
	/** Charging and shooting fireballs (server), and their glow's timing (client). */
	public final Fireballs fireballs = new Fireballs(this);
	/** The breath's flames in flight: they burn when and where they land (server). */
	public final BreathFlames flames;
	/** Who has been hurting it (server). */
	public final CombatMemory combat = new CombatMemory(this);
	/** How it goes at a target: on foot or one attack from the air (server). */
	public final Tactics tactics = new Tactics(this);
	private final WildDirector wild = new WildDirector(this);
	private final ArenaDirector arena = new ArenaDirector(this);
	private final double[] groundHeights = new double[4];
	private final PartSolver solver = new PartSolver();
	private final TailMotion.Pose tailMotion = new TailMotion.Pose();
	private final Tail.World tailWorld = new Tail.World();
	private final double[] offsets = new double[PoseTrack.PARTS * 3];
	private int bodyTickedAt = Integer.MIN_VALUE;

	private Context context = Context.UNKNOWN;
	private int actionSequence;
	/** Ticks of flight left before the next roar (server). */
	private int roarIn = DragonVoice.AIR_ROAR_MIN_TICKS;

	public DragonBrain(EnderDragon dragon) {
		this.dragon = dragon;
		this.prey = new PreyHold(dragon, this);
		this.flames = new BreathFlames(dragon);
		AirRoute route = new AirRoute(this);
		this.hull = new HullCollision(this, route);
		this.flight = new Flight(this, route, hull);
	}

	public EnderDragon dragon() {
		return dragon;
	}

	public Context context() {
		return context;
	}

	public BlockGrid grid() {
		return new LevelGrid(dragon.level());
	}

	// ---------------------------------------------------------------- what is playing

	public Kind kind() {
		// the cocoon: only once it is dead (its last flight before that flies as any other)
		if (dragon.dragonDeathTime > 0 || dragon.isDeadOrDying()) return Kind.DYING;
		EnderDragonPhase<?> phase = dragon.getPhaseManager().getCurrentPhase().getPhase();
		if (phase == EnderDragonPhase.SITTING_SCANNING) return Kind.PERCH_SCANNING;
		if (phase == EnderDragonPhase.SITTING_FLAMING) return Kind.PERCH_FLAMING;
		if (phase == EnderDragonPhase.SITTING_ATTACKING) return Kind.PERCH_ATTACKING;
		if (phase == DragonPhases.GROUND_FIGHT) return Kind.GROUND;
		if (dragon.getPhaseManager().getCurrentPhase() instanceof BreathStreamPhase) return Kind.PERCH_BREATH;
		return Kind.AIR;
	}

	/** Server: where in its wingbeat the dragon is (0..1), or -1 with the wings still. */
	public double beatPhase() {
		return flight.model.beatPhase(dragon.level().getGameTime());
	}

	/** Server: ticks until the wings may go still (0 when gliding): a beat or push under way is always finished. */
	public long wingsStillIn() {
		return flight.model.ticksToChange(dragon.level().getGameTime());
	}

	public FlightModel.Plan flightPlan() {
		return FlightModel.Plan.decode(dragon.getEntityData().get(DragonData.FLIGHT));
	}

	public DragonAnim action() {
		return DragonAnimSelector.actionAnim(dragon.getEntityData().get(DragonData.ACTION));
	}

	public DragonAnimSelector.Choice choice() {
		int bits = dragon.getEntityData().get(DragonData.ACTION);
		return DragonAnimSelector.select(kind(), foothold(), DragonAnimSelector.actionAnim(bits), DragonAnimSelector.actionSequence(bits),
				flightPlan(), gait.walking());
	}

	/** How it stands on the ground (synced): on all fours, sat up on a narrow foothold, or clinging. */
	public Foothold foothold() {
		return Foothold.of(dragon.getEntityData().get(DragonData.FOOTHOLD));
	}

	/** Server: how it stands from now on (set as it lands; back on all fours as it takes off). */
	public void setFoothold(Foothold foothold) {
		if (foothold() != foothold) dragon.getEntityData().set(DragonData.FOOTHOLD, foothold.ordinal());
	}

	public double horizontalSpeed() {
		return Math.hypot(dragon.getX() - dragon.xo, dragon.getZ() - dragon.zo);
	}

	/**
	 * In a phase that attacks (vanilla's charge, strafe and flaming perch, the perched breath, any
	 * {@link DragonswornPhase#attacks} phase: the air attacks) or holding prey: no roaring through it.
	 */
	public boolean attacking() {
		DragonPhaseInstance current = dragon.getPhaseManager().getCurrentPhase();
		EnderDragonPhase<?> phase = current.getPhase();
		return phase == EnderDragonPhase.CHARGING_PLAYER || phase == EnderDragonPhase.STRAFE_PLAYER
				|| phase == EnderDragonPhase.SITTING_FLAMING || kind() == Kind.PERCH_BREATH
				|| current instanceof DragonswornPhase p && p.attacks() || prey.hold() != Grip.Hold.NONE;
	}

	/** On its feet: landed to fight or rest. */
	public boolean onGround() {
		return kind() == Kind.GROUND;
	}

	/**
	 * Feet on the ground as the model shows it (both sides): standing or walking, perched, the takeoff
	 * until the jump, a running landing from the strike of its feet.
	 */
	public boolean footing() {
		Kind kind = kind();
		if (kind == Kind.DYING) return false;
		if (kind != Kind.AIR) return true;
		DragonAnim action = action();
		if (action == DragonAnim.TAKEOFF) return clock.anim() == DragonAnim.TAKEOFF && clock.seconds() < DragonAnim.TAKEOFF_JUMP_SECONDS;
		// the model (and the runway's path) strike the ground after the blend into the landing
		return action == DragonAnim.LAND && clock.anim() == DragonAnim.LAND
				&& clock.seconds() >= DragonAnim.LAND_TOUCH_SECONDS + DragonAnim.BLEND_TICKS / 20.0;
	}

	private DragonBody.Mode bodyMode() {
		// the cocoon floats up still: no banking, leaning, nor the shoulders turned against its pitch
		if (footing() || kind() == Kind.DYING) return DragonBody.Mode.GROUND;
		DragonAnim action = action();
		if (action == DragonAnim.TAKEOFF) return DragonBody.Mode.HOVER;
		// the flare of a running landing: no more banking, leaning into the braking
		if (action == DragonAnim.LAND && clock.seconds() >= DragonAnim.LAND_FLARE_SECONDS + DragonAnim.BLEND_TICKS / 20.0) {
			return DragonBody.Mode.HOVER;
		}
		return flightPlan().mode() == FlightModel.Mode.HOVER ? DragonBody.Mode.HOVER : DragonBody.Mode.FLIGHT;
	}

	/** Starts a one-shot (bite, roar, tail sweep, takeoff) on every client. */
	public void startAction(DragonAnim anim) {
		actionSequence++;
		dragon.getEntityData().set(DragonData.ACTION, DragonAnimSelector.encodeAction(anim, actionSequence));
	}

	public void clearAction() {
		dragon.getEntityData().set(DragonData.ACTION, DragonAnimSelector.encodeAction(null, actionSequence));
		aimStrike(null);
	}

	/** Server: aims the bite or tail strike playing at {@code at} (world), or takes the aim away (null). */
	public void aimStrike(Vec3 at) {
		Vector3f aim = at == null ? DragonData.NO_STRIKE
				: new Vector3f((float) (at.x - dragon.getX()), (float) (at.y - dragon.getY()), (float) (at.z - dragon.getZ()));
		if (!aim.equals(dragon.getEntityData().get(DragonData.STRIKE))) dragon.getEntityData().set(DragonData.STRIKE, aim);
	}

	/** The strike's aim from the synced data, for the animation playing. */
	private void updateStrike() {
		Vector3f aim = dragon.getEntityData().get(DragonData.STRIKE);
		DragonAnim anim = action() != null ? action() : kind() == Kind.PERCH_BREATH ? DragonAnim.BREATH : null;
		if (Strike.strikes(anim) && Float.isFinite(aim.x())) strike.aim(anim, aim.x(), aim.y(), aim.z());
		else strike.clear();
	}

	// ---------------------------------------------------------------- body and hitboxes

	private void tickBody() {
		if (bodyTickedAt == dragon.tickCount) return;
		bodyTickedAt = dragon.tickCount;
		body.setShaking(prey.hold() == Grip.Hold.JAW);
		body.tick(dragon.getYRot(), dragon.getX(), dragon.getY(), dragon.getZ(), bodyMode());
		gait.tick(horizontalSpeed());
		clock.tick(choice(), flightPlan(), horizontalSpeed());
		updateStrike();
		updateLook();
		boolean standing = footing();
		if (standing) sampleGround();
		body.ground.tick(standing, groundHeights, dragon.getY());
	}

	/**
	 * The ground under each foot of the footprint ({@link GroundFit#FOOTPRINT}), relative to the dragon's
	 * position; NaN where there is none or it is a cliff away. Both sides see the same blocks, so the
	 * body (and its hitboxes) tilt the same way on both.
	 */
	private void sampleGround() {
		BlockGrid grid = grid();
		double yaw = Math.toRadians(-body.yaw(1.0F)), c = Math.cos(yaw), s = Math.sin(yaw);
		// sat up on its hind feet, nothing stands where the front feet would
		int feet = onGround() && foothold().narrow() ? 2 : 4;
		for (int i = feet; i < 4; i++) groundHeights[i] = Double.NaN;
		for (int i = 0; i < feet; i++) {
			double mx = GroundFit.FOOTPRINT[i][0], mz = GroundFit.FOOTPRINT[i][1];
			int g = grid.ground(Mth.floor(dragon.getX() + mx * c + mz * s), Mth.floor(dragon.getZ() - mx * s + mz * c));
			double h = g == BlockGrid.NO_GROUND ? Double.NaN : g - dragon.getY();
			groundHeights[i] = Math.abs(h) > 4.0 ? Double.NaN : h;
		}
	}

	/** The entity the dragon is paying attention to (synced), or null. */
	public Entity lookTarget() {
		int id = dragon.getEntityData().get(DragonData.LOOK);
		return id < 0 ? null : dragon.level().getEntity(id);
	}

	/** Server: what the head should turn to (null: nothing in particular). */
	public void setLookTarget(LivingEntity entity) {
		int id = entity == null ? -1 : entity.getId();
		if (dragon.getEntityData().get(DragonData.LOOK) != id) dragon.getEntityData().set(DragonData.LOOK, id);
	}

	/**
	 * Puts every part on the model's bones (both sides), then (server) smashes soft blocks they touch,
	 * and pushes whatever stands in them out of them.
	 */
	public void placeParts() {
		tickBody();
		// the tail blends in from the last animation as the model does, and keeps out of the blocks
		// the pose the model shows: the animation BLEND_TICKS late, blended out of the last one
		double shown = clock.shownSeconds(1.0F);
		TailMotion.sample(clock.anim(), shown, clock.from(), clock.fromSeconds(), clock.blend(1.0F), tailMotion);
		tailWorld.set(grid(), body, 1.0F, dragon.getX(), dragon.getY(), dragon.getZ(), dragon.tickCount + 1.0);
		solver.solve(clock.anim(), shown, new PartSolver.Blend(clock.from(), clock.fromShownSeconds(), clock.blend(1.0F), clock.changes()),
				body, strike, 1.0F, tailMotion, tailWorld, offsets);
		EnderDragonPart[] parts = dragon.getSubEntities();
		for (int i = 0; i < parts.length && i < PoseTrack.PARTS; i++) {
			parts[i].setPos(dragon.getX() + offsets[i * 3], dragon.getY() + offsets[i * 3 + 1] - parts[i].getBbHeight() / 2.0,
					dragon.getZ() + offsets[i * 3 + 2]);
		}
		if (!dragon.level().isClientSide) {
			hull.breakSoftBlocks(parts);
			hull.pushOut(parts, onGround(), Flight.collides(dragon.getPhaseManager().getCurrentPhase()));
		}
		// the hitboxes are soft: what stands in them is pushed out, as mobs push each other (each side what it moves)
		PartCollision.push(dragon, parts);
	}

	/**
	 * Turns the head to what the dragon watches ({@link #lookTarget}), once a tick on both sides, from
	 * where the last solve posed the head; the next solve bends the neck by it, and the renderer draws it.
	 * A bite, a roar, the breath or prey in the jaws aims the head itself; a tail strike keeps its eyes
	 * on the target.
	 */
	private void updateLook() {
		Kind kind = kind();
		DragonAnim action = action();
		boolean tailStrike = action == DragonAnim.TAIL_SWEEP;
		boolean free = (action == null || tailStrike) && kind != Kind.DYING && kind != Kind.PERCH_BREATH
				&& kind != Kind.PERCH_FLAMING && prey.hold() != Grip.Hold.JAW;
		// a fireball's windup turns the head all the way round to its target (it fires down the head's line)
		boolean aiming = fireballs.aiming();
		Entity target = lookTarget();
		double wantYaw = Double.NaN, wantPitch = Double.NaN;
		if (target != null && free) {
			double[] at = new double[3];
			PartSolver.toModel(body, 1.0F, target.getX() - dragon.getX(), target.getEyeY() - dragon.getY(), target.getZ() - dragon.getZ(), at);
			if (solver.lookAngles(at[0], at[1], at[2], lookWant)) {
				wantYaw = lookWant[0];
				wantPitch = lookWant[1];
			}
		}
		look.update(dragon.tickCount, wantYaw, wantPitch, free ? (kind == Kind.AIR && !aiming ? 0.7 : 1.0) : 0.0, !tailStrike);
		for (int i = 0; i < PoseTrack.NECK_PIVOTS - 1; i++) {
			solver.lookX[i] = look.neckPitch(i);
			solver.lookY[i] = look.neckYaw(i);
		}
		solver.lookX[PoseTrack.NECK_PIVOTS - 1] = look.headPitch();
		solver.lookY[PoseTrack.NECK_PIVOTS - 1] = look.headYaw();
		solver.lookTail = look.tailYaw(TailChain.SEGMENTS);
	}

	/** World position of a part's center. */
	public Vec3 partCenter(int part) {
		return dragon.getSubEntities()[part].getBoundingBox().getCenter();
	}

	/** {@link PartSolver#lookAngles} of the last solve, for a point in model space ({@code at}, blocks). */
	boolean lookAngles(double[] at, double[] out) {
		return solver.lookAngles(at[0], at[1], at[2], out);
	}

	// ---------------------------------------------------------------- end of tick

	public void tickEnd() {
		tickBody();     // dragons whose AI is off (or dying) still need a body for the renderer
		if (dragon.level().isClientSide) {
			fireballs.clientTick();
			return;
		}
		// a dying dragon lets go of what it holds
		if ((dragon.isDeadOrDying() || dying()) && prey.hold() != Grip.Hold.NONE) prey.release(null);
		prey.tick();
		fireballs.tick();
		flames.tick();
		if (dragon.isNoAi() || dragon.isDeadOrDying() || dying()) return;
		updateContext();
		roarTick();
		if (context == Context.WILD) wild.tick();
		else if (context == Context.ARENA) arena.tick();
	}

	/**
	 * Now and then a flying dragon roars, its jaw opening with it (every client plays it on the change).
	 * On the ground and on the perch the roar animation does it instead; never while attacking.
	 */
	private void roarTick() {
		if (kind() != Kind.AIR || action() != null || attacking()) return;
		if (--roarIn > 0) return;
		roarIn = DragonVoice.AIR_ROAR_MIN_TICKS + dragon.getRandom().nextInt(DragonVoice.AIR_ROAR_SPREAD_TICKS);
		roar();
	}

	/** A roar now, in flight (the jaw opens with it on every client). */
	public void roar() {
		dragon.getEntityData().set(DragonData.VOICE, dragon.getEntityData().get(DragonData.VOICE) + 1);
	}

	private void updateContext() {
		if (context != Context.UNKNOWN) return;
		if (dragon.getDragonFight() != null) {
			context = Context.ARENA;
		} else if (dragon.tickCount > 2) {
			context = Context.WILD;
		}
	}

	/**
	 * Vanilla phases that assume the End's exit portal at 0, 0 are swapped for Dragonsworn's own on a
	 * wild dragon; leaving the ground is always the jump. The End fight's dragon never comes down on the
	 * portal: its landing approach becomes a perch beside a player ({@link ArenaDirector}), so every takeoff
	 * there is from the island's ground.
	 */
	public EnderDragonPhase<?> remap(EnderDragonPhase<?> phase) {
		DragonPhaseInstance current = dragon.getPhaseManager().getCurrentPhase();
		EnderDragonPhase<?> from = current == null ? null : current.getPhase();
		if (phase == EnderDragonPhase.TAKEOFF && (from == DragonPhases.GROUND_FIGHT || from == DragonPhases.GROUND_APPROACH)) {
			return DragonPhases.LIFTOFF;
		}
		if (context == Context.ARENA) {
			if (phase == EnderDragonPhase.LANDING_APPROACH) {
				arena.wantPerch();
				return from != null ? from : EnderDragonPhase.HOLDING_PATTERN;
			}
			if (phase == EnderDragonPhase.TAKEOFF) return DragonPhases.LIFTOFF;
		}
		if (context == Context.WILD) {
			if (phase == EnderDragonPhase.HOLDING_PATTERN || phase == EnderDragonPhase.STRAFE_PLAYER
					|| phase == EnderDragonPhase.LANDING_APPROACH) return DragonPhases.ROAM;
			if (phase == EnderDragonPhase.TAKEOFF) return DragonPhases.LIFTOFF;
		}
		return phase;
	}

	/** Whether the dragon keeps moving at x, z: entities freeze in chunks that are loaded but not ticking. */
	public boolean ticking(int x, int z) {
		return dragon.level() instanceof ServerLevel level && level.isPositionEntityTicking(new BlockPos(x, 0, z));
	}

	// ---------------------------------------------------------------- dying

	/** On its last flight (vanilla's dying phase, health held at 1 until it is over). */
	public boolean dying() {
		return dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING;
	}

	/**
	 * Server, as the last flight starts: whatever it was doing stops (it lets go of its prey, takes its feet
	 * off a foothold), it cries out, and it heads for the altar of the End fight's island, or, a wild dragon,
	 * only up.
	 */
	public void startDeathFlight() {
		clearAction();
		setFoothold(Foothold.STAND);
		if (prey.hold() != Grip.Hold.NONE) prey.release(null);
		setLookTarget(null);
		roar();
		double[] altar = null;
		if (dragon.getDragonFight() != null) {
			BlockPos top = dragon.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, EndPodiumFeature.getLocation(dragon.getFightOrigin()));
			altar = new double[]{top.getX() + 0.5, top.getY(), top.getZ() + 0.5};
		}
		death.start(dragon.getX(), dragon.getY(), dragon.getZ(), altar);
	}

	/** Server, each tick of the last flight: once it is over, the dragon dies (the cocoon, vanilla's light and rise). */
	public void deathTick() {
		boolean blocked = death.stage() == DeathFlight.Stage.RISE && dragon.verticalCollision && dragon.getDeltaMovement().y >= 0.0;
		if (death.tick(dragon.getX(), dragon.getY(), dragon.getZ(), blocked) == DeathFlight.Stage.DONE) {
			dragon.setDeltaMovement(Vec3.ZERO);
			dragon.setHealth(0.0F);
		}
	}

	/** Where the last flight heads now. */
	public Vec3 deathTarget() {
		return new Vec3(death.targetX(), death.targetY(), death.targetZ());
	}

	// ---------------------------------------------------------------- saving

	public void save(CompoundTag tag) {
		CompoundTag own = new CompoundTag();
		own.putString("Context", context.name());
		tag.put("Dragonsworn", own);
	}

	public void load(CompoundTag tag) {
		if (!tag.contains("Dragonsworn")) return;
		CompoundTag own = tag.getCompound("Dragonsworn");
		try {
			context = Context.valueOf(own.getString("Context"));
		} catch (IllegalArgumentException e) {
			context = Context.UNKNOWN;
		}
		if (context == Context.ARENA) context = Context.UNKNOWN;   // re-detected from the End fight
	}
}
