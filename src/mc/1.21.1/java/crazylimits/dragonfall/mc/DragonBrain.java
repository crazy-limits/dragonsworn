package crazylimits.dragonfall.mc;

import crazylimits.dragonfall.ai.HitTally;
import crazylimits.dragonfall.ai.Roaming;
import crazylimits.dragonfall.anim.AnimClock;
import crazylimits.dragonfall.anim.DragonAnim;
import crazylimits.dragonfall.anim.DragonAnimSelector;
import crazylimits.dragonfall.anim.DragonAnimSelector.Kind;
import crazylimits.dragonfall.anim.DragonVoice;
import crazylimits.dragonfall.body.DragonBody;
import crazylimits.dragonfall.body.Grip;
import crazylimits.dragonfall.body.PartSolver;
import crazylimits.dragonfall.body.PoseTrack;
import crazylimits.dragonfall.body.Strike;
import crazylimits.dragonfall.body.Tail;
import crazylimits.dragonfall.body.TailMotion;
import crazylimits.dragonfall.flight.FlightModel;
import crazylimits.dragonfall.limb.GroundFit;
import crazylimits.dragonfall.mc.breath.BreathStreamPhase;
import crazylimits.dragonfall.mc.phase.DragonfallPhase;
import crazylimits.dragonfall.mc.phase.GroundApproachPhase;
import crazylimits.dragonfall.mc.phase.RoamPhase;
import crazylimits.dragonfall.mc.phase.SnatchPhase;
import crazylimits.dragonfall.nav.AirPlanner;
import crazylimits.dragonfall.nav.BlockGrid;
import crazylimits.dragonfall.nav.LandingSite;
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
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Everything Dragonfall adds to one dragon. Lives on the entity (both sides); the server half decides
 * and moves, the client half only animates and places its own hitboxes the same way.
 *
 * <h2>Two kinds of dragon</h2>
 * <ul>
 *   <li><b>Arena</b>: the End fight's dragon. Vanilla's fight stays (pillar circuit, strafes, the perch
 *       on the exit portal), and now and then it lands next to a player on the island and fights on
 *       the ground; more often once the crystals are gone.</li>
 *   <li><b>Wild</b>: any other dragon (summoned with a command, anywhere). Nothing ties it to one place:
 *       it roams ({@link Roaming}), flying a while, landing somewhere along its way, walking about there
 *       and taking off again. It hunts players who come near (fireball passes, hovering barrages,
 *       charges, ground assaults).</li>
 * </ul>
 *
 * <h2>Flying</h2>
 * The dragon no longer flies through blocks: its hull (head to hips) collides with anything solid that
 * it cannot break, and it plans around obstacles ({@link AirPlanner}). Leaves and plants
 * ({@code #dragonfall:dragon_breakable}) are smashed with their breaking sound, like a ravager. Speed
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
	private static final TargetingConditions HUNT = TargetingConditions.forCombat().range(48.0);
	private static final TargetingConditions ARENA_TARGET = TargetingConditions.forCombat().range(150.0);

	private final EnderDragon dragon;
	public final DragonBody body = new DragonBody();
	public final AnimClock clock = new AnimClock();
	/** The roar sounding now (client). */
	public final DragonVoice.Roar roar = new DragonVoice.Roar();
	/** The bite, tail strike or stream breath aimed now (both sides, from {@link DragonData#STRIKE}). */
	public final Strike strike = new Strike();
	/** Hits taken (server): too many at once and a landed dragon takes off. */
	public final HitTally hits = new HitTally();
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
	private int attackCooldown = 100, groundCooldown = 900, scanCooldown;
	/** Wild: ticks of roaming flight left before it looks for somewhere to land (a first short one). */
	private int airLeft = 300;
	private boolean landed;
	private LivingEntity target, lastAttacker;
	private Entity lastHurtBy;
	private int lastHurtAt = Integer.MIN_VALUE / 2;
	private int actionSequence;
	/** Ticks of flight left before the next roar (server). */
	private int roarIn = DragonVoice.AIR_ROAR_MIN_TICKS;

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
		if (dragon.dragonDeathTime > 0 || dragon.isDeadOrDying()) return Kind.DYING;
		EnderDragonPhase<?> phase = dragon.getPhaseManager().getCurrentPhase().getPhase();
		if (phase == EnderDragonPhase.DYING) return Kind.DYING;
		if (phase == EnderDragonPhase.SITTING_SCANNING) return Kind.PERCH_SCANNING;
		if (phase == EnderDragonPhase.SITTING_FLAMING) return Kind.PERCH_FLAMING;
		if (phase == EnderDragonPhase.SITTING_ATTACKING) return Kind.PERCH_ATTACKING;
		if (phase == DragonPhases.GROUND_FIGHT) return Kind.GROUND;
		if (dragon.getPhaseManager().getCurrentPhase() instanceof BreathStreamPhase) return Kind.PERCH_BREATH;
		return Kind.AIR;
	}

	public FlightModel.Plan flightPlan() {
		return FlightModel.Plan.decode(dragon.getEntityData().get(DragonData.FLIGHT));
	}

	public DragonAnim action() {
		return DragonAnimSelector.actionAnim(dragon.getEntityData().get(DragonData.ACTION));
	}

	public DragonAnimSelector.Choice choice() {
		int bits = dragon.getEntityData().get(DragonData.ACTION);
		return DragonAnimSelector.select(kind(), DragonAnimSelector.actionAnim(bits), DragonAnimSelector.actionSequence(bits),
				flightPlan(), horizontalSpeed());
	}

	public double horizontalSpeed() {
		return Math.hypot(dragon.getX() - dragon.xo, dragon.getZ() - dragon.zo);
	}

	/** In a phase that attacks (the charge, the strafe, the perched breath, the snatch, a hold): no roaring through it. */
	public boolean attacking() {
		EnderDragonPhase<?> phase = dragon.getPhaseManager().getCurrentPhase().getPhase();
		return phase == EnderDragonPhase.CHARGING_PLAYER || phase == EnderDragonPhase.STRAFE_PLAYER
				|| phase == EnderDragonPhase.SITTING_FLAMING || kind() == Kind.PERCH_BREATH
				|| phase == DragonPhases.SNATCH || prey.hold() != Grip.Hold.NONE;
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
		if (footing()) return DragonBody.Mode.GROUND;
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
		for (int i = 0; i < 4; i++) {
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

	/** Puts every part on the model's bones (both sides), then (server) smashes soft blocks they touch. */
	public void placeParts() {
		tickBody();
		// the tail blends in from the last animation as the model does, and keeps out of the blocks
		TailMotion.sample(clock.anim(), clock.seconds(), clock.from(), clock.fromSeconds(), clock.blend(1.0F), tailMotion);
		tailWorld.set(grid(), body, 1.0F, dragon.getX(), dragon.getY(), dragon.getZ(), dragon.tickCount + 1.0);
		solver.solve(clock.anim(), clock.seconds(), body, strike, 1.0F, tailMotion, tailWorld, offsets);
		EnderDragonPart[] parts = dragon.getSubEntities();
		for (int i = 0; i < parts.length && i < PoseTrack.PARTS; i++) {
			parts[i].setPos(dragon.getX() + offsets[i * 3], dragon.getY() + offsets[i * 3 + 1] - parts[i].getBbHeight() / 2.0,
					dragon.getZ() + offsets[i * 3 + 2]);
		}
		if (!dragon.level().isClientSide) {
			breakSoftBlocks(parts);
			pushOut(parts);
		}
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
		if (dragon.level().isClientSide) return;
		// a dying dragon lets go of what it holds
		if (dragon.isDeadOrDying() && prey.hold() != Grip.Hold.NONE) prey.release(null);
		prey.tick();
		if (dragon.isNoAi() || dragon.isDeadOrDying()) return;
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
	 * Vanilla phases that assume the End's exit portal at 0, 0 are swapped for Dragonfall's own on a
	 * wild dragon; leaving the ground is always the jump.
	 */
	public EnderDragonPhase<?> remap(EnderDragonPhase<?> phase) {
		DragonPhaseInstance current = dragon.getPhaseManager().getCurrentPhase();
		EnderDragonPhase<?> from = current == null ? null : current.getPhase();
		if (phase == EnderDragonPhase.TAKEOFF && (from == DragonPhases.GROUND_FIGHT || from == DragonPhases.GROUND_APPROACH)) {
			return DragonPhases.LIFTOFF;
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
		if (onGround()) landed = true;
		if (!(phase instanceof RoamPhase roam)) return;
		if (landed) {
			// back in the air: a fresh spell of flight before it comes down again
			landed = false;
			airLeft = Roaming.airSpell(ThreadLocalRandom.current());
		}
		airLeft--;
		if (!roam.idle()) return;
		if (target != null && attackCooldown <= 0) {
			attack(roam, target);
			attackCooldown = ThreadLocalRandom.current().nextInt(140, 260);
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
		if (lastAttacker != null && (!lastAttacker.isAlive() || lastAttacker.distanceToSqr(dragon) > 128 * 128
				|| lastAttacker instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			lastAttacker = null;
		}
		if (lastAttacker != null) return lastAttacker;
		return dragon.level().getNearestPlayer(HUNT, dragon);
	}

	/** One attack on a target from the wild circuit. */
	private void attack(RoamPhase roam, LivingEntity target) {
		double r = ThreadLocalRandom.current().nextDouble();
		if (r < 0.15 && SnatchPhase.start(dragon, target)) return;
		if (r < 0.4 && target.onGround() && tryGroundAssault(target)) return;
		if (r < 0.7) {
			roam.startPass(target);
		} else if (r < 0.85 && dragon.hasLineOfSight(target)) {
			dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
			dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(target.position());
		} else {
			roam.startBarrage(target);
		}
	}

	private void arenaTick() {
		if (groundCooldown > 0) {
			groundCooldown--;
			return;
		}
		if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.HOLDING_PATTERN) return;
		groundCooldown = 100;     // retry soon when nobody is on open ground
		BlockPos origin = dragon.getFightOrigin();
		Player player = dragon.level().getNearestPlayer(ARENA_TARGET, dragon, origin.getX(), origin.getY(), origin.getZ());
		if (player == null || !player.onGround() || player.distanceToSqr(Vec3.atCenterOf(origin)) > 100 * 100) return;
		// now and then a snatch instead of a landing
		if (ThreadLocalRandom.current().nextDouble() < 0.3 && SnatchPhase.start(dragon, player)) {
			groundCooldown = ThreadLocalRandom.current().nextInt(500, 900);
			return;
		}
		if (tryGroundAssault(player)) {
			boolean crystals = dragon.getDragonFight() != null && dragon.getDragonFight().getCrystalsAlive() > 0;
			groundCooldown = crystals ? ThreadLocalRandom.current().nextInt(900, 1500) : ThreadLocalRandom.current().nextInt(400, 800);
		}
	}

	/** Lands near the target to fight it on foot, if there is room to land there. */
	public boolean tryGroundAssault(LivingEntity target) {
		int[] site = new LandingSite(grid()).find(target.getX(), target.getZ(), 8, 17, 12, dragon.getX(), dragon.getZ());
		if (site == null || Math.abs(site[1] - target.getY()) > 5) return false;
		GroundApproachPhase.start(dragon, site, target);
		return true;
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

	/** A hit that took health off (server). */
	public void hit(DamageSource source) {
		lastHurtBy = source.getEntity();
		lastHurtAt = dragon.tickCount;
		hits.hit(dragon.tickCount);
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
		if (phase instanceof DragonfallPhase own) return own.flightForce();
		EnderDragonPhase<?> id = phase.getPhase();
		if (id == EnderDragonPhase.HOVERING || id == EnderDragonPhase.DYING) return FlightModel.Force.HOVER;
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

	/** Vanilla's portal landing, takeoff and death fly through the podium as before. */
	private boolean collides(DragonPhaseInstance phase) {
		if (phase instanceof DragonfallPhase own) return own.collides();
		EnderDragonPhase<?> id = phase.getPhase();
		if (id == EnderDragonPhase.LANDING || id == EnderDragonPhase.TAKEOFF || id == EnderDragonPhase.DYING) return false;
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
		Vec3 look = phase instanceof RoamPhase roam ? roam.lookTarget() : null;
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
		tag.put("Dragonfall", own);
	}

	public void load(CompoundTag tag) {
		if (!tag.contains("Dragonfall")) return;
		CompoundTag own = tag.getCompound("Dragonfall");
		try {
			context = Context.valueOf(own.getString("Context"));
		} catch (IllegalArgumentException e) {
			context = Context.UNKNOWN;
		}
		if (context == Context.ARENA) context = Context.UNKNOWN;   // re-detected from the End fight
	}
}
