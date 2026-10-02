package crazylimits.dragonsworn.mc;

import crazylimits.dragonsworn.ai.AirTactics;
import crazylimits.dragonsworn.ai.CombatStance;
import crazylimits.dragonsworn.ai.DeathFlight;
import crazylimits.dragonsworn.ai.HitTally;
import crazylimits.dragonsworn.ai.Roaming;
import crazylimits.dragonsworn.anim.AnimClock;
import crazylimits.dragonsworn.anim.BreathAttack;
import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.anim.DragonAnimSelector;
import crazylimits.dragonsworn.anim.DragonAnimSelector.Kind;
import crazylimits.dragonsworn.anim.DragonVoice;
import crazylimits.dragonsworn.body.DragonBody;
import crazylimits.dragonsworn.config.DragonConfig;
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
import crazylimits.dragonsworn.mc.breath.BreathStreamPhase;
import crazylimits.dragonsworn.mc.phase.BreathPassPhase;
import crazylimits.dragonsworn.mc.phase.DragonswornPhase;
import crazylimits.dragonsworn.mc.phase.FlybyBitePhase;
import crazylimits.dragonsworn.mc.phase.GroundApproachPhase;
import crazylimits.dragonsworn.mc.phase.HoverAttackPhase;
import crazylimits.dragonsworn.mc.phase.RoamPhase;
import crazylimits.dragonsworn.mc.phase.SnatchPhase;
import crazylimits.dragonsworn.nav.AirPlanner;
import crazylimits.dragonsworn.nav.BlockGrid;
import crazylimits.dragonsworn.nav.Foothold;
import crazylimits.dragonsworn.nav.LandingSite;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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
 * <h2>Flying</h2>
 * The dragon no longer flies through blocks: its hull (head to hips) collides with anything solid that
 * it cannot break, and it plans around obstacles ({@link AirPlanner}). Leaves and plants
 * ({@code #dragonsworn:dragon_breakable}) are smashed with their breaking sound, like a ravager. Speed
 * comes from wingbeats ({@link FlightModel}).
 */
public final class DragonBrain {
	public enum Context { UNKNOWN, ARENA, WILD }

	/** Height of the flying hull's center above the dragon's position (blocks). */
	static final double BODY_CENTER = 3.6;
	/** Hover: gravity, and the lift of a full downstroke that balances it on average over a beat. */
	static final double HOVER_GRAVITY = 0.01, HOVER_LIFT = HOVER_GRAVITY / (0.4 * 2 / Math.PI);
	/** Parts that make up the solid hull: head, both necks, chest, hips, tail root. */
	private static final int[] HULL = {0, 1, 8, 2, 9, 3};
	/** Walking: the hull without the tail root (the tail lays itself round blocks, {@link Tail}). */
	private static final int[] WALK_HULL = {0, 1, 8, 2, 9};
	/** Standing: what is pushed out of a wall it turned into (the head and neck bend away by themselves). */
	private static final int[] GROUND_CORE = {8, 2, 9};
	/** How far a hull caught in a block is pushed out per tick, at most (blocks; tried smallest first). */
	private static final double[] PUSH_OUT = {0.15, 0.4, 0.8};
	/** Ticks wedged in terrain (nothing frees it) before it may move through it to get out. */
	private static final int ESCAPE_TICKS = 60;
	/** Flight: how far ahead (ticks of its velocity) it looks for what it is about to fly into. */
	private static final double LOOKAHEAD_TICKS = 14.0;
	/** Ticks between checks that the way ahead is still clear, and between full replans of a route. */
	private static final int CHECK_TICKS = 5, REPLAN_TICKS = 40;
	/** Rise over run past which flight cannot climb to a waypoint: it hovers up instead. */
	private static final double STEEP = 0.8;
	private static final int MAX_BREAKS_PER_TICK = 64;
	private static final TargetingConditions ARENA_TARGET = TargetingConditions.forCombat().range(150.0);
	/**
	 * A narrow foothold beside its prey: this far from it at least, at most and preferably (blocks). It
	 * cannot walk in from there, so within the bite's reach (the jaws strike ~6 blocks ahead).
	 */
	private static final double[] NARROW_RANGE = {4.0, 6.5, 5.0};

	private final EnderDragon dragon;
	public final DragonBody body = new DragonBody();
	public final AnimClock clock = new AnimClock();
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
	private final double[] groundHeights = new double[4];
	private final FlightModel flight = new FlightModel();
	private final PartSolver solver = new PartSolver();
	private final TailMotion.Pose tailMotion = new TailMotion.Pose();
	private final Tail.World tailWorld = new Tail.World();
	private final double[] offsets = new double[PoseTrack.PARTS * 3];
	private int bodyTickedAt = Integer.MIN_VALUE;

	private Context context = Context.UNKNOWN;
	private int attackCooldown = DragonConfig.FIRST_ATTACK.get(), groundCooldown = DragonConfig.ARENA_FIRST_ASSAULT.get(), scanCooldown, landCooldown;
	/** Wild: ticks of roaming flight left before it looks for somewhere to land (a first short one). */
	private int airLeft = 300;
	private boolean landed;
	/** Arena: vanilla's dragon chose to land on the exit portal; it perches by a player instead. */
	private boolean perchWanted;
	private LivingEntity target, lastAttacker;
	/** The target last found with nowhere to land by it (on a wall, up a pillar), and when. */
	private LivingEntity walled;
	private int walledAt;
	private Entity lastHurtBy;
	private int lastHurtAt = Integer.MIN_VALUE / 2;
	private int actionSequence;
	/** Ticks of flight left before the next roar (server). */
	private int roarIn = DragonVoice.AIR_ROAR_MIN_TICKS;
	/** The fireball charging (server): what it is for and ticks into its windup; null for none. */
	private LivingEntity fireballTarget;
	private int fireballTicks;
	/** What the head watched before the fireball turned it to its target (server; -1: nothing). */
	private int fireballLookBefore = -1;
	/**
	 * When the last fireball started charging: the server's own, the client's view of {@link DragonData#FIREBALL}
	 * (the count last seen, null before the first tick, and when it changed).
	 */
	private Integer fireballsSeen;
	private int fireballSeenAt = Integer.MIN_VALUE / 2;

	private List<double[]> route = List.of();
	private int routeIndex;
	private boolean routeClimb;
	private Vec3 routeGoal;
	private long routeCheckedAt = Long.MIN_VALUE, replanAt = Long.MIN_VALUE;
	/** A swerve away from what is straight ahead, held until {@link #evadeUntil}. */
	private Vec3 evade;
	private long evadeUntil = Long.MIN_VALUE;
	private int stuckTicks, wedgedTicks;

	public DragonBrain(EnderDragon dragon) {
		this.dragon = dragon;
		this.prey = new PreyHold(dragon, this);
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
		return flight.beatPhase(dragon.level().getGameTime());
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
				flightPlan(), horizontalSpeed());
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

	/** In a phase that attacks (the charge, the strafe, the perched breath, the snatch, the breath pass, a hold): no roaring through it. */
	public boolean attacking() {
		EnderDragonPhase<?> phase = dragon.getPhaseManager().getCurrentPhase().getPhase();
		return phase == EnderDragonPhase.CHARGING_PLAYER || phase == EnderDragonPhase.STRAFE_PLAYER
				|| phase == EnderDragonPhase.SITTING_FLAMING || kind() == Kind.PERCH_BREATH
				|| phase == DragonPhases.SNATCH || phase == DragonPhases.BREATH_PASS || phase == DragonPhases.FLYBY_BITE
				|| phase == DragonPhases.HOVER_ATTACK || prey.hold() != Grip.Hold.NONE;
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
		solver.solve(clock.anim(), shown, clock.from(), clock.fromShownSeconds(), clock.blend(1.0F), clock.changes(), body, strike, 1.0F,
				tailMotion, tailWorld, offsets);
		EnderDragonPart[] parts = dragon.getSubEntities();
		for (int i = 0; i < parts.length && i < PoseTrack.PARTS; i++) {
			parts[i].setPos(dragon.getX() + offsets[i * 3], dragon.getY() + offsets[i * 3 + 1] - parts[i].getBbHeight() / 2.0,
					dragon.getZ() + offsets[i * 3 + 2]);
		}
		if (!dragon.level().isClientSide) {
			breakSoftBlocks(parts);
			pushOut(parts);
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
		boolean aiming = BreathAttack.fireballAiming(fireballGlowTicks(0.0F));
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

	/**
	 * The pose just placed may have turned or swung the hull into a wall (a turn swings the head and
	 * hips, a wingbeat heaves the body): the dragon is pushed back out, a little per tick, the way that
	 * frees the most. On its feet only sideways (its height is the ground's).
	 */
	private void pushOut(EnderDragonPart[] parts) {
		boolean ground = onGround();
		if (!ground && !collides(dragon.getPhaseManager().getCurrentPhase())) {
			wedgedTicks = 0;
			return;
		}
		BlockGrid grid = grid();
		AABB[] hull = hull(ground ? GROUND_CORE : HULL);
		int inside = overlap(grid, hull, Vec3.ZERO);
		if (inside == 0) {
			wedgedTicks = 0;
			return;
		}
		Vec3 best = null;
		int least = inside;
		for (double d : PUSH_OUT) {
			for (Vec3 dir : ground ? SIDEWAYS : AROUND) {
				Vec3 shift = dir.scale(d);
				int n = overlap(grid, hull, shift);
				if (n < least) {
					least = n;
					best = shift;
				}
			}
			if (least == 0) break;
		}
		if (best == null) {
			wedgedTicks++;
			return;
		}
		wedgedTicks = 0;
		dragon.setPos(dragon.getX() + best.x, dragon.getY() + best.y, dragon.getZ() + best.z);
		for (EnderDragonPart part : parts) part.setPos(part.getX() + best.x, part.getY() + best.y, part.getZ() + best.z);
	}

	private static final Vec3[] SIDEWAYS = {new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 0, -1),
			new Vec3(0.7071, 0, 0.7071), new Vec3(-0.7071, 0, 0.7071), new Vec3(0.7071, 0, -0.7071), new Vec3(-0.7071, 0, -0.7071)};
	/** Up first: out of a ceiling or off the ground is the likelier way out in flight. */
	private static final Vec3[] AROUND = {new Vec3(0, 1, 0), SIDEWAYS[0], SIDEWAYS[1], SIDEWAYS[2], SIDEWAYS[3],
			SIDEWAYS[4], SIDEWAYS[5], SIDEWAYS[6], SIDEWAYS[7], new Vec3(0, -1, 0)};

	/** World position of a part's center. */
	public Vec3 partCenter(int part) {
		return dragon.getSubEntities()[part].getBoundingBox().getCenter();
	}

	private void breakSoftBlocks(EnderDragonPart[] parts) {
		Level level = dragon.level();
		if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
		int broken = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (EnderDragonPart part : parts) {
			AABB box = part.getBoundingBox().inflate(0.25);
			for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
				for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
					for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
						pos.set(x, y, z);
						BlockState state = level.getBlockState(pos);
						if (state.isAir() || !LevelGrid.breakable(state)) continue;
						// drops, the block's breaking sound and particles: what a ravager does to leaves
						if (level.destroyBlock(pos.immutable(), true, dragon) && ++broken >= MAX_BREAKS_PER_TICK) return;
					}
				}
			}
		}
	}

	/** Vanilla's wall check, without the demolition: solid, unbreakable blocks in {@code box}. */
	public boolean inWall(AABB box) {
		return blocked(grid(), box);
	}

	private static boolean blocked(BlockGrid grid, AABB box) {
		return overlap(grid, box, false) > 0;
	}

	/** How many solid, unbreakable blocks {@code box} is in (stops at the first when {@code all} is false). */
	private static int overlap(BlockGrid grid, AABB box, boolean all) {
		int n = 0;
		for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX - 1e-7); x++) {
			for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY - 1e-7); y++) {
				for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ - 1e-7); z++) {
					if (!grid.blocked(x, y, z)) continue;
					if (!all) return 1;
					n++;
				}
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- end of tick

	public void tickEnd() {
		tickBody();     // dragons whose AI is off (or dying) still need a body for the renderer
		if (dragon.level().isClientSide) {
			int fireballs = dragon.getEntityData().get(DragonData.FIREBALL);
			if (fireballsSeen != null && fireballs != fireballsSeen) fireballSeenAt = dragon.tickCount;
			fireballsSeen = fireballs;
			return;
		}
		// a dying dragon lets go of what it holds
		if ((dragon.isDeadOrDying() || dying()) && prey.hold() != Grip.Hold.NONE) prey.release(null);
		prey.tick();
		fireballTick();
		if (dragon.isNoAi() || dragon.isDeadOrDying() || dying()) return;
		updateContext();
		roarTick();
		if (context == Context.WILD) wildTick();
		else if (context == Context.ARENA) arenaTick();
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

	/**
	 * Server: a fireball at {@code target}. The dragon heats up first, the stream breath's glow played
	 * {@link BreathAttack#FIREBALL_SPEEDUP} times faster (every client, on the change), and the fireball
	 * flies from the head once the glow reaches the jaw. Ignored while one is charging.
	 */
	public void chargeFireball(LivingEntity target) {
		if (fireballTarget != null) return;
		fireballTarget = target;
		fireballTicks = 0;
		fireballSeenAt = dragon.tickCount;
		fireballLookBefore = dragon.getEntityData().get(DragonData.LOOK);
		dragon.getEntityData().set(DragonData.FIREBALL, dragon.getEntityData().get(DragonData.FIREBALL) + 1);
	}

	private void fireballTick() {
		if (fireballTarget == null) return;
		if (dragon.isDeadOrDying() || dying() || !fireballTarget.isAlive() || fireballTarget.level() != dragon.level()) {
			endFireball();
			return;
		}
		// the head turns to the target through the windup (the phase's own look comes back after)
		setLookTarget(fireballTarget);
		if (++fireballTicks < BreathAttack.FIREBALL_WINDUP_TICKS) return;
		LivingEntity target = fireballTarget;
		Vec3 head = partCenter(0);
		Vec3 aim = new Vec3(target.getX() - head.x, target.getY(0.5) - head.y, target.getZ() - head.z);
		if (!headPointsAt(target)) {
			// behind it, or the head has not come round yet: wait for it, but never shoot backwards
			if (fireballTicks >= BreathAttack.FIREBALL_WINDUP_TICKS + BreathAttack.FIREBALL_AIM_TICKS) endFireball();
			return;
		}
		endFireball();
		Vec3 dir = aim.normalize();
		// out of the mouth, in front of the head
		Vec3 from = head.add(dir.scale(FIREBALL_MUZZLE));
		if (!dragon.isSilent()) dragon.level().levelEvent(null, 1017, dragon.blockPosition(), 0);
		DragonFireball fireball = new DragonFireball(dragon.level(), dragon, dir);
		fireball.moveTo(from.x, from.y, from.z, 0.0F, 0.0F);
		dragon.level().addFreshEntity(fireball);
	}

	/** How far in front of the head's center the fireball leaves the mouth (blocks). */
	private static final double FIREBALL_MUZZLE = 1.5;

	private void endFireball() {
		fireballTarget = null;
		// back to what it watched before (a phase that sets its own look does so again next tick)
		Entity before = fireballLookBefore < 0 ? null : dragon.level().getEntity(fireballLookBefore);
		setLookTarget(before instanceof LivingEntity living && living.isAlive() ? living : null);
	}

	/**
	 * Whether the head, as last posed with its look turn, points within {@link BreathAttack#FIREBALL_CONE}
	 * of {@code target}: the look's remaining error, the target from the eyes against where the head points.
	 */
	private boolean headPointsAt(Entity target) {
		double[] at = new double[3];
		PartSolver.toModel(body, 1.0F, target.getX() - dragon.getX(), target.getY(0.5) - dragon.getY(), target.getZ() - dragon.getZ(), at);
		if (!solver.lookAngles(at[0], at[1], at[2], lookWant)) return false;
		double yaw = lookWant[0] - look.yaw(), pitch = lookWant[1] - look.pitch();
		return yaw * yaw + pitch * pitch < BreathAttack.FIREBALL_CONE * BreathAttack.FIREBALL_CONE;
	}

	/** Client: ticks (fractional) since the last fireball's windup started, for the heat glow. */
	public double fireballGlowTicks(float partialTick) {
		return dragon.tickCount - fireballSeenAt + partialTick;
	}

	private void updateContext() {
		if (context != Context.UNKNOWN) return;
		if (dragon.getDragonFight() != null) {
			context = Context.ARENA;
		} else if (dragon.tickCount > 2) {
			context = Context.WILD;
		}
	}

	private BlockPos groundAt(BlockPos pos) {
		int y = grid().ground(pos.getX(), pos.getZ());
		return new BlockPos(pos.getX(), y == BlockGrid.NO_GROUND ? pos.getY() - 20 : y, pos.getZ());
	}

	/**
	 * Vanilla phases that assume the End's exit portal at 0, 0 are swapped for Dragonsworn's own on a
	 * wild dragon; leaving the ground is always the jump. The End fight's dragon never comes down on the
	 * portal: its landing approach becomes a perch beside a player ({@link #arenaTick}), so every takeoff
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
				perchWanted = true;
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

	private void wildTick() {
		DragonPhaseInstance phase = dragon.getPhaseManager().getCurrentPhase();
		// summoned dragons start hovering where they appeared: get going
		if (phase.getPhase() == EnderDragonPhase.HOVERING && dragon.tickCount > 40) {
			dragon.getPhaseManager().setPhase(DragonPhases.ROAM);
			return;
		}
		if (attackCooldown > 0) attackCooldown--;
		if (--scanCooldown <= 0) {
			scanCooldown = 20;
			target = findWildTarget();
		}
		// vanilla phases (the charge, death) head back to the fight origin: keep it where the dragon is
		if (dragon.tickCount % 100 == 0) dragon.setFightOrigin(groundAt(dragon.blockPosition()));
		stance.tick(target != null);
		if (onGround()) landed = true;
		if (landCooldown > 0) landCooldown--;
		if (!(phase instanceof RoamPhase roam)) return;
		if (landed) {
			// back in the air: a short flight before it comes down again
			landed = false;
			airLeft = Roaming.airSpell(ThreadLocalRandom.current());
		}
		airLeft--;
		if (!roam.idle()) return;
		// a fight is the ground's (lazy dragons): come down beside the target whenever there is room
		if (target != null && DragonConfig.LAND_TO_FIGHT.get() && stance.grounded() && landCooldown <= 0 && !airborne(target)) {
			landCooldown = DragonConfig.LANDING_RETRY.get();
			if (target.onGround()) {
				if (tryGroundAssault(target)) return;
				walled(target);
			}
		}
		if (target != null && attackCooldown <= 0) {
			AirTactics.Reach reach = reach(target);
			attack(roam, target, reach);
			// on a break in the air, or at a target it cannot land by, it attacks from there in earnest
			boolean earnest = !stance.grounded() || AirTactics.airborne(reach);
			attackCooldown = earnest ? DragonConfig.between(DragonConfig.EARNEST_COOLDOWN_MIN, DragonConfig.EARNEST_COOLDOWN_MAX, ThreadLocalRandom.current())
					: DragonConfig.between(DragonConfig.CASUAL_COOLDOWN_MIN, DragonConfig.CASUAL_COOLDOWN_MAX, ThreadLocalRandom.current());
		} else if (target == null && airLeft <= 0) {
			airLeft = 100;   // nowhere to land here: look again a little further on
			landAhead();
		}
	}

	/** Comes down somewhere along its way, to roam on foot for a while (it rests: no target). */
	private void landAhead() {
		float yaw = dragon.getYRot() * Mth.DEG_TO_RAD;
		double x = dragon.getX() + Mth.sin(yaw) * 24.0, z = dragon.getZ() - Mth.cos(yaw) * 24.0;
		int[] site = new LandingSite(grid()).find(x, z, 0, 24, 0, dragon.getX(), dragon.getZ());
		if (site != null && ticking(site[0], site[2])) GroundApproachPhase.start(dragon, site, null);
	}

	/** Whether the dragon keeps moving at x, z: entities freeze in chunks that are loaded but not ticking. */
	public boolean ticking(int x, int z) {
		return dragon.level() instanceof ServerLevel level && level.isPositionEntityTicking(new BlockPos(x, 0, z));
	}

	private LivingEntity findWildTarget() {
		if (lastAttacker != null && (!lastAttacker.isAlive() || lastAttacker.distanceToSqr(dragon) > DragonConfig.FORGET_RANGE.get() * DragonConfig.FORGET_RANGE.get()
				|| lastAttacker instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			lastAttacker = null;
		}
		if (lastAttacker != null) return lastAttacker;
		double range = DragonConfig.HUNT_RANGE.get();
		return range <= 0.0 ? null : dragon.level().getNearestPlayer(TargetingConditions.forCombat().range(range), dragon);
	}

	/**
	 * Whether {@code target} is in the air: gliding on elytra, flying, or with nothing under it for a few
	 * blocks (a jump is not). There is no landing beside it then: the dragon fights it in the air.
	 */
	public boolean airborne(LivingEntity target) {
		if (target.onGround()) return false;
		if (target.isFallFlying() || target instanceof Player p && p.getAbilities().flying) return true;
		return dragon.level().noCollision(target.getBoundingBox().expandTowards(0.0, -DragonConfig.AIRBORNE_GAP.get(), 0.0));
	}

	/** Remembers that {@code target} stands where the dragon cannot come down beside it. */
	private void walled(LivingEntity target) {
		walled = target;
		walledAt = dragon.tickCount;
	}

	/** Where {@code target} is for an attack from the air: in it, on ground it cannot land by (lately found so), or on open ground. */
	private AirTactics.Reach reach(LivingEntity target) {
		boolean walledNow = walled == target && dragon.tickCount - walledAt < DragonConfig.WALLED_TICKS.get();
		return AirTactics.reach(airborne(target), !walledNow);
	}

	/**
	 * One attack on a target from the air (the landing to fight on foot is {@link #wildTick}'s): the
	 * first of {@link AirTactics#choices} that can start. In the End fight ({@code roam} null) vanilla's
	 * strafe makes the fireball attacks.
	 */
	private void attack(RoamPhase roam, LivingEntity target, AirTactics.Reach reach) {
		for (AirTactics.Attack attack : AirTactics.choices(reach, ThreadLocalRandom.current().nextDouble())) {
			if (start(attack, roam, target)) return;
		}
	}

	private boolean start(AirTactics.Attack attack, RoamPhase roam, LivingEntity target) {
		switch (attack) {
			case SNATCH:
				return SnatchPhase.start(dragon, target);
			case BREATH_PASS:
				return BreathPassPhase.start(dragon, target);
			case FLYBY_BITE:
				return FlybyBitePhase.start(dragon, target);
			case HOVER_BITE:
				return HoverAttackPhase.start(dragon, target, HoverAttackPhase.Mode.BITE);
			case HOVER_BREATH:
				return HoverAttackPhase.start(dragon, target, HoverAttackPhase.Mode.BREATH);
			case CHARGE:
				if (!dragon.hasLineOfSight(target)) return false;
				dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
				dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(target.position());
				return true;
			default:
				// the fireball pass and the barrage: the roam's; vanilla's strafe in the End fight
				if (roam == null) {
					dragon.getPhaseManager().setPhase(EnderDragonPhase.STRAFE_PLAYER);
					dragon.getPhaseManager().getPhase(EnderDragonPhase.STRAFE_PLAYER).setTarget(target);
				} else if (attack == AirTactics.Attack.FIREBALL_PASS) {
					roam.startPass(target);
				} else {
					roam.startBarrage(target);
				}
				return true;
		}
	}

	private void arenaTick() {
		if (perchWanted) {
			perchWanted = false;
			if (dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.HOLDING_PATTERN && perchByPlayer()) return;
		}
		if (groundCooldown > 0) {
			groundCooldown--;
			return;
		}
		if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.HOLDING_PATTERN) return;
		groundCooldown = DragonConfig.ARENA_RETRY.get();     // retry soon when nobody is on open ground
		BlockPos origin = dragon.getFightOrigin();
		Player player = dragon.level().getNearestPlayer(ARENA_TARGET, dragon, origin.getX(), origin.getY(), origin.getZ());
		if (player == null || player.distanceToSqr(Vec3.atCenterOf(origin)) > island() * island()) return;
		// in the air (elytra): fought there
		if (airborne(player)) {
			attack(null, player, AirTactics.Reach.AIR);
			groundCooldown = DragonConfig.between(DragonConfig.ARENA_AFTER_AIR_MIN, DragonConfig.ARENA_AFTER_AIR_MAX, ThreadLocalRandom.current());
			return;
		}
		if (!player.onGround()) return;
		// now and then a snatch or a breath pass instead of a landing
		double r = ThreadLocalRandom.current().nextDouble(), snatch = DragonConfig.ARENA_SNATCH_CHANCE.get();
		boolean pass = r < snatch ? DragonConfig.SNATCH.get() && SnatchPhase.start(dragon, player)
				: r < snatch + DragonConfig.ARENA_BREATH_PASS_CHANCE.get() && DragonConfig.BREATH_PASS.get() && BreathPassPhase.start(dragon, player);
		if (pass) {
			groundCooldown = DragonConfig.between(DragonConfig.ARENA_AFTER_PASS_MIN, DragonConfig.ARENA_AFTER_PASS_MAX, ThreadLocalRandom.current());
			return;
		}
		if (!DragonConfig.ARENA_GROUND_ASSAULT.get()) return;
		if (tryGroundAssault(player)) {
			boolean crystals = dragon.getDragonFight() != null && dragon.getDragonFight().getCrystalsAlive() > 0;
			groundCooldown = crystals
					? DragonConfig.between(DragonConfig.ARENA_AFTER_LANDING_MIN, DragonConfig.ARENA_AFTER_LANDING_MAX, ThreadLocalRandom.current())
					: DragonConfig.between(DragonConfig.ARENA_NO_CRYSTALS_MIN, DragonConfig.ARENA_NO_CRYSTALS_MAX, ThreadLocalRandom.current());
			return;
		}
		// nowhere to land by it (up a spire, pillaring up to a crystal): it comes to fight it in the air there
		attack(null, player, AirTactics.Reach.WALL);
		groundCooldown = DragonConfig.between(DragonConfig.ARENA_AFTER_AIR_MIN, DragonConfig.ARENA_AFTER_AIR_MAX, ThreadLocalRandom.current());
	}

	/** Arena: how far from the fight's origin a player counts as on the island (blocks). */
	private static double island() {
		return DragonConfig.ISLAND.get();
	}

	/**
	 * Arena: perches on the island beside the player nearest the dragon (on the ground, within
	 * {@link #island} of the fight's origin), if there is room to land there; else it flies on, and
	 * vanilla's holding pattern decides to land again later.
	 */
	private boolean perchByPlayer() {
		BlockPos origin = dragon.getFightOrigin();
		Vec3 center = Vec3.atCenterOf(origin);
		Player player = dragon.level().getNearestPlayer(ARENA_TARGET.copy().selector(p -> p.distanceToSqr(center) < island() * island()),
				dragon, dragon.getX(), dragon.getY(), dragon.getZ());
		if (player == null) return false;
		int[] site = landingSiteBy(player);
		if (site == null) return false;
		GroundApproachPhase.perch(dragon, site);
		return true;
	}

	/** A landing site beside {@code target}, on its level; null when there is no room. */
	private int[] landingSiteBy(LivingEntity target) {
		int[] site = new LandingSite(grid()).find(target.getX(), target.getZ(), 8, 17, 12, dragon.getX(), dragon.getZ());
		return site == null || Math.abs(site[1] - target.getY()) > 5 ? null : site;
	}

	/**
	 * Lands near the target to fight it on foot, if there is room to land there. With no room for all
	 * four limbs, it comes down on a narrow foothold ({@link Foothold}) within a bite of it instead: sat up
	 * on a ledge, or clinging to a pillar's top.
	 */
	public boolean tryGroundAssault(LivingEntity target) {
		int[] site = landingSiteBy(target);
		if (site != null) {
			GroundApproachPhase.start(dragon, site, target, Foothold.STAND);
			return true;
		}
		if (!DragonConfig.NARROW_FOOTHOLDS.get()) return false;
		LandingSite sites = new LandingSite(grid());
		for (Foothold foothold : new Foothold[]{Foothold.UPRIGHT, Foothold.CLING}) {
			site = sites.near(target.getX(), target.getY(), target.getZ(), NARROW_RANGE[0], NARROW_RANGE[1], NARROW_RANGE[2],
					dragon.getX(), dragon.getZ(), foothold);
			if (site == null || !ticking(site[0], site[2])) continue;
			GroundApproachPhase.start(dragon, site, target, foothold);
			return true;
		}
		return false;
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

	/** A blow at part {@code part} (index into the parts; -1: the dragon itself), whether it hurts or not (server). */
	public void hurtBy(DamageSource source, int part) {
		if (source.getEntity() instanceof LivingEntity attacker && attacker != dragon) lastAttacker = attacker;
		// someone else going for the head makes it drop what it holds in its jaws
		boolean head = part == 0 || part == 1 || part == 8;
		Entity by = source.getEntity();
		if (head && !dragon.level().isClientSide && by != null && by != dragon && prey.hold() == Grip.Hold.JAW && by != prey.prey()) {
			prey.release(new Vec3(0.0, 0.1, 0.0));
		}
	}

	/** A hit that took {@code lost} health off (server). */
	public void hit(DamageSource source, float lost) {
		lastHurtBy = source.getEntity();
		lastHurtAt = dragon.tickCount;
		hits.hit(dragon.tickCount);
		if (context == Context.WILD) stance.hurt(lost / dragon.getMaxHealth(), ThreadLocalRandom.current());
	}

	/** The living thing that hurt the dragon within the last {@code ticks}, or null. */
	public LivingEntity recentAttacker(int ticks) {
		if (!(lastHurtBy instanceof LivingEntity living) || !living.isAlive() || dragon.tickCount - lastHurtAt > ticks) return null;
		return living instanceof Player p && (p.isCreative() || p.isSpectator()) ? null : living;
	}

	/** Whether {@code attacker} hurt the dragon (arrows count as their archer) within the last {@code ticks}. */
	public boolean hurtRecentlyBy(Entity attacker, int ticks) {
		return attacker != null && attacker == lastHurtBy && dragon.tickCount - lastHurtAt <= ticks;
	}

	// ---------------------------------------------------------------- flying

	/** One tick of flight toward {@code target}, replacing vanilla's (server only). */
	public void fly(DragonPhaseInstance phase, Vec3 target) {
		long tick = dragon.level().getGameTime();
		boolean collide = collides(phase);
		Vec3 aim = collide ? route(target, tick) : target;
		Vec3 v = dragon.getDeltaMovement();
		FlightModel.Plan before = flight.plan();
		FlightModel.Force force = force(phase, target);
		// a way round terrain that goes steeply up (over a wall it is facing): it rises on its wings,
		// hovering; a steep climb the phase asked for itself (the snatch's) is flown
		if (collide && force == FlightModel.Force.NONE && (aim != target || dragon.horizontalCollision) && steep(aim)) {
			force = FlightModel.Force.HOVER;
		}
		FlightModel.Plan plan = flight.update(tick, v.y, dragon.yRotA * 0.1, v.horizontalDistance(), force,
				ThreadLocalRandom.current());
		if (plan != before) dragon.getEntityData().set(DragonData.FLIGHT, plan.encode());
		if (plan.mode() == FlightModel.Mode.HOVER) hoverStep(phase, aim, tick, collide);
		else flightStep(phase, aim, tick, collide);
	}

	/**
	 * Forces the wings into a mode outside of {@link #fly} (the jump of a takeoff), its beat already at
	 * phase {@code u} (the takeoff's power stroke carries on into the hover's beat).
	 */
	public void forceFlight(FlightModel.Force force, double u) {
		long tick = dragon.level().getGameTime();
		FlightModel.Plan before = flight.plan();
		FlightModel.Plan plan = flight.update(tick, 0, 0, 0, force, ThreadLocalRandom.current());
		if (plan != before) {
			flight.startAtPhase(tick, u);
			dragon.getEntityData().set(DragonData.FLIGHT, plan.encode());
		}
	}

	private FlightModel.Force force(DragonPhaseInstance phase, Vec3 target) {
		if (phase instanceof DragonswornPhase own) return own.flightForce();
		EnderDragonPhase<?> id = phase.getPhase();
		if (id == EnderDragonPhase.HOVERING) return FlightModel.Force.HOVER;
		if (id == EnderDragonPhase.DYING) return death.hovers(dragon.getX(), dragon.getZ()) ? FlightModel.Force.HOVER : FlightModel.Force.NONE;
		if (id == EnderDragonPhase.LANDING) {
			boolean close = dragon.getY() - target.y < 14 && Math.hypot(target.x - dragon.getX(), target.z - dragon.getZ()) < 12;
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
		double dy = aim.y - dragon.getY(), horizontal = Math.hypot(aim.x - dragon.getX(), aim.z - dragon.getZ());
		return dy > 4.0 && (dy > horizontal * STEEP || dragon.horizontalCollision);
	}

	/** Vanilla's portal landing and takeoff fly through the podium as before. */
	private boolean collides(DragonPhaseInstance phase) {
		if (phase instanceof DragonswornPhase own) return own.collides();
		EnderDragonPhase<?> id = phase.getPhase();
		if (id == EnderDragonPhase.LANDING || id == EnderDragonPhase.TAKEOFF) return false;
		return !phase.isSitting() || id == EnderDragonPhase.HOVERING;
	}

	/**
	 * Level and climbing flight, like vanilla's steering, but driven by the wings: forward acceleration
	 * is the downstroke's thrust plus what the glide gives back, more in a dive, less in a climb. Turning
	 * bleeds speed; slow, it sinks unless it beats (the flight model then beats continuously).
	 */
	private void flightStep(DragonPhaseInstance phase, Vec3 aim, long tick, boolean collide) {
		double dx = aim.x - dragon.getX(), dy = aim.y - dragon.getY(), dz = aim.z - dragon.getZ();
		double distSq = dx * dx + dy * dy + dz * dz;
		float flySpeed = phase.getFlySpeed();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		double climb = horizontal > 0 ? Mth.clamp(dy / horizontal, -flySpeed, flySpeed) : dy;
		dragon.setDeltaMovement(dragon.getDeltaMovement().add(0.0, climb * 0.01, 0.0));
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
		double accel = (FlightModel.GLIDE_ACCEL + flight.thrust(tick)) * (align * near + (1.0F - near))
				+ FlightModel.DIVE_GAIN * Math.max(0.0, -vy) - FlightModel.CLIMB_COST * Math.max(0.0, vy);
		dragon.moveRelative((float) Math.max(0.0, accel), new Vec3(0.0, 0.0, -1.0));
		// each downstroke heaves the body up, a lot when slow; slow and not beating, it sinks
		dragon.setDeltaMovement(dragon.getDeltaMovement().add(0.0, flight.lift(tick, dragon.getDeltaMovement().horizontalDistance()), 0.0));
		move(dragon.getDeltaMovement(), collide);
		Vec3 dir = dragon.getDeltaMovement().normalize();
		double drag = (0.8 + 0.15 * (dir.dot(facing) + 1.0) / 2.0) * FlightModel.turnDrag(dragon.yRotA * 0.1);
		dragon.setDeltaMovement(dragon.getDeltaMovement().multiply(drag, 0.91F, drag));
	}

	/**
	 * Standing in the air: drifts slowly toward the aim, turns to face it (or the phase's look target),
	 * and holds its height with the beats; every downstroke lifts it, gravity pulls it back between.
	 */
	private void hoverStep(DragonPhaseInstance phase, Vec3 aim, long tick, boolean collide) {
		double dx = aim.x - dragon.getX(), dz = aim.z - dragon.getZ();
		double horizontal = Math.hypot(dx, dz);
		Vec3 look = phase instanceof DragonswornPhase own ? own.hoverLook() : null;
		double fx = look != null ? look.x - dragon.getX() : dx, fz = look != null ? look.z - dragon.getZ() : dz;
		if (Math.hypot(fx, fz) > 1.5) {
			float want = (float) Math.toDegrees(Math.atan2(fx, -fz));
			float turn = Mth.clamp(Mth.wrapDegrees(want - dragon.getYRot()), -3.0F, 3.0F);
			dragon.setYRot(dragon.getYRot() + turn);
			dragon.yRotA = turn * 10.0F;
		} else {
			dragon.yRotA = 0.0F;
		}
		Vec3 v = dragon.getDeltaMovement();
		double want = Math.min(0.3, horizontal * 0.05);
		double tx = horizontal > 1e-3 ? dx / horizontal * want : 0.0, tz = horizontal > 1e-3 ? dz / horizontal * want : 0.0;
		double vx = v.x + (tx - v.x) * 0.08, vz = v.z + (tz - v.z) * 0.08;
		double error = aim.y - dragon.getY();
		double gain = Mth.clamp(1.0 + 0.2 * error - 4.0 * v.y, 0.2, 2.4);
		double vy = v.y * 0.96 + flight.stroke(tick) * HOVER_LIFT * gain - HOVER_GRAVITY;
		dragon.setDeltaMovement(vx, vy, vz);
		move(dragon.getDeltaMovement(), collide);
	}

	/**
	 * Moves by {@code delta}, the hull stopped by solid blocks: blocked axes are dropped (slides along
	 * walls), the vanilla collision flags are set (vanilla phases pick a new target on them) and the
	 * route is replanned. A hull already caught in a block (the pose swung it there) may move in any way
	 * that does not take it deeper, so it never passes through; only one wedged for
	 * {@link #ESCAPE_TICKS} (nothing pushes it out) moves freely to get out.
	 */
	public void move(Vec3 delta, boolean collide) {
		if (!collide) {
			dragon.move(MoverType.SELF, delta);
			return;
		}
		BlockGrid grid = grid();
		AABB[] hull = hull(HULL);
		int inside = overlap(grid, hull, Vec3.ZERO);
		if (inside > 0 && wedgedTicks > ESCAPE_TICKS) {
			dragon.move(MoverType.SELF, delta.add(0.0, 0.08, 0.0));
			stuck();
			return;
		}
		if (overlap(grid, hull, delta) <= inside) {
			dragon.move(MoverType.SELF, delta);
			dragon.horizontalCollision = dragon.verticalCollision = false;
			if (stuckTicks > 0) stuckTicks--;
			return;
		}
		double my = overlap(grid, hull, new Vec3(0.0, delta.y, 0.0)) > inside ? 0.0 : delta.y;
		double mx = overlap(grid, hull, new Vec3(delta.x, my, 0.0)) > inside ? 0.0 : delta.x;
		double mz = overlap(grid, hull, new Vec3(mx, my, delta.z)) > inside ? 0.0 : delta.z;
		dragon.move(MoverType.SELF, new Vec3(mx, my, mz));
		dragon.horizontalCollision = mx != delta.x || mz != delta.z;
		dragon.verticalCollision = my != delta.y;
		Vec3 v = dragon.getDeltaMovement();
		dragon.setDeltaMovement(mx != delta.x ? 0.0 : v.x, my != delta.y ? 0.0 : v.y, mz != delta.z ? 0.0 : v.z);
		stuck();
	}

	/**
	 * A step on its feet by dx, dz (server): the body (head to hips) is stopped by solid blocks the
	 * same way, sliding along a wall. Returns false when it was stopped (wholly or along one axis).
	 */
	public boolean walk(double dx, double dz) {
		BlockGrid grid = grid();
		AABB[] hull = hull(WALK_HULL);
		int inside = overlap(grid, hull, Vec3.ZERO);
		Vec3 delta = new Vec3(dx, 0.0, dz);
		double mx = dx, mz = dz;
		if (overlap(grid, hull, delta) > inside) {
			mx = overlap(grid, hull, new Vec3(dx, 0.0, 0.0)) > inside ? 0.0 : dx;
			mz = overlap(grid, hull, new Vec3(mx, 0.0, dz)) > inside ? 0.0 : dz;
		}
		dragon.setPos(dragon.getX() + mx, dragon.getY(), dragon.getZ() + mz);
		boolean free = mx == dx && mz == dz;
		dragon.horizontalCollision = !free;
		return free;
	}

	private void stuck() {
		stuckTicks++;
		replanAt = Long.MIN_VALUE;   // plan again now
	}

	public int stuckTicks() {
		return stuckTicks;
	}

	private AABB[] hull(int[] ids) {
		EnderDragonPart[] parts = dragon.getSubEntities();
		AABB[] out = new AABB[ids.length];
		for (int i = 0; i < ids.length; i++) out[i] = parts[ids[i]].getBoundingBox().deflate(0.15);
		return out;
	}

	/** Solid blocks the hull, moved by {@code delta}, is in (counted per box). */
	private static int overlap(BlockGrid grid, AABB[] hull, Vec3 delta) {
		int n = 0;
		for (AABB box : hull) n += overlap(grid, box.move(delta), true);
		return n;
	}

	/**
	 * Where to steer for {@code target}: straight at it when the way is clear (checked 48 blocks ahead),
	 * else the next waypoint of a planned route around what is in the way ({@link AirPlanner}); climbing
	 * when no route is found. Between replans the route is kept while its next leg is clear, waypoints
	 * already in straight view are skipped, and whatever its momentum is carrying it into within
	 * {@link #LOOKAHEAD_TICKS} makes it swerve at once ({@link #swerve}) and plan again.
	 */
	private Vec3 route(Vec3 target, long tick) {
		Vec3 center = dragon.position().add(0.0, BODY_CENTER, 0.0);
		double[] from = {center.x, center.y, center.z};
		AirPlanner planner = null;
		boolean replan = routeGoal == null || routeGoal.distanceToSqr(target) > 36.0 || tick >= replanAt;
		if (!replan && tick - routeCheckedAt >= CHECK_TICKS) {
			routeCheckedAt = tick;
			planner = new AirPlanner(grid());
			Vec3 ahead = center.add(dragon.getDeltaMovement().scale(LOOKAHEAD_TICKS));
			if (!planner.lineClear(from, new double[]{ahead.x, ahead.y, ahead.z})) {
				replan = true;
			} else if (routeIndex < route.size() && !planner.lineClear(from, route.get(routeIndex))) {
				replan = true;
			}
		}
		if (replan) {
			routeCheckedAt = tick;
			routeGoal = target;
			if (planner == null) planner = new AirPlanner(grid());
			Vec3 goal = target.add(0.0, BODY_CENTER, 0.0);
			Vec3 toGoal = goal.subtract(center);
			Vec3 probe = toGoal.length() > 48.0 ? center.add(toGoal.normalize().scale(48.0)) : goal;
			if (planner.lineClear(from, new double[]{probe.x, probe.y, probe.z})) {
				route = List.of();
				routeClimb = false;
				replanAt = tick + CHECK_TICKS * 2;
			} else {
				route = planner.plan(from, new double[]{goal.x, goal.y, goal.z}, 1500);
				routeIndex = 0;
				routeClimb = route.isEmpty();
				replanAt = tick + REPLAN_TICKS;
			}
			// still carried toward a wall the new plan steers away from: swerve until it can turn
			Vec3 v = dragon.getDeltaMovement();
			Vec3 ahead = center.add(v.scale(LOOKAHEAD_TICKS));
			if (v.lengthSqr() > 0.01 && !planner.lineClear(from, new double[]{ahead.x, ahead.y, ahead.z})) {
				Vec3 next = routeClimb ? center.add(0.0, 12.0, 0.0)
						: routeIndex < route.size() ? new Vec3(route.get(routeIndex)[0], route.get(routeIndex)[1], route.get(routeIndex)[2]) : goal;
				evade = swerve(planner, center, v, next);
				evadeUntil = evade == null ? Long.MIN_VALUE : tick + CHECK_TICKS * 2;
			}
		}
		if (evade != null && tick < evadeUntil) return evade.subtract(0.0, BODY_CENTER, 0.0);
		evade = null;
		if (routeClimb) return dragon.position().add(dragon.getLookAngle().scale(-6.0)).add(0.0, 12.0, 0.0);
		while (routeIndex < route.size() && distanceSq(center, route.get(routeIndex)) < 36.0) routeIndex++;
		// a later waypoint already in straight view: cut the corner
		if (planner != null) {
			while (routeIndex + 1 < route.size() && planner.lineClear(from, route.get(routeIndex + 1))) routeIndex++;
		}
		if (routeIndex >= route.size()) return target;
		double[] p = route.get(routeIndex);
		return new Vec3(p[0], p[1] - BODY_CENTER, p[2]);
	}

	/**
	 * A point to swerve to when its momentum carries it into terrain: the clear direction (turned up to
	 * 90 degrees either way, pitched up or down, or straight up) nearest to where it wants to go next,
	 * or null when none is clear.
	 */
	private static Vec3 swerve(AirPlanner planner, Vec3 center, Vec3 velocity, Vec3 next) {
		double reach = Math.max(10.0, velocity.length() * LOOKAHEAD_TICKS);
		Vec3 forward = velocity.normalize();
		Vec3 want = next.subtract(center).normalize();
		double[] from = {center.x, center.y, center.z};
		Vec3 best = null;
		double bestScore = -Double.MAX_VALUE;
		for (int yaw = -90; yaw <= 90; yaw += 30) {
			for (int pitch : new int[]{0, 35, -25, 70}) {
				Vec3 dir = forward.yRot(yaw * Mth.DEG_TO_RAD);
				double horizontal = Math.max(1e-3, dir.horizontalDistance());
				double up = Math.tan(pitch * Mth.DEG_TO_RAD) * horizontal;
				dir = new Vec3(dir.x, Mth.clamp(dir.y + up, -0.9, 3.0), dir.z).normalize();
				Vec3 to = center.add(dir.scale(reach));
				if (!planner.lineClear(from, new double[]{to.x, to.y, to.z})) continue;
				// toward the next waypoint, and as little of a swerve as will do
				double score = dir.dot(want) + 0.5 * dir.dot(forward);
				if (score > bestScore) {
					bestScore = score;
					best = to;
				}
			}
		}
		if (best != null) return best;
		Vec3 up = center.add(0.0, reach, 0.0);
		return planner.lineClear(from, new double[]{up.x, up.y, up.z}) ? up : null;
	}

	private static double distanceSq(Vec3 a, double[] b) {
		double dx = a.x - b[0], dy = a.y - b[1], dz = a.z - b[2];
		return dx * dx + dy * dy + dz * dz;
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
