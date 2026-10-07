package crazylimits.dragonsworn.mc.phase;

import crazylimits.dragonsworn.ai.CombatStance;
import crazylimits.dragonsworn.ai.Foothold;
import crazylimits.dragonsworn.ai.GroundTactics;
import crazylimits.dragonsworn.ai.Roaming;
import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.body.Grip;
import crazylimits.dragonsworn.body.Parts;
import crazylimits.dragonsworn.body.PoseTrack;
import crazylimits.dragonsworn.body.Strike;
import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonPhases;
import crazylimits.dragonsworn.mc.DragonSounds;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.PreyHold;
import crazylimits.dragonsworn.mc.Targets;
import crazylimits.dragonsworn.mc.breath.BreathStreamPhase;
import crazylimits.dragonsworn.nav.BlockGrid;
import crazylimits.dragonsworn.nav.Burrow;
import crazylimits.dragonsworn.nav.GroundPlanner;
import crazylimits.dragonsworn.nav.Surface;
import crazylimits.dragonsworn.nav.SurfaceSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * On the ground. With a target it fights ({@link GroundTactics}): turns to face it head first, walks
 * in along a path around obstacles ({@link GroundPlanner}), bites what is in front, strikes with its tail
 * at what is beside it (or behind it, when hurt from there), beats its wings at what is too close for
 * either (the wing buffet: everything round its body is thrown away), turns round to what it cannot reach,
 * and roars at what stays out of reach while nobody is close (it slows whoever runs off or shoots from
 * afar). One blow at a time.
 *
 * <p>A blow is aimed ({@link Strike}): the jaws or the tail's tip go exactly to where the target was a
 * moment before the blow (the server's {@code dodge_window}), and only what is there when it lands is hit. Stepping
 * out of the way is a dodge. Hit too often at once (from its blind spots), it takes to the air
 * ({@link crazylimits.dragonsworn.ai.HitTally}). A wild dragon is lazy: it fights on foot for as long as it
 * takes, and takes a break in the air only when hurt too much ({@link CombatStance}). A target it cannot
 * reach (up a pillar, across a ditch: no blow reaches, no step gets it anywhere) is not stared at: after
 * {@code unreached_patience} it takes off and fights it from the air, where it will not land by it again for
 * a while ({@code Tactics#walled}).
 *
 * <p>Without one it roams on foot ({@link Roaming}): walks a stretch, stops and looks
 * around, walks on, and wakes for any player who comes close. After a while (a long one, for a wild
 * dragon), or when the target gets away, it takes off.
 *
 * <p>The seize (Skyrim's dragons): now and then the bite does not let go. It is a bite first: only one
 * that lands (its jaws close on the target, {@link #blow}) takes the prey; dodged, the jaws close on
 * nothing and nothing is held. The prey hangs from the jaws
 * ({@link Grip.Hold#JAW}), unable to move but free to use items (an ender pearl gets it out), and is
 * shaken and chewed (the server's {@code chew_damage} every {@code chew_interval}) until it is flung away. Anyone
 * else hitting the head or neck makes the dragon drop it ({@code DragonBrain#hurtBy}).
 *
 * <p>On a narrow foothold ({@link Foothold#UPRIGHT}: sat up on its hind feet, the wings out for balance;
 * {@link Foothold#CLING}: gripping a pillar's top, the wings beating) it fights with its head alone: it
 * turns to face its prey and bites (and seizes) what is in reach, but never walks, lashes its tail or
 * roars. Once its prey has been out of the jaws' reach for {@code narrow_patience} it takes off (and
 * looks for somewhere better); clinging tires it out after {@code cling_max}, and with nobody to fight
 * it does not stay up there long.
 *
 * <p>On a wall ({@code nav/Surface}: a cliff, a spire's flank) it is all the same: the wall is its ground,
 * in the face's frame ({@code DragonBrain#local}), so it climbs as it walks and fights as on the ground
 * (no wing buffet there: the wings are holding on). Prey it cannot walk any closer to (up a pillar, on a
 * ledge, down on the ground below) it hops after ({@link HopPhase}: onto the wall, over its top, round a
 * corner, down off it) before it gives up and takes off. Prey hiding in a tunnel too narrow for its head
 * ({@link Burrow}) it bites as far in as its jaws reach ({@code tunnel_reach}), or, deeper in, it pours its
 * breath down the tunnel ({@link BreathStreamPhase}) and fights on after.
 *
 * <p>Arrows hit it here (unlike the vanilla perch): ranged players earn these windows too. The head
 * and neck no longer hurt by touch; the bite does.
 */
public class GroundFightPhase extends AbstractDragonPhaseInstance implements DragonswornPhase {
	/** Only targets this close are worth asking the IK whether a blow reaches. */
	static final double STRIKE_RANGE = 14.0;
	/** The End's dragon: a player at its crystals this close (blocks) becomes its target on the ground; further, it takes off for them. */
	public static final double GUARD_NEAR = 24.0;
	/** A narrow foothold: how long it stays up there with nobody to fight (ticks). */
	static final int NARROW_REST = 100;
	/** How far into a tunnel's mouth its snout goes (blocks): the bite's aim; the jaws snap on past it. */
	static final double SNOUT_IN = 1.0;
	/** A hop must bring it at least this much nearer its prey (blocks); and it looks for one this often (ticks). */
	static final double HOP_GAIN = 3.0;
	static final int HOP_EVERY = 20;
	/** Ground this far under its position (blocks) is further than it steps down: what it stood on is gone, it falls. */
	static final double FALL = GroundPlanner.STEP_DOWN + 1.0;
	/** The breath down a tunnel: its mouth no further than this from the dragon (blocks). */
	static final double TUNNEL_BREATH_RANGE = 12.0;
	/*
	 * The rest is the server's (DragonConfig, [ground_combat]): the dodge window (REACTION_TICKS), the
	 * recoveries after a bite and a tail strike, how long a hit provokes (PROVOKED_TICKS), the damages, the
	 * seize (its chance, cooldown, hold and chewing: SEIZE_CHANCE, CHEW_INTERVAL), and on a narrow foothold
	 * NARROW_PATIENCE (prey out of the jaws' reach before it takes off) and CLING_MAX.
	 */

	@Nullable
	private LivingEntity target;
	/** How it stands: on all fours, or on a narrow foothold (no walking, no tail). */
	private Foothold foothold = Foothold.STAND;
	private int ticks, duration, lostTicks, pauseTicks, wanderTicks, unreachedTicks;
	/** The target it is closing in on. */
	@Nullable
	private LivingEntity chased;
	private double walkHeading;
	@Nullable
	private DragonAnim action;
	private int actionTicks;
	private boolean actionHit;
	/** The bite playing is a seize; the next bite will be one; how long the jaws have held, and for how long they will. */
	private boolean seizing, seizeNext;
	private int holdTicks, holdFor, seizeReadyAt;
	private int attackReadyAt, roarReadyAt;
	/** Where the blow playing is aimed (world). */
	@Nullable
	private Vec3 aim;
	/** Asks whether a blow would reach, without touching the dragon's own aim. */
	private final Strike probe = new Strike();
	/** Its steps on the ground: turning, and walking a path round what is in the way. */
	private final GroundWalker walker;
	@Nullable
	private int[] wander;
	/** Where it was at the start of the tick, in the frame of its face. */
	private double lastX, lastZ;
	/** The tunnel its target hides in (looked for now and then), or null; when the next breath down one may be. */
	@Nullable
	private Burrow burrow;
	private int breathReadyAt;
	/** Ticks it has got nowhere (under a block and a half every {@link #HOP_EVERY}) with its prey out of reach, and from where. */
	private int stalled;
	@Nullable
	private Vec3 stallFrom;

	public GroundFightPhase(EnderDragon dragon) {
		super(dragon);
		walker = new GroundWalker(dragon);
	}

	/** Lands (the dragon is on the ground already) and fights {@code target}, or rests when null. */
	public static void start(EnderDragon dragon, @Nullable LivingEntity target) {
		start(dragon, target, true);
	}

	/**
	 * As {@link #start(EnderDragon, LivingEntity)}; {@code thud}: it has just come down (hovering), all
	 * four feet at once. A running landing has struck the ground already and skidded out.
	 */
	public static void start(EnderDragon dragon, @Nullable LivingEntity target, boolean thud) {
		start(dragon, target, thud, Foothold.STAND);
	}

	/** As above, standing with {@code foothold}. */
	public static void start(EnderDragon dragon, @Nullable LivingEntity target, boolean thud, Foothold foothold) {
		dragon.getPhaseManager().setPhase(DragonPhases.GROUND_FIGHT);
		GroundFightPhase phase = dragon.getPhaseManager().getPhase(DragonPhases.GROUND_FIGHT);
		phase.foothold = foothold;
		DragonswornDragon.brain(dragon).setFoothold(foothold);
		if (foothold == Foothold.CLING) phase.duration = Math.min(phase.duration, DragonConfig.CLING_MAX.get());
		if (thud) phase.landingThud();
		phase.engage(target);
	}

	@Nullable
	@Override
	public LivingEntity attackTarget() {
		return target;
	}

	/** How it stands. */
	public Foothold foothold() {
		return foothold;
	}

	@Override
	public EnderDragonPhase<GroundFightPhase> getPhase() {
		return DragonPhases.GROUND_FIGHT;
	}

	@Override
	public boolean isSitting() {
		return true;
	}

	@Override
	public void begin() {
		target = null;
		foothold = Foothold.STAND;
		ticks = lostTicks = wanderTicks = unreachedTicks = 0;
		chased = null;
		// a wild dragon stays down a good while
		duration = brain().context() == DragonBrain.Context.WILD ? Roaming.groundSpell(ThreadLocalRandom.current())
				: DragonConfig.between(DragonConfig.ARENA_STAY_MIN, DragonConfig.ARENA_STAY_MAX, ThreadLocalRandom.current());
		pauseTicks = Roaming.pause(ThreadLocalRandom.current());
		walkHeading = Roaming.headingOfYaw(dragon.getYRot());
		action = null;
		walker.forgetPath();
		wander = null;
		attackReadyAt = 20;
		seizing = seizeNext = false;
		holdTicks = 0;
		seizeReadyAt = 100;
		roarReadyAt = 0;
		aim = null;
		brain().hits.clear();
		burrow = null;
		breathReadyAt = 0;
		stalled = 0;
		stallFrom = null;
		Vec3 here = brain().local();
		lastX = here.x;
		lastZ = here.z;
		dragon.setDeltaMovement(Vec3.ZERO);
	}

	private void engage(@Nullable LivingEntity target) {
		this.target = target;
		// sat up there is no roaring: the roar rears up on all fours; and only at a target out of reach
		if (target != null && !foothold.narrow() && DragonConfig.ROAR.get() && quiet()
				&& target.distanceTo(dragon) >= DragonConfig.ROAR_QUIET.get()) startAction(roarAnim());
	}

	/** Nobody close (within {@code roar_quiet_range}): a roar is for what is out of reach. */
	/** Whether {@code mob_buffet} players (or more) stand within the wing buffet's reach round its body. */
	private boolean mobbed(double range) {
		int mob = DragonConfig.MOB_BUFFET.get();
		if (mob <= 0) return false;
		List<Player> players = dragon.level().getEntitiesOfClass(Player.class, dragon.getBoundingBox().inflate(range), p -> p.isAlive() && !Targets.untouchable(p)
				&& Math.hypot(p.getX() - dragon.getX(), p.getZ() - dragon.getZ()) <= range && Math.abs(p.getY() - dragon.getY()) <= range * 0.6);
		return players.size() >= mob;
	}

	private boolean quiet() {
		double range = DragonConfig.ROAR_QUIET.get();
		return ((ServerLevel) dragon.level()).getNearestPlayer(TargetingConditions.forCombat().range(range), dragon) == null
				&& (target == null || target.distanceTo(dragon) >= range);
	}

	private DragonBrain brain() {
		return DragonswornDragon.brain(dragon);
	}

	@Override
	public void end() {
		if (brain().prey.hold() == Grip.Hold.JAW) brain().prey.release(null);
		if (action != null) brain().clearAction();
		action = null;
		aim = null;
		brain().setLookTarget(null);
	}

	@Override
	public void doServerTick(ServerLevel serverLevel) {
		ticks++;
		Vec3 here = brain().local();
		lastX = here.x;
		lastZ = here.z;
		if (!followGround()) return;
		// the head follows its prey, or resting, whoever comes near
		brain().setLookTarget(target != null ? target : dragon.level().getNearestPlayer(dragon, 32.0));
		// worn down from where it cannot answer: up and away
		if (DragonConfig.OVERWHELM_TAKEOFF.get() && brain().hits.overwhelmed(dragon.tickCount)) {
			brain().hits.clear();
			brain().stance.overwhelmed(ThreadLocalRandom.current());
			takeOff();
			return;
		}
		if (action != null) {
			runAction();
			return;
		}
		if (brain().prey.hold() == Grip.Hold.JAW || holdTicks > 0) {
			hold();
			return;
		}
		// on a wall only a short while: it came there from the air and leaves by it
		if (brain().face().wall() && brain().wallTicks() > DragonConfig.WALL_TIME.get()) {
			takeOff();
			return;
		}
		boolean wild = brain().context() == DragonBrain.Context.WILD;
		// hurt too much on the ground: a break in the air (it attacks from there)
		if (wild && target != null && !brain().stance.grounded()) {
			takeOff();
			return;
		}
		// a wild dragon fights on as long as it has someone to fight (clinging tires it out all the same)
		if (ticks > duration && !(wild && target != null && foothold != Foothold.CLING)) {
			takeOff();
			return;
		}
		if (target != null && (!target.isAlive() || Targets.untouchable(target))) target = null;
		// whoever is hurting it now (from a blind spot, while it watched another) becomes the target
		int provoked = DragonConfig.PROVOKED_TICKS.get();
		LivingEntity attacker = brain().combat.recentAttacker(provoked / 2);
		if (attacker != null && attacker != target && attacker.distanceToSqr(dragon) < 24.0 * 24.0
				&& !(target != null && brain().combat.hurtRecentlyBy(target, provoked))) target = attacker;
		// the End's dragon guards its crystals: whoever is at them comes first (close by it turns on them, far off it flies there)
		Player guarded = brain().guardTarget();
		if (guarded != null && guarded != target) {
			if (guarded.distanceToSqr(dragon) < GUARD_NEAR * GUARD_NEAR) {
				target = guarded;
			} else {
				takeOff();
				return;
			}
		}
		if (target == null) {
			rest();
			return;
		}
		// across the face it stands on (the ground's x, z; on a wall, the wall's)
		Vec3 t = brain().local(target.position()), at0 = brain().local();
		double dx = t.x - at0.x, dz = t.z - at0.z, rise = t.y - at0.y;
		double distance = Math.sqrt(dx * dx + dz * dz);
		// a climber goes after prey above or below it: only distance loses it
		boolean climbs = DragonConfig.CLIMBING.get();
		boolean far = distance > DragonConfig.LOSE_DISTANCE.get() || climbs && target.distanceTo(dragon) > DragonConfig.LOSE_DISTANCE.get();
		lostTicks = far || !climbs && Math.abs(rise) > 10 ? lostTicks + 1 : 0;
		if (lostTicks > DragonConfig.LOSE_TICKS.get()) {
			// far above or below it: no landing by it again for a while
			if (Math.abs(rise) > 10) brain().tactics.walled(target);
			takeOff();
			return;
		}
		if (ticks % 10 == 1 || chased != target) burrow = climbs ? Burrow.find(brain().grid(), target.getX(), target.getY(), target.getZ()) : null;
		float bearing = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(dx, -dz)) - dragon.getYRot());
		boolean ready = ticks >= attackReadyAt, near = ready && distance < STRIKE_RANGE;
		Vec3 at = strikePoint(target);
		boolean narrow = foothold.narrow();
		boolean biteReaches = (near || narrow) && distance < STRIKE_RANGE && reaches(bite(), at);
		// out of reach for too long, it leaves to fight from the air: on a narrow foothold (it cannot walk in) out of
		// its jaws' reach; standing, without a blow or a step on its way (arrived as close as it gets, or blocked)
		if (chased != target) {
			chased = target;
			unreachedTicks = 0;
		}
		unreachedTicks = biteReaches ? 0 : unreachedTicks + 1;
		// hiding in a tunnel past its bite: the breath down it
		if (!biteReaches && burrow != null && breathDown()) return;
		// stuck short of it (shuffling at a wall's foot counts): a hop across the ground that gets it nearer
		// (onto a ledge, down off one; never onto a wall: it gets there only from the air)
		if (biteReaches) {
			stalled = 0;
			stallFrom = null;
		} else if (ticks % HOP_EVERY == 0) {
			Vec3 now = brain().local();
			stalled = stallFrom != null && now.distanceTo(stallFrom) < 1.5 ? stalled + HOP_EVERY : 0;
			stallFrom = now;
			if (Math.max(stalled, unreachedTicks) >= DragonConfig.HOP_PATIENCE.get() && hop()) return;
		}
		// shuffling at a wall's foot (no hop gets it nearer) is getting nowhere too: it flies up there instead
		if (Math.max(unreachedTicks, stalled) > (narrow ? DragonConfig.NARROW_PATIENCE : DragonConfig.UNREACHED_PATIENCE).get()) {
			brain().tactics.walled(target);
			takeOff();
			return;
		}
		// what the server took away from it never reaches (the bite still decides when to stop walking in)
		boolean bite = DragonConfig.BITE.get();
		double buffet = DragonConfig.BUFFET_RANGE.get();
		// on a wall the wings are holding on: no buffet
		boolean buffets = DragonConfig.WING_BUFFET.get() && !brain().face().wall();
		boolean buffetReaches = buffets && distance < buffet && Math.abs(rise) < buffet * 0.6;
		GroundTactics.Decision decision = GroundTactics.decide(foothold, distance, bearing,
				near && biteReaches && bite, near && !narrow && !brain().face().wall() && DragonConfig.TAIL_STRIKE.get() && reaches(DragonAnim.TAIL_SWEEP, at), buffetReaches,
				buffets && ready && mobbed(buffet), ready,
				DragonConfig.ROAR.get() && ticks >= roarReadyAt && quiet(), brain().combat.hurtRecentlyBy(target, provoked), ThreadLocalRandom.current().nextDouble());
		switch (decision.action()) {
			case BITE -> {
				startAction(bite());
				seizing = PreyHold.holdable(target) && (seizeNext || DragonConfig.SEIZE.get() && ticks >= seizeReadyAt
						&& ThreadLocalRandom.current().nextDouble() < DragonConfig.SEIZE_CHANCE.get());
				seizeNext = false;
			}
			case TAIL_STRIKE -> startAction(DragonAnim.TAIL_SWEEP);
			case WING_BUFFET -> startAction(DragonAnim.WING_BUFFET);
			case ROAR -> startAction(roarAnim());
			case NONE -> {
				// on a wall it holds its foothold: it never walks there
				if (brain().face().wall()) break;
				// on a way round something it keeps walking, even where that leads away from the target
				if (decision.walk() || walker.detouring()) {
					// a step on its way is getting somewhere (round a wall it may lead away for a while)
					if (walker.walkToward(t.x, t.z, GroundTactics.CLOSE_IN, ticks)) {
						Vec3 now = brain().local();
						if (Math.hypot(now.x - lastX, now.z - lastZ) > 0.02) unreachedTicks = 0;
					}
				} else if (decision.turn()) {
					walker.turnToward(t.x, t.z);
				}
			}
		}
	}

	// ---------------------------------------------------------------- climbing

	/**
	 * A hop toward its target across the ground ({@link SurfaceSites#toward}): within {@code hop_range}, at
	 * least {@link #HOP_GAIN} nearer (to a tunnel's mouth, for prey hiding in one). False when none does,
	 * and always on a wall (it leaves one by flying).
	 */
	private boolean hop() {
		if (!DragonConfig.CLIMBING.get() || target == null || foothold.narrow() || brain().face().wall()) return false;
		double[] to = burrow != null ? burrow.mouth() : new double[]{target.getX(), target.getY(), target.getZ()};
		SurfaceSites.Site site = new SurfaceSites(brain().grid()).toward(dragon.getX(), dragon.getY(), dragon.getZ(),
				to[0], to[1], to[2], DragonConfig.HOP_RANGE.get(), HOP_GAIN);
		if (site == null) return false;
		double[] w = site.world();
		if (!brain().ticking(Mth.floor(w[0]), Mth.floor(w[2]))) return false;
		HopPhase.start(dragon, site, target);
		return true;
	}

	/**
	 * Its target hides in a tunnel beyond its bite, the mouth near: it pours its breath down it (and fights
	 * on after). False when it may not (switched off, too soon after the last, the mouth too far).
	 */
	private boolean breathDown() {
		if (!DragonConfig.TUNNEL_BREATH.get() || ticks < breathReadyAt || target == null) return false;
		double[] mouth = burrow.mouth();
		if (brain().partCenter(Parts.HEAD).distanceTo(new Vec3(mouth[0], mouth[1], mouth[2])) > TUNNEL_BREATH_RANGE) return false;
		double[] deep = burrow.at(burrow.depth());
		breathReadyAt = ticks + DragonConfig.TUNNEL_BREATH_COOLDOWN.get();
		BreathStreamPhase.pourDown(dragon, target, new Vec3(deep[0], deep[1], deep[2]));
		return true;
	}

	/** What the bite reaches down its target's tunnel past where the snout goes in: whoever is there is struck too. */
	private void tunnelStruck(List<LivingEntity> hit) {
		if (burrow == null) return;
		double reach = SNOUT_IN + DragonConfig.TUNNEL_REACH.get(), depth = Math.min(reach, burrow.depth());
		double[] mouth = burrow.mouth();
		for (LivingEntity living : dragon.level().getEntitiesOfClass(LivingEntity.class,
				new AABB(mouth[0], mouth[1], mouth[2], mouth[0], mouth[1], mouth[2]).inflate(reach + 1.0),
				e -> !(e instanceof EnderDragon) && !hit.contains(e))) {
			if (burrow.reaches(living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(), SNOUT_IN, depth, 0.9)) hit.add(living);
		}
	}

	// ---------------------------------------------------------------- resting

	private void rest() {
		double wake = DragonConfig.WAKE_RANGE.get();
		LivingEntity near = wake <= 0.0 ? null : ((ServerLevel) dragon.level()).getNearestPlayer(TargetingConditions.forCombat().range(wake), dragon);
		if (near != null) {
			engage(near);
			duration = Math.max(duration, ticks + 300);
			return;
		}
		// nowhere to walk to up there, and nothing to do: off again soon
		if (foothold.narrow() || brain().face().wall()) {
			if (++wanderTicks > (foothold.narrow() ? NARROW_REST : DragonConfig.WALL_REST.get())) takeOff();
			return;
		}
		if (wander != null) {
			// arrived, no way there, or taking too long: stop and look around a while
			if (++wanderTicks < 400 && walker.walkToward(wander[0] + 0.5, wander[2] + 0.5, 1.5, ticks)) return;
			wander = null;
			pauseTicks = Roaming.pause(ThreadLocalRandom.current());
			return;
		}
		if (--pauseTicks > 0) return;
		wander = nextWalk();
		wanderTicks = 0;
		if (wander == null) pauseTicks = 40;
	}

	/** Somewhere to walk to, on from the last walk's heading; null when nothing nearby will do. */
	@Nullable
	private int[] nextWalk() {
		GroundPlanner planner = new GroundPlanner(brain().localGrid());
		RandomGenerator random = ThreadLocalRandom.current();
		for (int attempt = 0; attempt < 6; attempt++) {
			double h = Roaming.nextHeading(walkHeading, attempt, random);
			double leg = Roaming.walkLeg(random);
			Vec3 here = brain().local();
			int x = Mth.floor(here.x + Math.cos(h) * leg), z = Mth.floor(here.z + Math.sin(h) * leg);
			int y = planner.stand(x, z);
			Vec3 there = brain().world(x, y, z);
			if (y == BlockGrid.NO_GROUND || Math.abs(y - here.y) > 6 || !brain().ticking(Mth.floor(there.x), Mth.floor(there.z))) continue;
			walkHeading = h;
			return new int[]{x, y, z};
		}
		return null;
	}

	// ---------------------------------------------------------------- moving on the ground

	/**
	 * Keeps the feet on the terrain under the body: the highest ground under the hips and wrists. A
	 * wall the wrists are against is not ground to climb: only ground within a step of its feet counts
	 * there (under its middle, whatever is there). False when what it stood on is gone: it falls
	 * ({@link LiftoffPhase#fall}).
	 */
	private boolean followGround() {
		// in the frame of the face it stands on: on a wall the wall is the ground
		BlockGrid grid = brain().localGrid();
		Vec3 here = brain().local();
		// on a wall its foothold gone (the blocks under its feet removed): it falls off and flies
		if (!brain().standsOn()) {
			LiftoffPhase.fall(dragon);
			return false;
		}
		Vec3 facing = Targets.facing(dragon.getYRot());
		double fx = facing.x, fz = facing.z;
		int best = grid.ground(Mth.floor(here.x), Mth.floor(here.z));
		// sat up, it stands on its hind feet alone, right under it
		double[][] feet = foothold.narrow() ? new double[0][] : new double[][]{{fx * 3.5, fz * 3.5}, {-fx * 1.5, -fz * 1.5}};
		for (double[] at : feet) {
			int g = grid.ground(Mth.floor(here.x + at[0]), Mth.floor(here.z + at[1]));
			if (g != BlockGrid.NO_GROUND && g - here.y <= GroundPlanner.STEP_UP + 0.5) best = Math.max(best, g);
		}
		if (best == BlockGrid.NO_GROUND || here.y - best > FALL) {
			LiftoffPhase.fall(dragon);       // the ground is gone (removed under it, water, void): it falls and flies
			return false;
		}
		double dy = Mth.clamp(best - here.y, -0.35, 0.25);
		if (Math.abs(dy) > 1e-3) {
			Vec3 to = brain().world(here.x, here.y + dy, here.z);
			dragon.setPos(to.x, to.y, to.z);
		}
		Vec3 now = brain().local();
		Vec3 moved = brain().world(now.x - lastX, 0.0, now.z - lastZ);
		dragon.setDeltaMovement(moved);
		return true;
	}

	/** Feet on the ground: their thud and dust (a hovering landing's, a running landing's strike). */
	public void landingThud() {
		thud(dragon);
	}

	public static void thud(EnderDragon dragon) {
		if (!(dragon.level() instanceof ServerLevel level)) return;
		// all four feet at once, and the wings braking: the walk's own steps follow from its animation
		level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), DragonSounds.STEP, SoundSource.HOSTILE, 1.0F, 0.7F);
		level.playSound(null, dragon.getX(), dragon.getY() + 3.0, dragon.getZ(), DragonSounds.WING, SoundSource.HOSTILE, 1.0F, 0.8F);
		// what its feet strike: under it, or on a wall the face behind it
		Surface.Face face = DragonswornDragon.brain(dragon).face();
		BlockPos below = face.wall() ? BlockPos.containing(dragon.getX() - face.nx * 0.5, dragon.getY(), dragon.getZ() - face.nz * 0.5)
				: BlockPos.containing(dragon.getX(), dragon.getY() - 0.5, dragon.getZ());
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)),
				dragon.getX(), dragon.getY() + 0.2, dragon.getZ(), 80, 3.0, 0.2, 3.0, 0.15);
	}

	// ---------------------------------------------------------------- actions

	/** The bite of its foothold: on all fours, sat up, clinging, or on a wall. */
	private DragonAnim bite() {
		if (brain().face().wall()) return DragonAnim.WALL_BITE;
		return switch (foothold) {
			case STAND, WALL -> DragonAnim.ATTACK;
			case UPRIGHT -> DragonAnim.UPRIGHT_BITE;
			case CLING -> DragonAnim.CLING_BITE;
		};
	}

	/** The roar of where it stands: on a wall the head is thrown back to point straight up. */
	private DragonAnim roarAnim() {
		return brain().face().wall() ? DragonAnim.WALL_ROAR : DragonAnim.ROAR;
	}

	private void startAction(DragonAnim anim) {
		action = anim;
		actionTicks = 0;
		actionHit = false;
		brain().startAction(anim);
		// a blow at it: it is in reach
		if (Strike.strikes(anim) || anim == DragonAnim.WING_BUFFET) unreachedTicks = 0;
		if (Strike.strikes(anim) && target != null) {
			aim = strikePoint(target);
			brain().aimStrike(aim);
		}
		if (anim.roars()) {
			roarReadyAt = ticks + brain().crowd.scale(DragonConfig.between(DragonConfig.ROAR_COOLDOWN_MIN, DragonConfig.ROAR_COOLDOWN_MAX, ThreadLocalRandom.current()));
		}
	}

	/**
	 * Where a blow at {@code target} goes: the middle of its body; hiding in a tunnel too narrow for the head,
	 * as far into the mouth as the snout goes ({@link #SNOUT_IN}), the jaws snapping on down it from there.
	 */
	private Vec3 strikePoint(LivingEntity target) {
		if (burrow != null && target == chased) {
			double[] in = burrow.at(Math.min(SNOUT_IN, burrow.depth()));
			return new Vec3(in[0], in[1], in[2]);
		}
		return target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
	}

	/** Whether {@code anim}'s blow would put the jaws (or the tail's tip) on {@code at} from where the dragon stands. */
	private boolean reaches(DragonAnim anim, Vec3 at) {
		probe.aim(anim, at.x - dragon.getX(), at.y - dragon.getY(), at.z - dragon.getZ());
		return probe.solve(brain().body, 1.0F) < Strike.radius(anim) * 0.5;
	}

	/** When {@code anim}'s blow lands, seconds into the action: when the model shows it (the animation starts after the blend). */
	private static double hitSeconds(DragonAnim anim) {
		return switch (anim) {
			case ATTACK, UPRIGHT_BITE, CLING_BITE, WALL_BITE, TAIL_SWEEP -> Strike.hitSeconds(anim) + DragonAnim.BLEND_TICKS / 20.0;
			case WING_BUFFET -> DragonAnim.BUFFET_SECONDS + DragonAnim.BLEND_TICKS / 20.0;
			case ROAR, WALL_ROAR -> DragonAnim.ROAR_SECONDS;
			default -> Double.MAX_VALUE;
		};
	}

	private void runAction() {
		actionTicks++;
		double seconds = actionTicks / 20.0;
		boolean strike = Strike.strikes(action);
		double hitAt = hitSeconds(action);
		// the aim follows the target until a moment before the blow; then it is committed
		if (strike && target != null && !actionHit && actionTicks < hitAt * 20.0 - DragonConfig.REACTION_TICKS.get()) {
			aim = strikePoint(target);
			brain().aimStrike(aim);
			// a bite turns the body after it while the neck coils
			if (action.bites()) {
				Vec3 t = brain().local(target.position());
				walker.turnToward(t.x, t.z);
			}
		}
		if (!actionHit && seconds >= hitAt) {
			actionHit = true;
			switch (action) {
				case ATTACK, UPRIGHT_BITE, CLING_BITE, WALL_BITE, TAIL_SWEEP -> blow(action);
				case WING_BUFFET -> buffet();
				case ROAR, WALL_ROAR -> roar();
				default -> {}
			}
		}
		if (seconds >= PoseTrack.length(action) + (strike ? DragonAnim.BLEND_TICKS / 20.0 : 0.0)) {
			int recovery = action.bites() ? DragonConfig.BITE_RECOVERY.get() : action == DragonAnim.TAIL_SWEEP ? DragonConfig.TAIL_RECOVERY.get()
					: action == DragonAnim.WING_BUFFET ? DragonConfig.BUFFET_RECOVERY.get() : 0;
			// more players round it, quicker blows
			attackReadyAt = Math.max(attackReadyAt, ticks + brain().crowd.scale(recovery));
			action = null;
			aim = null;
			brain().clearAction();
		}
	}

	/**
	 * The blow lands: the jaws (or the tail's tip) are where the aim put them; whatever is there now is
	 * hit. A target that stepped away, or one the blow could not reach, is missed.
	 */
	private void blow(DragonAnim anim) {
		if (aim == null) return;
		Vec3 point = JawBlow.landing(dragon, probe, anim, aim);
		boolean bite = anim.bites();
		boolean seize = bite && seizing;
		seizing = false;
		if (bite) dragon.playSound(DragonSounds.BITE, 3.0F, 0.6F);
		else dragon.playSound(DragonSounds.TAIL, 4.0F, 0.5F);
		double radius = Strike.hitRadius(anim);
		DamageSource source = dragon.damageSources().mobAttack(dragon);
		List<LivingEntity> struck = new ArrayList<>(JawBlow.struck(dragon, point, radius));
		if (bite) tunnelStruck(struck);
		for (LivingEntity living : struck) {
			// the seize: the jaws close on the target and keep it
			if (seize && living == target && living.hurtServer(((ServerLevel) dragon.level()), source, DragonConfig.SEIZE_DAMAGE.f()) && living.isAlive() && seize(living)) {
				seize = false;
				continue;
			}
			if (!living.hurtServer(((ServerLevel) dragon.level()), source, bite ? DragonConfig.BITE_DAMAGE.f() : DragonConfig.TAIL_DAMAGE.f()) || bite) continue;
			// the tail flings what it hits away from the dragon
			Vec3 push = living.position().subtract(dragon.position()).multiply(1, 0, 1).normalize().scale(1.8);
			living.push(push.x, 0.45, push.z);
			living.hurtMarked = true;
		}
	}

	/**
	 * The wing buffet's blast: everything round the body (within {@code wing_buffet_range}, not far above or
	 * below) is hurt and thrown away from the dragon, the nearest hardest; the dust flies.
	 */
	private void buffet() {
		double range = DragonConfig.BUFFET_RANGE.get();
		dragon.playSound(DragonSounds.BUFFET, 4.0F, 0.6F);
		ServerLevel level = (ServerLevel) dragon.level();
		level.sendParticles(ParticleTypes.CLOUD, dragon.getX(), dragon.getY() + 0.5, dragon.getZ(), 60, range * 0.4, 0.2, range * 0.4, 0.2);
		DamageSource source = dragon.damageSources().mobAttack(dragon);
		Vec3 facing = Targets.facing(dragon.getYRot());
		for (Entity e : level.getEntities(dragon, dragon.getBoundingBox().inflate(range), EntitySelector.NO_CREATIVE_OR_SPECTATOR)) {
			if (!(e instanceof LivingEntity living) || e instanceof EnderDragon || e == brain().prey.prey()) continue;
			Vec3 to = living.position().subtract(dragon.position()).multiply(1, 0, 1);
			double distance = to.length();
			if (distance > range || Math.abs(living.getY() - dragon.getY()) > range * 0.6) continue;
			living.hurtServer(((ServerLevel) dragon.level()), source, DragonConfig.BUFFET_DAMAGE.f());
			Vec3 push = (distance > 1e-3 ? to.scale(1.0 / distance) : facing).scale(DragonConfig.BUFFET_KNOCKBACK.get() * (1.0 - 0.5 * distance / range));
			living.push(push.x, 0.5, push.z);
			living.hurtMarked = true;
		}
	}

	/** Makes its next bite a seize (if the target can be held): it still has to land to take the prey. */
	public void seizeNext() {
		seizeNext = true;
	}

	/** A seizing bite is playing and its aim is committed: from now on only what is at the jaws when they close is taken. */
	public boolean seizeCommitted() {
		return seizing && action != null && action.bites() && !actionHit && actionTicks >= hitSeconds(action) * 20.0 - DragonConfig.REACTION_TICKS.get();
	}

	/** Takes {@code prey} in the jaws (a seizing bite closed on it) and fights it from there (the seize's hold). */
	private boolean seize(LivingEntity prey) {
		if (!brain().prey.seize(prey, Grip.Hold.JAW)) return false;
		target = prey;
		holdTicks = 0;
		holdFor = DragonConfig.between(DragonConfig.HOLD_MIN, DragonConfig.HOLD_MAX, ThreadLocalRandom.current());
		return true;
	}

	/**
	 * Prey in the jaws: shaken (the body's shake runs while the hold is synced) and chewed, then flung
	 * off to one side. When it gets away (or someone hitting the head made the dragon drop it) the fight
	 * goes on, the head free again.
	 */
	private void hold() {
		DragonBrain brain = brain();
		brain.setLookTarget(null);
		if (!brain.prey.holding()) {
			holdTicks = 0;
			attackReadyAt = Math.max(attackReadyAt, ticks + brain().crowd.scale(DragonConfig.BITE_RECOVERY.get()));
			seizeReadyAt = ticks + DragonConfig.SEIZE_COOLDOWN.get();
			return;
		}
		holdTicks++;
		Entity prey = brain.prey.prey();
		if (holdTicks == 3 && prey instanceof ServerPlayer player) player.displayClientMessage(Component.translatable("dragonsworn.seized"), true);
		if (holdTicks % DragonConfig.CHEW_INTERVAL.get() == 0 && prey instanceof LivingEntity living) {
			living.hurt(dragon.damageSources().mobAttack(dragon), DragonConfig.CHEW_DAMAGE.f());
			dragon.playSound(DragonSounds.CHEW, 1.5F, 0.8F);
		}
		if (holdTicks >= holdFor) {
			// flung off to one side, out and up
			float yaw = (dragon.getYRot() + (dragon.getRandom().nextBoolean() ? 70.0F : -70.0F)) * Mth.DEG_TO_RAD;
			// to the side across the face it stands on, and out from it (up, on the ground)
			Vec3 side = brain.world(Mth.sin(yaw) * 1.3, 0.0, -Mth.cos(yaw) * 1.3);
			Surface.Face face = brain.face();
			Vec3 out = face.wall() ? new Vec3(face.nx * 0.55, 0.3, face.nz * 0.55) : new Vec3(0.0, 0.55, 0.0);
			brain.prey.release(side.add(out));
			dragon.playSound(DragonSounds.FLING, 3.0F, 0.5F);
		}
	}

	/**
	 * Terrify: everything within {@code roar_range} is slowed for {@code roar_slowness} (whoever runs away or
	 * shoots from afar), what is within 16 blocks also pushed back. The growl is the client's
	 * (DragonVoiceMixin), in step with the jaw.
	 */
	private void roar() {
		double range = DragonConfig.ROAR_RANGE.get();
		int slow = DragonConfig.ROAR_SLOW_TICKS.get();
		for (Entity e : dragon.level().getEntities(dragon, dragon.getBoundingBox().inflate(range), EntitySelector.NO_CREATIVE_OR_SPECTATOR)) {
			if (!(e instanceof LivingEntity living) || e instanceof EnderDragon) continue;
			Vec3 to = e.position().subtract(dragon.position()).multiply(1, 0, 1);
			double distance = to.length();
			if (distance > range) continue;
			if (slow > 0) living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slow, 1), dragon);
			if (distance > 16.0) continue;
			Vec3 push = to.normalize().scale(1.2 * (1.0 - distance / 16.0));
			living.push(push.x, 0.2, push.z);
			living.hurtMarked = true;
		}
	}

	private void takeOff() {
		dragon.getPhaseManager().setPhase(DragonPhases.LIFTOFF);
	}

	@Override
	public boolean collides() {
		return false;
	}

	@Override
	public float getFlySpeed() {
		return 0.0F;
	}

	@Nullable
	@Override
	public Vec3 getFlyTargetLocation() {
		return null;
	}
}
