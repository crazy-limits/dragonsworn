package crazylimits.dragonfall.mc.client;

import crazylimits.dragonfall.anim.DragonAnim;
import crazylimits.dragonfall.anim.DragonDebug;
import crazylimits.dragonfall.body.PoseTrack;
import crazylimits.dragonfall.body.Tail;
import crazylimits.dragonfall.mc.DragonBrain;
import crazylimits.dragonfall.mc.DragonPhases;
import crazylimits.dragonfall.mc.DragonfallDragon;
import crazylimits.dragonfall.mc.LevelGrid;
import crazylimits.dragonfall.mc.breath.BreathParticles;
import crazylimits.dragonfall.mc.breath.BreathStreamPhase;
import crazylimits.dragonfall.mc.phase.GroundApproachPhase;
import crazylimits.dragonfall.mc.phase.GroundFightPhase;
import crazylimits.dragonfall.mc.phase.SnatchPhase;
import crazylimits.dragonfall.body.Grip;
import crazylimits.dragonfall.anim.BreathAttack;
import crazylimits.dragonfall.nav.BlockGrid;
import crazylimits.dragonfall.nav.LandingSite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The in-game test, on any loader. Off unless the JVM runs with {@code -Ddragonfall.showcase=true}
 * ({@code ./gradlew :<target>:runClient -Pdragonfall.showcase}).
 *
 * <p>From the title screen it creates a fresh flat creative world, lays a stone yard, summons a frozen
 * (NoAI) vanilla Ender Dragon, checks that Dragonfall's renderer is the one drawing it, then loops
 * every animation and photographs it from the front three-quarter, the side and above. Then it tests
 * the AI live (see {@link #liveDragon}) and the stream breath. Screenshots go to
 * {@code <run>/screenshots/df-*.png}, a pass/fail report to {@code <run>/showcase-report.txt}, and the
 * game quits when done.
 */
public final class Showcase {
	public static final boolean ENABLED = Boolean.getBoolean("dragonfall.showcase");
	private static final Logger LOG = LoggerFactory.getLogger("dragonfall/showcase");
	private static final String WORLD = "dragonfall_showcase";
	/** Runs only this stage when set ({@code -Pdragonfall.showcase=grabs}, {@code =landing}). */
	private static final String ONLY = System.getProperty("dragonfall.showcase.only", "");

	private record Step(int ticks, Consumer<Minecraft> action) {}

	private static final List<Step> STEPS = new ArrayList<>();
	private static final List<String> REPORT = new ArrayList<>();
	private static int stage, delay, index;
	private static boolean failed;

	private Showcase() {}

	public static void tick(Minecraft mc) {
		if (!ENABLED) return;
		// The test window is usually not focused; a paused game would freeze every animation.
		mc.options.pauseOnLostFocus = false;
		if (stage == 2 && mc.screen instanceof PauseScreen) mc.setScreen(null);
		// Creative flight keeps the camera where each shot puts it; otherwise it falls between shots.
		if (stage == 2 && mc.player != null) mc.player.getAbilities().flying = true;
		try {
			switch (stage) {
				case 0 -> {
					if (mc.screen instanceof TitleScreen) {
						createWorld(mc);
						stage = 1;
					}
				}
				case 1 -> {
					if (mc.player != null && mc.level != null && mc.screen == null) {
						buildScript(mc.player.blockPosition());
						delay = 60;
						stage = 2;
					}
				}
				case 2 -> {
					if (--delay > 0) return;
					if (index >= STEPS.size()) {
						finish(mc);
						stage = 3;
						return;
					}
					Step step = STEPS.get(index++);
					step.action.accept(mc);
					delay = Math.max(1, step.ticks);
				}
				default -> {}
			}
		} catch (Throwable t) {
			LOG.error("Showcase failed", t);
			REPORT.add("FAIL exception: " + t);
			failed = true;
			finish(mc);
			stage = 3;
		}
	}

	private static void createWorld(Minecraft mc) throws IOException {
		Path saves = mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);
		if (Files.exists(saves)) {
			try (Stream<Path> walk = Files.walk(saves)) {
				walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
			}
		}
		GameRules rules = new GameRules();
		rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
		rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
		rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
		LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.NORMAL, true, rules,
				WorldDataConfiguration.DEFAULT);
		mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(1L, false, false),
				registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
						.value().createWorldDimensions(),
				new TitleScreen());
	}

	private static void buildScript(BlockPos at) {
		int x = at.getX(), y = at.getY(), z = at.getZ() - 40;
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:smooth_stone", x - 24, y - 1, z - 24, x + 24, y - 1, z + 24), 2);
		command("time set 6000", 1);
		if (ONLY.equals("grabs")) {
			grabs(x, y, z);
			return;
		}
		if (ONLY.equals("walls")) {
			STEPS.add(new Step(1, mc -> mc.options.hideGui = true));
			walls(x, y, z + 150);
			return;
		}
		if (ONLY.equals("breath")) {
			STEPS.add(new Step(1, mc -> mc.options.hideGui = true));
			breath(x, y, z);
			breathMoving(x, y, z + 60);
			return;
		}
		// the AI's ground assault and takeoff, and the running landing
		if (ONLY.equals("landing")) {
			STEPS.add(new Step(1, mc -> mc.options.hideGui = true));
			liveDragon(x, y, z + 150);
			runningLanding(x, y, z + 600);
			return;
		}
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {NoAI:1b}", x, y, z), 40);
		STEPS.add(new Step(1, mc -> {
			mc.options.hideGui = true;
			int dragons = 0;
			boolean ours = true;
			for (var entity : mc.level.entitiesForRendering()) {
				if (entity instanceof EnderDragon dragon) {
					dragons++;
					ours &= mc.getEntityRenderDispatcher().getRenderer(dragon) instanceof DragonRenderer;
				}
			}
			check(dragons == 1, "one dragon in the client world (found " + dragons + ")");
			check(ours, "the Ender Dragon is drawn by Dragonfall's renderer");
		}));

		// A NoAI dragon keeps yaw 0, so its head points north (-z).
		String front = view(x - 13, y + 5, z - 15, x, y + 3, z);
		String side = view(x - 24, y + 3, z + 2, x, y + 3, z + 2);
		String top = view(x - 12, y + 18, z + 12, x, y + 2, z);

		// Flight is shot on a second dragon summoned in the air, away from the first: a NoAI dragon ignores
		// teleports on the client (vanilla only interpolates its position when it has AI).
		int ax = x + 120, air = y + 16;
		String flight = view(ax - 22, air - 1, z + 6, ax, air + 3, z + 2);
		String flightFront = view(ax - 12, air + 2, z - 24, ax, air + 3, z);
		boolean[] airborne = {false};
		for (DragonAnim anim : DragonAnim.values()) {
			String name = anim.name().toLowerCase(Locale.ROOT);
			STEPS.add(new Step(10, mc -> DragonDebug.forcedAnimation = anim));
			switch (anim) {
				// The walk: four moments a quarter cycle apart (2.4 s cycle = 48 ticks).
				case WALK -> {
					for (int i = 0; i < 4; i++) shoot(front, name + "-front-" + i, 12);
				}
				// A wingbeat: four moments a quarter beat apart (1.6 s = 32 ticks), from the side.
				case FLY, FLAP, GLIDE, HOVER -> {
					if (!airborne[0]) {
						airborne[0] = true;
						command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {NoAI:1b}", ax, air, z), 30);
					}
					for (int i = 0; i < 4; i++) shoot(flight, name + "-side-" + i, 8);
					shoot(flightFront, name + "-front", 6);
				}
				// the takeoff and the landing: their moments from the side
				case TAKEOFF, LAND -> {
					for (int i = 0; i < 6; i++) shoot(side, name + "-side-" + i, anim == DragonAnim.LAND ? 11 : 4);
				}
				default -> {
					for (int i = 0; i < 2; i++) shoot(front, name + "-front-" + i, 15);
				}
			}
			boolean flying = anim == DragonAnim.FLY || anim == DragonAnim.FLAP || anim == DragonAnim.GLIDE || anim == DragonAnim.HOVER;
			if (!flying && anim != DragonAnim.TAKEOFF && anim != DragonAnim.LAND) shoot(side, name + "-side", 6);
			if (anim == DragonAnim.WALK || anim == DragonAnim.IDLE) shoot(top, name + "-top", 6);
		}
		tailCage(x - 60, y, z);
		STEPS.add(new Step(1, mc -> DragonDebug.forcedAnimation = null));
		liveDragon(x, y, z + 150);
		roaming(x, y, z + 300);
		hills(x, y, z + 450);
		runningLanding(x, y, z + 600);
		walls(x + 300, y, z + 150);
		breath(x, y, z);
		breathMoving(x, y, z + 60);
		grabs(x, y, z - 150);
	}

	/**
	 * Turning on the spot, a planted foot (not in a step) must not slide over the ground: the most any
	 * one moved in a tick while the body turned, per limb.
	 */
	private static void turnSlide(List<LimbContact.Sample> samples) {
		double[] worst = new double[4];
		double turned = 0.0;
		int steps = 0, ticks = 0;
		for (int k = 1; k < samples.size(); k++) {
			LimbContact.Sample a = samples.get(k - 1), b = samples.get(k);
			if (b.tick() != a.tick() + 1 || !b.anim().equals("idle") || !a.anim().equals("idle")) continue;
			double dyaw = Math.abs(Mth.wrapDegrees(b.yaw() - a.yaw()));
			if (dyaw < 0.2) continue;
			turned += dyaw;
			ticks++;
			for (int i = 0; i < 4; i++) {
				if (a.feet()[i] == null || b.feet()[i] == null) continue;
				if (b.stepping()[i] && !a.stepping()[i]) steps++;
				if (a.stepping()[i] || b.stepping()[i]) continue;
				worst[i] = Math.max(worst[i], Math.hypot(b.feet()[i][0] - a.feet()[i][0], b.feet()[i][2] - a.feet()[i][2]));
			}
		}
		REPORT.add(String.format(Locale.ROOT, "INFO turn on the spot: %.0f degrees over %d ticks, %d steps; planted feet slid at most %.3f %.3f %.3f %.3f blocks a tick (%s)",
				turned, ticks, steps, worst[0], worst[1], worst[2], worst[3], String.join(", ", LimbContact.LIMBS)));
		check(turned > 60 && steps >= 4, "it turned round on the spot stepping (" + steps + " steps)");
		double most = Math.max(Math.max(worst[0], worst[1]), Math.max(worst[2], worst[3]));
		check(most < 0.03, "planted feet and wrists stay where they stand while the body turns over them");
	}

	/** Parts whose boxes are the solid body: head, necks, chest, hips. */
	private static final int[] SOLID = {0, 1, 8, 2, 9};

	/** How many stone blocks the dragon's body is in (its boxes shrunk a little: brushing is not being in). */
	private static int inStone(ServerLevel level, EnderDragon dragon) {
		int n = 0;
		for (int part : SOLID) {
			net.minecraft.world.phys.AABB box = dragon.getSubEntities()[part].getBoundingBox().deflate(0.3);
			n += count(level, Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
					Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ), Blocks.STONE);
		}
		return n;
	}

	/**
	 * Stone stops it. In the air: a wild dragon sent at a husk behind a wide, tall stone wall must fly
	 * round or over it, never through. On the ground: a dragon standing with its back to a stone wall,
	 * the husk behind the wall, must turn round on the spot (stepping round: see the turn frames), then
	 * walk round the wall's end without climbing it or walking into it.
	 */
	private static void walls(int wx, int y, int wz) {
		// the flight: a wall across the way, 61 wide and 46 high
		command(view(wx - 60, y + 30, wz - 20, wx, y + 20, wz), 40);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", wx - 30, y, wz, wx + 30, y + 45, wz + 2), 2);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}", wx, y, wz + 30), 2);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", wx, y + 22, wz - 60), 50);
		int[] flight = {0, 0};     // ticks with the body in stone, worst overlap
		boolean[] beyond = {false};
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
			check(dragon != null && !prey.isEmpty(), "the wall-flight dragon and its husk are there");
			if (dragon == null || prey.isEmpty()) return;
			dragon.getPhaseManager().setPhase(DragonPhases.ROAM);
			dragon.getPhaseManager().getPhase(DragonPhases.ROAM).startPass(prey.get(0));
		});
		for (int i = 0; i < 300; i++) {
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				if (dragon == null) return;
				int n = inStone(level, dragon);
				if (n > 0) flight[0]++;
				flight[1] = Math.max(flight[1], n);
				if (dragon.getZ() > wz + 8) beyond[0] = true;
			});
			if (i % 30 == 0) track(String.format(Locale.ROOT, "walls-flight-%02d", i / 30), 3, 40, 12);
		}
		server(level -> {
			REPORT.add(String.format(Locale.ROOT, "INFO wall flight: %d ticks in stone, at worst %d blocks", flight[0], flight[1]));
			check(flight[0] == 0, "flying at a target behind a stone wall, the body never goes into the stone");
			check(beyond[0], "it found its way round or over the wall to the far side");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);

		// on the ground: back to a 25-wide wall, the husk behind it
		int gx = wx + 120;
		command(view(gx - 30, y + 20, wz - 20, gx, y + 2, wz - 10), 40);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", gx - 12, y, wz, gx + 12, y + 9, wz + 2), 2);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}", gx, y, wz + 12), 2);
		// yaw 0 faces north (-z): its back to the wall
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"],Rotation:[0f,0f]}", gx, y, wz - 9), 10);
		int[] ground = {0, 0};
		double[] highest = {Double.NEGATIVE_INFINITY};
		double[] closest = {Double.MAX_VALUE};
		boolean[] round = {false};
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
			if (dragon == null || prey.isEmpty()) return;
			dragon.setYRot(0.0F);
			GroundFightPhase.start(dragon, prey.get(0), false);
		});
		STEPS.add(new Step(1, mc -> LimbContact.start()));
		// the turn on the spot: a frame every few ticks, from the side and from above
		for (int i = 0; i < 12; i++) {
			shoot(view(gx - 14, y + 6, wz - 9, gx, y + 2, wz - 9), String.format(Locale.ROOT, "walls-turn-side-%02d", i), 3);
			shoot(view(gx - 4, y + 18, wz - 6, gx, y, wz - 9), String.format(Locale.ROOT, "walls-turn-top-%02d", i), 3);
		}
		STEPS.add(new Step(1, mc -> turnSlide(LimbContact.stop())));
		for (int i = 0; i < 700; i++) {
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				if (dragon == null) return;
				int n = inStone(level, dragon);
				if (n > 0) ground[0]++;
				ground[1] = Math.max(ground[1], n);
				// on its feet (once the fight is over it takes off: that is no climb)
				if (dragon.getPhaseManager().getCurrentPhase().getPhase() == DragonPhases.GROUND_FIGHT) {
					highest[0] = Math.max(highest[0], dragon.getY() - y);
					if (dragon.getZ() > wz + 3) round[0] = true;
				}
				List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
				if (!prey.isEmpty()) closest[0] = Math.min(closest[0], dragon.distanceTo(prey.get(0)));
			});
			if (i % 50 == 0) track(String.format(Locale.ROOT, "walls-walk-%02d", i / 50), 3, 30, 14);
		}
		server(level -> {
			REPORT.add(String.format(Locale.ROOT, "INFO wall walk: %d ticks in stone (worst %d), highest %.1f above the ground, closest %.1f to the husk",
					ground[0], ground[1], highest[0], closest[0]));
			check(ground[0] == 0, "walking to a target behind a stone wall, the body never goes into the stone");
			check(highest[0] < 1.5, "on its feet it does not climb the wall");
			check(round[0] && closest[0] < 12.0, "it walked round the wall to the husk's side");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
	}

	/**
	 * The stream breath at a moving target: a husk walks across in front of the dragon (17 blocks out,
	 * 0.15 blocks a tick, slower than the aim's {@code BreathAttack.AIM_SPEED}) through the inhale and the
	 * stream. The neck runs out straight with the head low, and the head must point at the husk:
	 * every tick of the stream the head's line (neck to head) is measured against the line to the
	 * husk, across and up/down.
	 */
	private static void breathMoving(int x, int y, int z) {
		int bx = x - 120, bz = z;
		double out = 17.0, from = -10.0, speed = 0.15;
		command(view(bx - 20, y + 6, bz - 4, bx, y + 3, bz - 8), 40);
		command(String.format(Locale.ROOT, "summon minecraft:husk %.1f %d %.1f {NoAI:1b,Invulnerable:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"df_walker\"]}",
				bx + from, y, bz - out), 2);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_breath\"],Rotation:[0f,0f]}", bx, y, bz), 20);
		server(level -> {
			List<? extends Husk> walker = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_walker"));
			for (EnderDragon d : level.getEntities(EntityType.ENDER_DRAGON, e -> e.getTags().contains("df_breath"))) {
				d.getPhaseManager().setPhase(BreathStreamPhase.PHASE);
				if (!walker.isEmpty()) d.getPhaseManager().getPhase(BreathStreamPhase.PHASE).setTarget(walker.get(0));
			}
		});
		String side = view(bx - 20, y + 6, bz - 4, bx, y + 3, bz - 8);
		String front = view(bx + 14, y + 8, bz - 26, bx, y + 3, bz - 6);
		double[] worstYaw = new double[1], worstPitch = new double[1];
		int[] measured = new int[1];
		float[] bodyYaw = {Float.NaN, Float.NaN};
		int total = BreathAttack.WINDUP_TICKS + BreathAttack.STREAM_TICKS;
		// one step a tick: the husk is where the breath's own clock puts it, the head measured as drawn
		for (int i = 0; i < total + 10; i++) {
			int step = i;
			STEPS.add(new Step(1, mc -> {
				mc.getSingleplayerServer().executeBlocking(() -> {
					ServerLevel level = mc.getSingleplayerServer().overworld();
					for (EnderDragon d : level.getEntities(EntityType.ENDER_DRAGON, e -> e.getTags().contains("df_breath"))) {
						if (!(d.getPhaseManager().getCurrentPhase() instanceof BreathStreamPhase breath)) continue;
						for (Husk walker : level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_walker"))) {
							walker.teleportTo(bx + from + speed * breath.ticks(), y, bz - out);
						}
					}
				});
				EnderDragon dragon = nearestDragon(mc, bx + 0.5, bz + 0.5);
				var walker = mc.level.getEntitiesOfClass(Husk.class, new AABB(bx - 30, y - 2, bz - 30, bx + 30, y + 4, bz + 10));
				if (step % 6 == 0) {
					String name = String.format(Locale.ROOT, "df-breath-moving-%03d.png", step);
					Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> LOG.info("{}", message.getString()));
				}
				if (step == total / 2) mc.player.connection.sendCommand(front);
				if (step == total / 2 + 12) mc.player.connection.sendCommand(side);
				if (dragon == null || walker.isEmpty() || !(dragon.getPhaseManager().getCurrentPhase() instanceof BreathStreamPhase breath)) return;
				// the stream, once the neck has swung out straight (the pose's pour and the model's blend)
				if (breath.ticks() < BreathAttack.WINDUP_TICKS + 12 || breath.ticks() >= BreathAttack.WINDUP_TICKS + BreathAttack.STREAM_TICKS - 10) return;
				var head = dragon.getSubEntities()[0].getBoundingBox().getCenter();
				// the neck's line, base (part 8) to head: the stream's neck is straight along it
				var neck = dragon.getSubEntities()[8].getBoundingBox().getCenter();
				var at = walker.get(0).position().add(0.0, walker.get(0).getBbHeight() * 0.3, 0.0);
				double headYaw = Math.atan2(head.x - neck.x, head.z - neck.z), toYaw = Math.atan2(at.x - head.x, at.z - head.z);
				double headPitch = Math.atan2(head.y - neck.y, Math.hypot(head.x - neck.x, head.z - neck.z));
				double toPitch = Math.atan2(at.y - head.y, Math.hypot(at.x - head.x, at.z - head.z));
				worstYaw[0] = Math.max(worstYaw[0], Math.abs(Math.toDegrees(Math.atan2(Math.sin(headYaw - toYaw), Math.cos(headYaw - toYaw)))));
				worstPitch[0] = Math.max(worstPitch[0], Math.abs(Math.toDegrees(headPitch - toPitch)));
				measured[0]++;
				if (Float.isNaN(bodyYaw[0])) bodyYaw[0] = dragon.getYRot();
				bodyYaw[1] = dragon.getYRot();
			}));
		}
		STEPS.add(new Step(1, mc -> {
			REPORT.add(String.format(Locale.ROOT, "INFO moving target: over %d ticks of the stream the head's line is off the husk by up to %.1f deg across, %.1f deg up/down",
					measured[0], worstYaw[0], worstPitch[0]));
			check(measured[0] > 30, "the stream was measured (" + measured[0] + " ticks)");
			check(Math.abs(Mth.wrapDegrees(bodyYaw[1] - bodyYaw[0])) < 1.0F, String.format(Locale.ROOT,
					"the body stays put while the neck follows (it turned %.1f deg)", Mth.wrapDegrees(bodyYaw[1] - bodyYaw[0])));
			check(worstYaw[0] < 12.0, "the head follows the walking husk across");
			check(worstPitch[0] < 15.0, "and points down at it");
		}));
		command("kill @e[tag=df_breath]", 2);
		command("kill @e[tag=df_walker]", 2);
	}

	/**
	 * The two holds, live. The snatch: a wild dragon is sent at a husk on open ground; it must dive, take
	 * it in its talons, carry it up and drop it from high up. The seize: a dragon on the ground takes a
	 * husk in its jaws and shakes and chews it; someone else hitting its head makes it drop it, and a
	 * hold left alone ends with the husk flung away.
	 */
	private static void grabs(int gx, int y, int gz) {
		STEPS.add(new Step(1, mc -> mc.options.hideGui = true));
		command(view(gx - 40, y + 20, gz, gx, y + 8, gz), 40);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}", gx, y, gz), 2);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", gx, y + 24, gz + 70), 60);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null && !prey(level).isEmpty() && SnatchPhase.start(dragon, prey(level).get(0)), "the dragon goes for a snatch at the husk");
		});
		boolean[] reached = new boolean[1], held = new boolean[1];
		double[] peak = {Double.NEGATIVE_INFINITY};
		for (int i = 0; i < 70; i++) {
			if (i >= 12 && i % 3 == 0) closeUp(0, String.format(Locale.ROOT, "snatch-close-%02d", i), 1, 9.0);
			track(String.format(Locale.ROOT, "snatch-%02d", i), 5, 18, 2);
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				if (dragon == null || prey(level).isEmpty()) return;
				Grip.Hold hold = DragonfallDragon.brain(dragon).prey.hold();
				reached[0] |= hold == Grip.Hold.REACH;
				held[0] |= hold == Grip.Hold.TALON;
				if (hold == Grip.Hold.TALON) peak[0] = Math.max(peak[0], prey(level).get(0).getY() - y);
			});
		}
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(reached[0], "it reached down for the husk as it dived");
			check(held[0], "it took the husk in its talons");
			check(peak[0] > 20.0, String.format(Locale.ROOT, "it carried the husk up (%.1f blocks)", peak[0]));
			check(dragon != null && !prey(level).isEmpty() && !prey(level).get(0).isPassenger()
					&& DragonfallDragon.brain(dragon).prey.hold() == Grip.Hold.NONE, "and dropped it");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);

		// the seize, on the ground: a husk with plenty of health to chew on
		int sx = gx + 100;
		command(view(sx - 20, y + 8, gz, sx, y + 3, gz), 40);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"df_prey\"],"
				+ "attributes:[{id:\"minecraft:generic.max_health\",base:200.0}],Health:200f}", sx, y, gz - 8), 2);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"df_helper\"]}", sx - 6, y, gz - 6), 2);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"],Rotation:[180f,0f]}", sx, y, gz), 6);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon != null && !prey(level).isEmpty()) GroundFightPhase.start(dragon, prey(level).get(0));
		});
		STEPS.add(new Step(70, mc -> {}));      // its roar on landing
		float[] health = new float[1];
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			boolean ok = dragon != null && !prey(level).isEmpty()
					&& dragon.getPhaseManager().getCurrentPhase() instanceof GroundFightPhase fight && fight.seize(prey(level).get(0));
			check(ok, "a dragon on the ground takes the husk in its jaws");
			if (!prey(level).isEmpty()) health[0] = prey(level).get(0).getHealth();
		});
		for (int i = 0; i < 10; i++) closeUp(0, String.format(Locale.ROOT, "seize-%02d", i), 3, 7.0);
		STEPS.add(new Step(20, mc -> {}));
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = prey(level);
			check(dragon != null && DragonfallDragon.brain(dragon).prey.hold() == Grip.Hold.JAW && !prey.isEmpty() && prey.get(0).getVehicle() == dragon,
					"it holds the husk in its jaws");
			check(!prey.isEmpty() && prey.get(0).getY() > y + 2.0, String.format(Locale.ROOT, "the husk hangs from its jaws (%.1f up)",
					prey.isEmpty() ? 0.0 : prey.get(0).getY() - y));
			check(!prey.isEmpty() && prey.get(0).getHealth() < health[0], "and is chewed");
			// someone else strikes at the head
			List<? extends Husk> helper = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_helper"));
			if (dragon != null && !helper.isEmpty()) dragon.hurt(dragon.getSubEntities()[0], level.damageSources().mobAttack(helper.get(0)), 1.0F);
		});
		STEPS.add(new Step(3, mc -> {}));
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null && DragonfallDragon.brain(dragon).prey.hold() == Grip.Hold.NONE && !prey(level).isEmpty() && !prey(level).get(0).isPassenger(),
					"a blow at its head makes it drop the husk");
		});
		// a hold left alone ends with the prey flung off
		STEPS.add(new Step(40, mc -> {}));
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null || prey(level).isEmpty() || !(dragon.getPhaseManager().getCurrentPhase() instanceof GroundFightPhase fight)) return;
			fight.seize(prey(level).get(0));
		});
		for (int i = 0; i < 6; i++) closeUp(0, String.format(Locale.ROOT, "seize-shake-%02d", i), 20, 7.0);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = prey(level);
			check(dragon != null && DragonfallDragon.brain(dragon).prey.hold() == Grip.Hold.NONE && !prey.isEmpty() && !prey.get(0).isPassenger(),
					"left alone, the hold ends: the husk is flung off");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
		command("kill @e[tag=df_helper]", 2);
	}

	/**
	 * A close look at what the AI dragon holds: the camera {@code distance} blocks off to the dragon's
	 * left of its held prey (or of part {@code part} when it holds nothing), level with it.
	 */
	private static void closeUp(int part, String name, int ticks, double distance) {
		STEPS.add(new Step(Math.max(ticks, 3), mc -> {
			EnderDragon dragon = clientAiDragon(mc);
			if (dragon == null) return;
			var held = DragonfallDragon.brain(dragon).prey.prey();
			var at = held != null ? held.position().add(0.0, held.getBbHeight() / 2.0, 0.0) : dragon.getSubEntities()[part].position();
			double yaw = Math.toRadians(DragonfallDragon.brain(dragon).body.yaw(1.0F));
			double cx = at.x - Math.cos(yaw) * distance + Math.sin(yaw) * 2.0, cz = at.z - Math.sin(yaw) * distance - Math.cos(yaw) * 2.0;
			mc.player.connection.sendCommand(view(cx, at.y + 1.5, cz, at.x, at.y, at.z));
		}));
		STEPS.add(new Step(2, mc -> Screenshot.grab(mc.gameDirectory, "df-" + name + ".png", mc.getMainRenderTarget(),
				message -> LOG.info("{}", message.getString()))));
	}

	private static List<? extends Husk> prey(ServerLevel level) {
		return level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
	}

	/**
	 * The procedural tail among blocks: a frozen dragon (facing north, its tail to the south) with a wall
	 * across behind it, a pillar beside its tail and a step under it. Through the idle, the tail strike
	 * and the walk the drawn tail must lie on the step and bend round the pillar and along the wall,
	 * never into a block ({@link Tail#overlap}, every tick).
	 */
	private static void tailCage(int tx, int y, int tz) {
		command(view(tx - 22, y + 7, tz + 6, tx, y + 2, tz + 6), 40);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", tx - 9, y, tz + 8, tx + 9, y + 6, tz + 9), 2);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", tx + 2, y, tz + 4, tx + 3, y + 5, tz + 5), 2);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", tx - 4, y, tz + 3, tx + 1, y, tz + 7), 2);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {NoAI:1b}", tx, y, tz), 30);
		String side = view(tx - 22, y + 7, tz + 6, tx, y + 2, tz + 6);
		String top = view(tx - 6, y + 22, tz + 10, tx, y, tz + 5);
		String back = view(tx + 6, y + 9, tz + 22, tx, y + 2, tz + 4);
		double[] worst = new double[1];
		for (DragonAnim anim : new DragonAnim[]{DragonAnim.IDLE, DragonAnim.TAIL_SWEEP, DragonAnim.WALK}) {
			String name = "tail-cage-" + anim.name().toLowerCase(Locale.ROOT);
			STEPS.add(new Step(10, mc -> DragonDebug.forcedAnimation = anim));
			shoot(side, name + "-side", 12);
			shoot(top, name + "-top", 4);
			shoot(back, name + "-back", 4);
			for (int i = 0; i < 40; i++) {
				STEPS.add(new Step(1, mc -> {
					EnderDragon dragon = nearestDragon(mc, tx + 0.5, tz + 0.5);
					if (dragon != null) worst[0] = Math.max(worst[0], LimbAnimator.state(dragon).tail.overlap());
				}));
			}
		}
		STEPS.add(new Step(1, mc -> {
			REPORT.add(String.format(Locale.ROOT, "INFO tail cage: the drawn tail overlaps blocks by %.3f at worst", worst[0]));
			check(worst[0] < 0.05, "the tail bends round the wall, pillar and step behind it, never into them");
		}));
	}

	private static EnderDragon nearestDragon(Minecraft mc, double x, double z) {
		EnderDragon best = null;
		double distance = 4.0;
		for (var entity : mc.level.entitiesForRendering()) {
			if (entity instanceof EnderDragon dragon && Math.hypot(dragon.getX() - x, dragon.getZ() - z) < distance) {
				best = dragon;
				distance = Math.hypot(dragon.getX() - x, dragon.getZ() - z);
			}
		}
		return best;
	}

	/**
	 * The AI, live: a wild dragon summoned inside a cage of leaves next to a stone pillar. It must smash
	 * out through the leaves and leave the stone alone; then it is sent to land beside a husk, bite it on
	 * the ground, and take off again with the jump. Hitboxes are photographed on the ground and in flight.
	 */
	private static void liveDragon(int lx, int y, int lz) {
		int pillar = 9 * 41 * 9;
		// stand there first: commands only build in loaded chunks
		command(view(lx - 40, y + 20, lz, lx, y + 12, lz), 40);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", lx + 12, y, lz - 4, lx + 20, y + 40, lz + 4), 2);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:oak_leaves[persistent=true] hollow", lx - 7, y + 12, lz - 7, lx + 7, y + 26, lz + 7), 2);
		int[] leaves = new int[1];
		server(level -> leaves[0] = count(level, lx - 7, y + 12, lz - 7, lx + 7, y + 26, lz + 7, Blocks.OAK_LEAVES));
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", lx, y + 17, lz), 40);
		for (int i = 0; i < 12; i++) track(String.format(Locale.ROOT, "live-roam-%02d", i), 15, 34);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null, "the wild dragon is alive");
			if (dragon == null) return;
			int left = count(level, lx - 7, y + 12, lz - 7, lx + 7, y + 26, lz + 7, Blocks.OAK_LEAVES);
			check(left < leaves[0], "it smashed through the leaves around it (" + (leaves[0] - left) + " broken)");
			int stone = count(level, lx + 12, y, lz - 4, lx + 20, y + 40, lz + 4, Blocks.STONE);
			check(stone == pillar, "the stone pillar beside it is intact (" + stone + "/" + pillar + ")");
			check(DragonfallDragon.brain(dragon).context() == DragonBrain.Context.WILD, "a summoned dragon is wild");
			check(dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.HOVERING, "it left its hover to roam");
		});

		// the ground assault: the husk stands still, so the aimed blows land
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}", lx - 40, y, lz + 10), 5);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
			check(dragon != null && !prey.isEmpty() && DragonfallDragon.brain(dragon).tryGroundAssault(prey.get(0)),
					"there is room to land beside the husk");
		});
		Set<String> phases = new LinkedHashSet<>();
		for (int i = 0; i < 44; i++) {
			track(String.format(Locale.ROOT, "live-ground-%02d", i), 10, 30);
			STEPS.add(new Step(1, mc -> {
				EnderDragon dragon = clientAiDragon(mc);
				if (dragon != null) phases.add(dragon.getPhaseManager().getCurrentPhase().getPhase().toString().replaceAll(" .*", ""));
			}));
		}
		STEPS.add(new Step(1, mc -> mc.getEntityRenderDispatcher().setRenderHitBoxes(true)));
		track("live-ground-hitboxes", 3, 26);
		STEPS.add(new Step(1, mc -> mc.getEntityRenderDispatcher().setRenderHitBoxes(false)));
		server(level -> {
			REPORT.add("INFO phases seen: " + phases);
			check(phases.contains("DragonfallGroundApproach"), "it flew down to the landing site");
			check(phases.contains("DragonfallGroundFight"), "it landed and fought on the ground");
			List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
			check(prey.isEmpty() || prey.get(0).getHealth() < prey.get(0).getMaxHealth(), "an aimed blow (bite or tail) hit the husk, which stood still");
		});

		// the takeoff: leaving the ground is always the jump, then a climb on the wings
		double[] groundY = new double[1];
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null) return;
			groundY[0] = dragon.getY();
			dragon.getPhaseManager().setPhase(EnderDragonPhase.TAKEOFF);
			check(dragon.getPhaseManager().getCurrentPhase().getPhase() == DragonPhases.LIFTOFF, "a takeoff from the ground is the jump");
		});
		for (int i = 0; i < 10; i++) track("live-takeoff-" + i, 5, 30);
		for (int i = 0; i < 6; i++) track("live-climb-" + i, 15, 34);
		STEPS.add(new Step(1, mc -> mc.getEntityRenderDispatcher().setRenderHitBoxes(true)));
		track("live-flight-hitboxes", 3, 34);
		STEPS.add(new Step(1, mc -> mc.getEntityRenderDispatcher().setRenderHitBoxes(false)));
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null && dragon.getY() > groundY[0] + 6, "it climbed after the jump ("
					+ (dragon == null ? "gone" : String.format(Locale.ROOT, "%.1f", dragon.getY() - groundY[0])) + " blocks)");
			int stone = count(level, lx + 12, y, lz - 4, lx + 20, y + 40, lz + 4, Blocks.STONE);
			check(stone == pillar, "the pillar is still intact at the end (" + stone + "/" + pillar + ")");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
	}

	/**
	 * The landing at speed: a wild dragon in fast flight is sent to land on flat ground 90 blocks ahead of
	 * it. It must line up, glide in and land running ({@link DragonAnim#LAND}): feet striking the ground,
	 * a skid, and stopping on the site, then stand there.
	 */
	private static void runningLanding(int rx, int y, int rz) {
		command(view(rx - 40, y + 20, rz, rx, y + 12, rz), 40);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", rx, y + 26, rz), 20);
		// let it get going
		for (int i = 0; i < 4; i++) track("landing-cruise-" + i, 20, 34);
		int[] site = new int[3];
		boolean[] sent = new boolean[1];
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null) return;
			double yaw = Math.toRadians(dragon.getYRot());
			int sx = Mth.floor(dragon.getX() - Math.sin(yaw) * 90), sz = Mth.floor(dragon.getZ() + Math.cos(yaw) * 90);
			LevelGrid grid = new LevelGrid(level);
			int sy = new LandingSite(grid).fits(sx, sz);
			REPORT.add(String.format(Locale.ROOT, "INFO landing: speed %.2f, site %d %d %d", DragonfallDragon.brain(dragon).horizontalSpeed(), sx, sy, sz));
			if (sy == BlockGrid.NO_GROUND) return;
			site[0] = sx;
			site[1] = sy;
			site[2] = sz;
			GroundApproachPhase.start(dragon, new int[]{sx, sy, sz}, null);
			sent[0] = true;
		});
		boolean[] ran = new boolean[1], struck = new boolean[1], planned = new boolean[1];
		double[] fastest = new double[1], stop = {Double.NaN, 0.0};
		String[] why = new String[1];
		for (int i = 0; i < 45; i++) {
			track(String.format(Locale.ROOT, "landing-%02d", i), 6, 24, 5);
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				if (dragon == null) return;
				DragonBrain brain = DragonfallDragon.brain(dragon);
				if (dragon.getPhaseManager().getCurrentPhase() instanceof GroundApproachPhase approach) {
					planned[0] |= approach.runningIn();
					if (approach.fallback() != null && why[0] == null) why[0] = approach.fallback();
				}
				if (dragon.getPhaseManager().getCurrentPhase() instanceof GroundApproachPhase approach && approach.landing()) {
					if (!ran[0]) fastest[0] = brain.horizontalSpeed();
					ran[0] = true;
					struck[0] |= approach.grounded() && Math.abs(dragon.getY() - site[1]) < 0.01;
				}
				// where the landing left it (afterwards it rests, and may walk about)
				if (ran[0] && Double.isNaN(stop[0]) && brain.onGround()) {
					stop[0] = dragon.getX();
					stop[1] = dragon.getZ();
				}
			});
		}
		for (int i = 0; i < 3; i++) track("landing-stand-" + i, 10, 30, 5);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(sent[0], "there is a landing site ahead of the dragon");
			check(planned[0], "fast enough, with a clear runway, it came in to land running");
			if (why[0] != null) REPORT.add("INFO the running approach hovered down instead: " + why[0]);
			check(ran[0], String.format(Locale.ROOT, "it landed running (the landing started at %.2f blocks/tick)", fastest[0]));
			check(struck[0], "its feet struck the ground on the site's level");
			check(dragon != null && DragonfallDragon.brain(dragon).onGround(), "it stands on the ground after the landing");
			double off = Math.hypot(stop[0] - site[0] - 0.5, stop[1] - site[2] - 0.5);
			check(off < 2.5, String.format(Locale.ROOT, "it skidded to a stop on the site (%.1f blocks off)", off));
		});
		command("kill @e[tag=df_ai]", 2);
	}

	/**
	 * Free roaming: a summoned dragon with nobody to hunt (the camera is in creative) is left alone. It
	 * must wander off from where it appeared, come down somewhere on its own, and walk about there.
	 */
	private static void roaming(int rx, int y, int rz) {
		command(view(rx - 40, y + 20, rz, rx, y + 12, rz), 40);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", rx, y + 20, rz), 20);
		Set<String> phases = new LinkedHashSet<>();
		double[] furthest = new double[1], walked = new double[1], last = {Double.NaN, 0.0};
		for (int i = 0; i < 60; i++) {
			track(String.format(Locale.ROOT, "roam-%02d", i), 20, 34);
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				if (dragon == null) return;
				phases.add(dragon.getPhaseManager().getCurrentPhase().getPhase().toString().replaceAll(" .*", ""));
				furthest[0] = Math.max(furthest[0], Math.hypot(dragon.getX() - rx, dragon.getZ() - rz));
				if (DragonfallDragon.brain(dragon).onGround()) {
					if (!Double.isNaN(last[0])) walked[0] += Math.hypot(dragon.getX() - last[0], dragon.getZ() - last[1]);
					last[0] = dragon.getX();
					last[1] = dragon.getZ();
				} else {
					last[0] = Double.NaN;
				}
			});
		}
		server(level -> {
			REPORT.add("INFO roaming phases seen: " + phases);
			check(furthest[0] > 50, String.format(Locale.ROOT, "it wandered off from where it appeared (%.0f blocks)", furthest[0]));
			check(phases.contains("DragonfallGroundFight"), "it came down on its own with nobody to hunt");
			check(walked[0] > 6, String.format(Locale.ROOT, "it walked about on the ground (%.1f blocks)", walked[0]));
		});
		command("kill @e[tag=df_ai]", 2);
	}

	/**
	 * Feet on uneven ground. A dragon stands on flat ground before a stepped mound (three terraces, one block up
	 * every four) and walks over it to a husk beyond, the last stretch with a one-block step under its
	 * left side, so its feet stand at different heights front to back and side to side. Every tick on
	 * the ground {@link LimbContact} measures how close each limb comes to the ground under it: the
	 * nearest any of its corners is above (floating) or below (sunk in) the block under that corner. A planted foot touches: over each walk cycle every foot must
	 * come within {@link #CONTACT} of the ground at least once, and none may sink deeper than that.
	 * Flat ground is checked the same way first, which shows the measurement itself is sound.
	 */
	private static void hills(int tx, int y, int tz) {
		command(view(tx - 30, y + 12, tz + 20, tx, y + 2, tz), 40);
		for (int[] t : new int[][]{{-9, 9, 0}, {-5, 5, 1}, {-1, 1, 2}}) {
			command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:grass_block", tx + t[0], y + t[2], tz - 12, tx + t[1], y + t[2], tz + 12), 2);
		}
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:grass_block", tx + 10, y, tz - 12, tx + 24, y, tz - 1), 2);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,Invulnerable:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}",
				tx + 18, y, tz), 2);
		// nose toward +x (a dragon's yaw 90)
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"],Rotation:[90f,0f]}", tx - 20, y, tz), 6);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
			check(dragon != null && !prey.isEmpty(), "a dragon and its prey at the hills");
			if (dragon != null && !prey.isEmpty()) GroundFightPhase.start(dragon, prey.get(0));
		});
		// let the model settle from the hover it was summoned in into the ground pose before measuring
		STEPS.add(new Step(20, mc -> {}));
		STEPS.add(new Step(1, mc -> LimbContact.start()));
		for (int i = 0; i < 40; i++) track(String.format(Locale.ROOT, "hills-%02d", i), 8, 20, 2);
		STEPS.add(new Step(1, mc -> {
			List<LimbContact.Sample> samples = LimbContact.stop();
			REPORT.add("INFO hills: " + samples.size() + " ticks measured on the ground (per tick: showcase-limbs.csv)");
			writeLimbs(mc, samples);
			check(samples.size() > 150, "the dragon stood and walked long enough to measure (" + samples.size() + " ticks)");
			footing(samples, false, y);
			footing(samples, true, y);
		}));
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
	}

	/** How far a planted foot may be off the ground (blocks): a quarter block, 4 model px. */
	private static final double CONTACT = 0.25;
	/** Ticks of one walk cycle: every foot is planted at some point within it. */
	private static final int CYCLE = (int) Math.round(PoseTrack.length(DragonAnim.WALK) * 20);

	/**
	 * Reports and checks the feet over either the flat stretches (all four on the base ground) or the
	 * uneven ones. Floating: over a whole walk cycle a foot never came down to within this of the ground
	 * under it. Sunk: the deepest a foot went into the ground.
	 */
	private static void footing(List<LimbContact.Sample> samples, boolean uneven, int baseY) {
		String where = uneven ? "uneven ground" : "flat ground";
		int n = LimbContact.LIMBS.length;
		double[] floating = new double[n], sunk = new double[n];
		int windows = 0;
		for (int end = CYCLE; end <= samples.size(); end++) {
			List<LimbContact.Sample> window = samples.subList(end - CYCLE, end);
			// flat: the whole cycle on the base ground; uneven: at least half of it off it
			long off = window.stream().filter(s -> !flat(s, baseY)).count();
			if (uneven ? off < CYCLE / 2 : off > 0) continue;
			windows++;
			for (int limb = 0; limb < n; limb++) {
				double low = Double.MAX_VALUE;
				for (LimbContact.Sample s : window) {
					if (Double.isNaN(s.gap()[limb])) continue;
					low = Math.min(low, s.gap()[limb]);
					sunk[limb] = Math.min(sunk[limb], s.gap()[limb]);
				}
				floating[limb] = Math.max(floating[limb], low);
			}
		}
		check(windows > 0, "there were walk cycles on " + where + " (" + windows + ")");
		if (windows == 0) return;
		for (int limb = 0; limb < n; limb++) {
			String name = LimbContact.LIMBS[limb];
			REPORT.add(String.format(Locale.ROOT, "INFO %s, %s foot: floats up to %.2f, sinks up to %.2f blocks", where, name, floating[limb], -sunk[limb]));
			check(floating[limb] <= CONTACT, String.format(Locale.ROOT, "on %s the %s foot comes down to the ground (%.2f above at worst)", where, name, floating[limb]));
			check(sunk[limb] >= -CONTACT, String.format(Locale.ROOT, "on %s the %s foot stays out of the ground (%.2f in at worst)", where, name, -sunk[limb]));
		}
	}

	private static void writeLimbs(Minecraft mc, List<LimbContact.Sample> samples) {
		List<String> lines = new ArrayList<>();
		lines.add("tick,anim,gap_lh,gap_rh,gap_lf,gap_rf,ground_lh,ground_rh,ground_lf,ground_rf");
		for (LimbContact.Sample s : samples) {
			StringBuilder line = new StringBuilder(s.tick() + "," + s.anim());
			for (double g : s.gap()) line.append(String.format(Locale.ROOT, ",%.3f", g));
			for (double g : s.ground()) line.append(String.format(Locale.ROOT, ",%.2f", g));
			lines.add(line.toString());
		}
		try {
			Files.write(mc.gameDirectory.toPath().resolve("showcase-limbs.csv"), lines);
		} catch (IOException e) {
			LOG.error("Could not write the limb measurements", e);
		}
	}

	/** All four feet over the base ground. */
	private static boolean flat(LimbContact.Sample s, int baseY) {
		for (double g : s.ground()) {
			if (Double.isNaN(g) || Math.abs(g - baseY) > 0.01) return false;
		}
		return true;
	}

	/**
	 * The stream breath: an AI dragon (NoAI skips phases) is put in the breath phase on the server, aiming
	 * north at three husks where its sweep lands (11-18 blocks out; right under its chin is out of reach)
	 * and one off to the side; then a dragon fireball is dropped to check
	 * its cloud burns with void flame.
	 */
	private static void breath(int x, int y, int z) {
		int bx = x - 120, bz = z;
		command(view(bx - 22, y + 4, bz - 8, bx, y + 3, bz - 8), 40);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_breath\"],Rotation:[0f,0f]}", bx, y, bz), 20);
		for (int[] at : new int[][]{{0, -11}, {0, -15}, {1, -18}, {-9, -13}}) {
			command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"df_target\"]}", bx + at[0], y, bz + at[1]), 1);
		}
		server(level -> {
			for (EnderDragon d : level.getEntities(EntityType.ENDER_DRAGON, e -> e.getTags().contains("df_breath"))) {
				d.getPhaseManager().setPhase(BreathStreamPhase.PHASE);
			}
		});
		String breathSide = view(bx - 22, y + 4, bz - 8, bx, y + 3, bz - 8);
		String breathFront = view(bx + 10, y + 7, bz - 22, bx, y + 3, bz - 4);
		// the heat climbs over the 40-tick inhale (HeatGlowLayer): chest, throat, jaw, then the stream
		String breathClose = view(bx - 13, y + 3, bz - 6, bx, y + 4, bz - 4);
		shoot(breathClose, "breath-heat-chest", 10);  // ~tick 12
		shoot(breathClose, "breath-heat-neck", 8);    // ~tick 22
		shoot(breathClose, "breath-heat-jaw", 10);    // ~tick 34: embers in the parting jaw
		shoot(breathSide, "breath-stream-0", 12);     // stream
		shoot(breathFront, "breath-stream-front", 12);
		shoot(breathSide, "breath-stream-1", 14);
		STEPS.add(new Step(40, mc -> {}));
		server(level -> {
			var husks = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_target"));
			// the stream kills a husk outright, and a dead husk is gone from the list: count the unhurt ones
			long burned = 3 - husks.stream().filter(h -> h.getX() > bx - 4 && h.getHealth() >= h.getMaxHealth()).count();
			boolean sideSafe = husks.stream().filter(h -> h.getX() <= bx - 4).allMatch(h -> h.getHealth() >= h.getMaxHealth());
			check(burned == 3, "the stream breath burns the three husks in its line (" + burned + "/3)");
			check(sideSafe, "the husk beside the stream is untouched");
		});
		command("kill @e[tag=df_breath]", 2);
		command("kill @e[tag=df_target]", 2);
		command(String.format(Locale.ROOT, "summon minecraft:dragon_fireball %d %d %d {Motion:[0.0,-0.5,0.0]}", bx, y + 8, bz), 40);
		shoot(view(bx - 8, y + 4, bz - 8, bx, y, bz), "fireball-cloud", 20);
		server(level -> {
			var clouds = level.getEntities(EntityType.AREA_EFFECT_CLOUD, e -> true);
			check(!clouds.isEmpty() && clouds.stream().allMatch(c -> ((AreaEffectCloud) c).getParticle() == BreathParticles.VOID_FLAME),
					"the dragon fireball's cloud burns with void flame (" + clouds.size() + " clouds)");
		});
	}

	/** Runs on the integrated server's thread and waits for it (so checks happen in script order). */
	private static void server(Consumer<ServerLevel> action) {
		STEPS.add(new Step(1, mc -> mc.getSingleplayerServer().executeBlocking(() -> action.accept(mc.getSingleplayerServer().overworld()))));
	}

	private static EnderDragon aiDragon(ServerLevel level) {
		// a killed dragon lingers through its death: not it
		List<? extends EnderDragon> found = level.getEntities(EntityType.ENDER_DRAGON, e -> e.getTags().contains("df_ai") && !e.isDeadOrDying());
		return found.isEmpty() ? null : found.get(0);
	}

	/** The client's copy of the AI dragon (tags are not synced; it is the only one with AI). */
	private static EnderDragon clientAiDragon(Minecraft mc) {
		for (var entity : mc.level.entitiesForRendering()) {
			if (entity instanceof EnderDragon dragon && !dragon.isNoAi() && !dragon.isDeadOrDying()) return dragon;
		}
		return null;
	}

	private static int count(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
			if (level.getBlockState(pos).is(block)) n++;
		}
		return n;
	}

	/** Moves the camera beside the AI dragon wherever it is now, waits, and grabs a frame. */
	private static void track(String name, int ticks, double distance) {
		track(name, ticks, distance, 8);
	}

	/** {@link #track(String, int, double)} with the camera {@code height} blocks above the dragon's feet. */
	private static void track(String name, int ticks, double distance, double height) {
		STEPS.add(new Step(Math.max(ticks, 3), mc -> {
			EnderDragon dragon = clientAiDragon(mc);
			if (dragon == null) return;
			double yaw = Math.toRadians(DragonfallDragon.brain(dragon).body.yaw(1.0F));
			// from the dragon's left side, a little ahead and above
			double cx = dragon.getX() - Math.cos(yaw) * distance + Math.sin(yaw) * 8;
			double cz = dragon.getZ() - Math.sin(yaw) * distance - Math.cos(yaw) * 8;
			mc.player.connection.sendCommand(view(cx, dragon.getY() + height, cz, dragon.getX(), dragon.getY() + Math.min(3, height), dragon.getZ()));
		}));
		STEPS.add(new Step(2, mc -> Screenshot.grab(mc.gameDirectory, "df-" + name + ".png", mc.getMainRenderTarget(),
				message -> LOG.info("{}", message.getString()))));
	}

	private static String view(double x, double y, double z, double tx, double ty, double tz) {
		return String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f facing %.1f %.1f %.1f", x, y, z, tx, ty, tz);
	}

	private static void command(String command, int wait) {
		STEPS.add(new Step(wait, mc -> mc.player.connection.sendCommand(command)));
	}

	/** Moves the camera, waits `ticks` for the pose to develop, then grabs the frame. */
	private static void shoot(String view, String name, int ticks) {
		command(view, Math.max(ticks, 3));
		STEPS.add(new Step(2, mc -> Screenshot.grab(mc.gameDirectory, "df-" + name + ".png", mc.getMainRenderTarget(),
				message -> LOG.info("{}", message.getString()))));
	}

	private static void check(boolean ok, String what) {
		REPORT.add((ok ? "PASS " : "FAIL ") + what);
		if (!ok) failed = true;
	}

	private static void finish(Minecraft mc) {
		REPORT.add(failed ? "RESULT FAIL" : "RESULT PASS");
		try {
			Files.write(mc.gameDirectory.toPath().resolve("showcase-report.txt"), REPORT);
		} catch (IOException e) {
			LOG.error("Could not write the showcase report", e);
		}
		REPORT.forEach(line -> LOG.info("{}", line));
		mc.stop();
	}
}
