package crazylimits.dragonsworn.mc.phase;

import crazylimits.dragonsworn.ai.Foothold;
import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.flight.FlightModel;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonPhases;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.nav.BlockGrid;
import crazylimits.dragonsworn.nav.LandingSite;
import crazylimits.dragonsworn.nav.Runway;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Coming down on a landing site ({@link LandingSite}), one of two ways:
 * <ul>
 *   <li><b>Running</b> (arriving fast, {@link Runway#MIN_SPEED} or more, with a {@link Runway} that fits):
 *       an eagle's landing. It lines up behind the site (the runway's lead point), glides down the
 *       approach, and once it is {@link Runway#startDistance} short of the touch point, heading for it, the
 *       runway is planned again from exactly where it is (so the path it flies is the one checked) and the
 *       landing plays
 *       ({@link DragonAnim#LAND}): the legs swing forward, it flares with one braking stroke and its feet
 *       strike the ground ahead of it; it skids out to a stop on the site and folds its wings. From the
 *       start of the landing it follows the runway's path exactly (the animation is timed to it).</li>
 *   <li><b>Hovering</b>: fly to a point {@link #ABOVE} blocks over the site, check it still fits, then
 *       hover straight down onto it.</li>
 * </ul>
 * A narrow foothold ({@link Foothold#UPRIGHT}, {@link Foothold#CLING}: a ledge, a pillar's top) is always
 * hovered down onto: there is no runway there.
 * Landed, it fights (or rests), or for a perch ({@link #perch}: the End fight's, by its players) it
 * perches there as vanilla's dragon did on the exit portal: scans, roars, breathes and takes off.
 * If the site no longer fits (somebody built on it) or the dragon cannot get there, it gives up and stays
 * in the air; a running approach that cannot line up falls back to hovering down.
 */
public class GroundApproachPhase extends AbstractDragonPhaseInstance implements DragonswornPhase {
	static final double ABOVE = 12.0;
	private static final int GIVE_UP = 900;
	/** A running approach gets this long to start its landing before it hovers down instead. */
	private static final int RUN_GIVE_UP = 300;
	/**
	 * Heading for the touch point: the cosine between its motion and the way there. Loose: the landing's
	 * path starts from its motion and curves onto the runway, a banked turn into the landing.
	 */
	private static final double TOWARD = 0.3;
	/** The landing starts from this high above the touch point at most (blocks): higher, it is too steep. */
	private static final double MAX_START_HEIGHT = 22.0;
	/** The glide path: its slope (rise over run) down to the touch point, and the sink and climb rates it may ask for. */
	private static final double GLIDE_SLOPE = Runway.ENTRY_HEIGHT / Runway.GLIDE_IN, MAX_SINK = 0.6, MAX_RISE = 0.25;
	/** Closer than its start distance by this much without having started: it overshot (blocks). */
	private static final double OVERSHOOT = 8.0;

	@Nullable
	private int[] site;
	@Nullable
	private LivingEntity target;
	private boolean descending, decided, onLine, perch;
	/** How it will stand on the site. */
	private Foothold foothold = Foothold.STAND;
	private int ticks;
	@Nullable
	private Runway runway;
	/** Ticks into the running landing (-1: not landing yet), and where and how fast it started. */
	private int landTick = -1;
	private double[] landFrom, landVelocity;
	/** Where it was at the start of the last tick: its real motion (its delta has been damped since). */
	@Nullable
	private Vec3 lastPos;
	private Vec3 motion = Vec3.ZERO;
	/** Why the last running approach hovered down instead (for the in-game test), or null. */
	@Nullable
	private String fallback;

	public GroundApproachPhase(EnderDragon dragon) {
		super(dragon);
	}

	/** Lands on {@code site} ({x, y, z}), then fights {@code target} (or rests, when null). */
	public static void start(EnderDragon dragon, int[] site, @Nullable LivingEntity target) {
		start(dragon, site, target, Foothold.STAND);
	}

	/** As above, standing on the site with {@code foothold} (it fits there: {@link LandingSite#fits(int, int, Foothold)}). */
	public static void start(EnderDragon dragon, int[] site, @Nullable LivingEntity target, Foothold foothold) {
		dragon.getPhaseManager().setPhase(DragonPhases.GROUND_APPROACH);
		GroundApproachPhase phase = dragon.getPhaseManager().getPhase(DragonPhases.GROUND_APPROACH);
		phase.site = site;
		phase.target = target;
		phase.foothold = foothold;
	}

	/** Lands on {@code site} ({x, y, z}) and perches there (vanilla's perch, anywhere). */
	public static void perch(EnderDragon dragon, int[] site) {
		start(dragon, site, null);
		dragon.getPhaseManager().getPhase(DragonPhases.GROUND_APPROACH).perch = true;
	}

	@Override
	public EnderDragonPhase<GroundApproachPhase> getPhase() {
		return DragonPhases.GROUND_APPROACH;
	}

	@Override
	public void begin() {
		site = null;
		target = null;
		descending = decided = onLine = perch = false;
		foothold = Foothold.STAND;
		ticks = 0;
		runway = null;
		landTick = -1;
		lastPos = null;
		motion = Vec3.ZERO;
		fallback = null;
	}

	@Override
	public void end() {
		if (!dragon.level().isClientSide && brain().action() == DragonAnim.LAND) brain().clearAction();
	}

	private DragonBrain brain() {
		return DragonswornDragon.brain(dragon);
	}

	private Vec3 above() {
		return new Vec3(site[0] + 0.5, site[1] + ABOVE, site[2] + 0.5);
	}

	/** Why the last running approach fell back to hovering down, or null. */
	@Nullable
	public String fallback() {
		return fallback;
	}

	/** How it will stand on the site. */
	public Foothold foothold() {
		return foothold;
	}

	/** Coming in to land running (a runway was found), not hovering down. */
	public boolean runningIn() {
		return runway != null;
	}

	/** Landing running, between the start of the landing animation and its end. */
	public boolean landing() {
		return landTick >= 0;
	}

	@Override
	public void doServerTick() {
		if (landing()) {
			landTick();
			return;
		}
		motion = lastPos == null ? dragon.getDeltaMovement() : dragon.position().subtract(lastPos);
		lastPos = dragon.position();
		if (site == null || ++ticks > GIVE_UP || brain().stuckTicks() > 80) {
			giveUp();
			return;
		}
		if (!decided) {
			decided = true;
			// its motion: within its own tick it has not moved yet (x - xo is 0 here)
			if (motion.horizontalDistance() >= Runway.MIN_SPEED && foothold == Foothold.STAND) {
				runway = Runway.plan(brain().grid(), site, dragon.getX(), dragon.getZ());
			}
		}
		if (runway != null) {
			runIn();
			return;
		}
		double horizontal = Math.hypot(site[0] + 0.5 - dragon.getX(), site[2] + 0.5 - dragon.getZ());
		if (!descending && horizontal < 5.0 && Math.abs(dragon.getY() - (site[1] + ABOVE)) < 6.0) {
			if (new LandingSite(brain().grid()).fits(site[0], site[2], foothold) == BlockGrid.NO_GROUND) {
				giveUp();
				return;
			}
			descending = true;
		}
		if (descending && dragon.getY() - site[1] < 1.0 && horizontal < 2.0) {
			dragon.setPos(site[0] + 0.5, site[1], site[2] + 0.5);
			dragon.setDeltaMovement(Vec3.ZERO);
			GroundFightPhase.thud(dragon);
			touchDown();
		}
	}

	/** Lining up and gliding in; the landing starts at the distance its speed needs. */
	private void runIn() {
		if (ticks > RUN_GIVE_UP) {
			hoverInstead("no landing started in " + RUN_GIVE_UP + " ticks");
			return;
		}
		Vec3 v = motion;
		double speed = v.horizontalDistance();
		double tx = runway.touch[0] - dragon.getX(), tz = runway.touch[2] - dragon.getZ(), distance = Math.hypot(tx, tz);
		double toward = speed > 1e-3 && distance > 1e-3 ? (v.x * tx + v.z * tz) / (speed * distance) : 0.0;
		double start = Runway.startDistance(speed);
		onLine = toward > TOWARD && distance < Runway.GLIDE_IN + 10.0;
		glidePath(distance, toward);
		if (!onLine || distance > start + 1.0) {
			// too close without having started (it came in wide or too fast): hover down instead
			if (distance < start - OVERSHOOT && alongLine() > -start) {
				hoverInstead(String.format(java.util.Locale.ROOT, "overshot: %.1f from the touch point, heading %.2f", distance, toward));
			}
			return;
		}
		double height = dragon.getY() - runway.touch[1];
		if (height < 1.0 || height > MAX_START_HEIGHT) {
			if (distance < start - OVERSHOOT) hoverInstead(String.format(java.util.Locale.ROOT, "%.1f blocks up at the start", height));
			return;
		}
		// the runway from exactly here: the path it is about to fly is the one checked
		Runway here = Runway.plan(brain().grid(), site, dragon.getX(), dragon.getZ());
		if (here == null || new LandingSite(brain().grid()).fits(site[0], site[2]) == BlockGrid.NO_GROUND) {
			hoverInstead("no runway from where it started the landing");
			return;
		}
		runway = here;
		landTick = 0;
		landFrom = new double[]{dragon.getX(), dragon.getY(), dragon.getZ()};
		double scale = speed > Runway.MAX_SPEED ? Runway.MAX_SPEED / speed : 1.0;
		landVelocity = new double[]{v.x * scale, Math.min(0.0, v.y), v.z * scale};
		brain().startAction(DragonAnim.LAND);
		landTick();
	}

	/**
	 * Holds the dragon on a straight glide path down to the touch point once it heads there: the steering's
	 * own climb and dive are gentle (vanilla's), far too slow to lose the height of a cruise in the length
	 * of an approach. Height above the path is traded for sink (and so for speed, as gliding does).
	 */
	private void glidePath(double distance, double toward) {
		if (toward < 0.5) return;
		double want = runway.touch[1] + GLIDE_SLOPE * distance;
		double sink = Mth.clamp((want - dragon.getY()) * 0.06, -MAX_SINK, MAX_RISE);
		Vec3 v = dragon.getDeltaMovement();
		dragon.setDeltaMovement(v.x, v.y + (sink - v.y) * 0.25, v.z);
	}

	private void hoverInstead(String why) {
		fallback = why;
		runway = null;
	}

	/** Signed distance of the dragon from the touch point along the approach (negative: still short of it). */
	private double alongLine() {
		return (dragon.getX() - runway.touch[0]) * runway.dirX + (dragon.getZ() - runway.touch[2]) * runway.dirZ;
	}

	/** One tick of the running landing: on the runway's path, then standing. */
	private void landTick() {
		int t = landTick++;
		double ground = groundY();
		double[] p = runway.path(landFrom, landVelocity, t, ground);
		dragon.setPos(p[0], p[1], p[2]);
		dragon.setDeltaMovement(p[3], p[4], p[5]);
		// facing along its path (a curve onto the runway banks like a turn), down the runway once on the ground
		float yaw = Math.hypot(p[3], p[5]) > 0.05 && !Runway.grounded(t)
				? (float) Math.toDegrees(Math.atan2(-p[3], p[5])) : runway.yaw();
		dragon.setYRot(Mth.rotLerp(0.5F, dragon.getYRot(), yaw));
		dragon.yRotA = 0.0F;
		if (t == Runway.TOUCH_TICKS) GroundFightPhase.thud(dragon);
		if (t >= Runway.TOUCH_TICKS + Runway.SKID_TICKS) dragon.setDeltaMovement(Vec3.ZERO);
		if (t >= Math.round(DragonAnim.LAND_SECONDS * 20) + DragonAnim.BLEND_TICKS) {
			brain().clearAction();
			touchDown();
		}
	}

	/** On its feet: perches, or fights (rests). The landing's thud has sounded already. */
	private void touchDown() {
		if (perch) dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_SCANNING);
		else GroundFightPhase.start(dragon, target, false, foothold);
	}

	/** The ground under the dragon on the strip (it was checked flat to within a block of the site). */
	private double groundY() {
		int g = brain().grid().ground(Mth.floor(dragon.getX()), Mth.floor(dragon.getZ()));
		return g == BlockGrid.NO_GROUND || Math.abs(g - site[1]) > 1 ? site[1] : g;
	}

	private void giveUp() {
		dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
	}

	/** The ground under the landing dragon's feet (after the touch). */
	public boolean grounded() {
		return landing() && Runway.grounded(landTick);
	}

	@Override
	public FlightModel.Force flightForce() {
		if (site == null) return FlightModel.Force.NONE;
		// gliding the last stretch in, wings still; beating to line up
		if (runway != null) return onLine ? FlightModel.Force.GLIDE : FlightModel.Force.NONE;
		if (descending) return FlightModel.Force.HOVER;
		// flare out of the approach a little before the point above the site
		return above().distanceToSqr(dragon.position()) < 18 * 18 ? FlightModel.Force.HOVER : FlightModel.Force.NONE;
	}

	/** The site was checked to fit; the last blocks down the hanging tail may brush the ground. */
	@Override
	public boolean collides() {
		if (runway != null) return !onLine;
		return !(descending && site != null && dragon.getY() - site[1] < 4.0);
	}

	@Override
	public float getFlySpeed() {
		return 1.0F;
	}

	/** The running landing moves the dragon itself (null: no steering); otherwise where to fly. */
	@Nullable
	@Override
	public Vec3 getFlyTargetLocation() {
		if (site == null || landing()) return null;
		if (runway != null) {
			// to the lead point until past it, then down the approach line toward the touch point
			if (alongLine() < -Runway.GLIDE_IN - Runway.LEAD * 0.5) return new Vec3(runway.lead[0], runway.lead[1], runway.lead[2]);
			// a point on the line ahead, sloping down to the touch point
			double along = Math.min(alongLine() + 12.0, 0.0);
			double k = Math.max(0.0, -along) / Runway.GLIDE_IN;
			return new Vec3(runway.touch[0] + runway.dirX * along, runway.touch[1] + Runway.ENTRY_HEIGHT * k,
					runway.touch[2] + runway.dirZ * along);
		}
		if (!descending) return above();
		// the hover sinks toward the site, fast high up and gently over the last blocks (its height
		// control eases off as the error shrinks)
		return new Vec3(site[0] + 0.5, site[1], site[2] + 0.5);
	}
}
