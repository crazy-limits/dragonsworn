package crazylimits.dragonsworn.mc.client;

import crazylimits.dragonsworn.ai.CombatStance;
import crazylimits.dragonsworn.ai.DeathFlight;
import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.anim.DragonDebug;
import crazylimits.dragonsworn.body.PoseTrack;
import crazylimits.dragonsworn.body.Tail;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonPhases;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.PreyHold;
import crazylimits.dragonsworn.mc.LevelGrid;
import crazylimits.dragonsworn.mc.breath.BreathParticles;
import crazylimits.dragonsworn.mc.breath.DragonFire;
import crazylimits.dragonsworn.mc.breath.BreathStreamPhase;
import crazylimits.dragonsworn.mc.phase.BreathPassPhase;
import crazylimits.dragonsworn.mc.phase.FlybyBitePhase;
import crazylimits.dragonsworn.mc.phase.HoverAttackPhase;
import crazylimits.dragonsworn.mc.phase.RoamPhase;
import crazylimits.dragonsworn.mc.phase.GroundApproachPhase;
import crazylimits.dragonsworn.mc.phase.GroundFightPhase;
import crazylimits.dragonsworn.mc.phase.SnatchPhase;
import crazylimits.dragonsworn.body.Grip;
import crazylimits.dragonsworn.anim.BreathAttack;
import crazylimits.dragonsworn.anim.BreathPass;
import crazylimits.dragonsworn.flight.FlightModel;
import crazylimits.dragonsworn.nav.BlockGrid;
import crazylimits.dragonsworn.nav.Foothold;
import crazylimits.dragonsworn.nav.LandingSite;
import net.minecraft.client.Minecraft;
import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.mc.client.config.ConfigScreens;
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
import net.minecraft.world.phys.Vec3;
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
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The in-game test, on any loader. Off unless the JVM runs with {@code -Ddragonsworn.showcase=true}
 * ({@code ./gradlew :<target>:runClient -Pdragonsworn.showcase}).
 *
 * <p>From the title screen it creates a fresh flat creative world, lays a stone yard, summons a frozen
 * (NoAI) vanilla Ender Dragon, checks that Dragonsworn's renderer is the one drawing it, then loops
 * every animation and photographs it from the front three-quarter, the side and above. Then it tests
 * the AI live (see {@link #liveDragon}) and the stream breath. Screenshots go to
 * {@code <run>/screenshots/df-*.png}, a pass/fail report to {@code <run>/showcase-report.txt}, and the
 * game quits when done.
 */
public final class Showcase {
	public static final boolean ENABLED = Boolean.getBoolean("dragonsworn.showcase");
	private static final Logger LOG = LoggerFactory.getLogger("dragonsworn/showcase");
	private static final String WORLD = "dragonsworn_showcase";
	/** Runs only this stage when set ({@code -Pdragonsworn.showcase=grabs}, {@code =landing}). */
	private static final String ONLY = System.getProperty("dragonsworn.showcase.only", "");

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
		if (ONLY.equals("config")) {
			configScreen();
			return;
		}
		if (ONLY.equals("air")) {
			STEPS.add(new Step(1, mc -> mc.options.hideGui = true));
			air(x, y, z + 150);
			return;
		}
		if (ONLY.equals("pass")) {
			STEPS.add(new Step(1, mc -> mc.options.hideGui = true));
			breathPass(x, y, z);
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
		if (ONLY.equals("collision")) {
			STEPS.add(new Step(1, mc -> mc.options.hideGui = true));
			collision(x, y, z + 150);
			return;
		}
		if (ONLY.equals("hitboxes")) {
			STEPS.add(new Step(1, mc -> mc.options.hideGui = true));
			hitboxes(x, y, z + 150);
			return;
		}
		if (ONLY.equals("narrow")) {
			STEPS.add(new Step(1, mc -> mc.options.hideGui = true));
			narrow(x, y, z + 150, Foothold.UPRIGHT);
			narrow(x + 150, y, z + 150, Foothold.CLING);
			return;
		}
		if (ONLY.equals("stance")) {
			STEPS.add(new Step(1, mc -> mc.options.hideGui = true));
			stance(x, y, z + 150);
			return;
		}
		if (ONLY.equals("death")) {
			STEPS.add(new Step(1, mc -> mc.options.hideGui = true));
			death(x, y, z + 150);
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
			check(ours, "the Ender Dragon is drawn by Dragonsworn's renderer");
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
		breathPass(x + 150, y, z);
		grabs(x, y, z - 150);
		stance(x + 300, y, z + 450);
		hitboxes(x + 300, y, z + 600);
		collision(x + 450, y, z + 600);
		narrow(x + 450, y, z + 150, Foothold.UPRIGHT);
		narrow(x + 450, y, z + 300, Foothold.CLING);
	}

	/**
	 * A narrow foothold: a husk on top of a lone 1-block pillar, 12 blocks up, and beside it (6 blocks east)
	 * the only place to come down: a 3 by 3 platform ({@link Foothold#UPRIGHT}) or another lone pillar
	 * ({@link Foothold#CLING}). The dragon must land there with that foothold, stay on it (no walking), and
	 * bite the husk with that foothold's bite, never its tail.
	 */
	private static void narrow(int sx, int y, int sz, Foothold foothold) {
		String name = foothold.name().toLowerCase(Locale.ROOT);
		int top = y + 12, half = foothold == Foothold.UPRIGHT ? 1 : 0, px = sx + 6;
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", sx, y, sz, sx, top - 1, sz), 2);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", px - half, y, sz - half, px + half, top - 1, sz + half), 2);
		command(view(sx - 30, top + 12, sz - 10, sx + 3, top, sz), 40);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}", sx, top, sz), 5);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", sx - 25, top + 15, sz), 20);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
			check(dragon != null && !prey.isEmpty() && DragonswornDragon.brain(dragon).tryGroundAssault(prey.get(0)),
					name + ": somewhere to come down beside the husk");
			check(dragon != null && dragon.getPhaseManager().getCurrentPhase() instanceof GroundApproachPhase approach
					&& approach.foothold() == foothold, name + ": it comes down " + name);
		});
		boolean[] seen = new boolean[4];   // landed with it, bit with its bite, struck with the tail or roared, walked off
		double[] at = {Double.NaN, 0.0};
		for (int i = 0; i < 70; i++) {
			if (i == 30 || i == 50) {
				shoot(view(px - 14, top + 6, sz - 12, px, top + 4, sz), "narrow-" + name + "-" + i, 2);
			} else {
				track(String.format(Locale.ROOT, "narrow-%s-%02d", name, i), 6, 26, 6);
			}
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				if (dragon == null) return;
				DragonBrain brain = DragonswornDragon.brain(dragon);
				if (!brain.onGround()) return;
				seen[0] |= brain.foothold() == foothold;
				DragonAnim action = brain.action();
				seen[1] |= action == (foothold == Foothold.UPRIGHT ? DragonAnim.UPRIGHT_BITE : DragonAnim.CLING_BITE);
				seen[2] |= action == DragonAnim.TAIL_SWEEP || action == DragonAnim.ROAR || action == DragonAnim.ATTACK;
				if (Double.isNaN(at[0])) {
					at[0] = dragon.getX();
					at[1] = dragon.getZ();
				}
				seen[3] |= Math.hypot(dragon.getX() - at[0], dragon.getZ() - at[1]) > 1.0;
			});
		}
		server(level -> {
			List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
			check(seen[0], name + ": it landed on the foothold " + name);
			check(seen[1], name + ": it bit with the " + name + " bite");
			check(!seen[2], name + ": no tail strike, roar or four-legged bite up there");
			check(!seen[3], name + ": it stayed on its foothold");
			check(prey.isEmpty() || prey.get(0).getHealth() < prey.get(0).getMaxHealth(), name + ": the bite hurt the husk");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
	}

	/** Server and client turn the head alike (the server's hitboxes are the ones that are hit). */
	private static void lookAgrees() {
		double[] clientLook = new double[2];
		STEPS.add(new Step(1, mc -> {
			EnderDragon dragon = clientAiDragon(mc);
			if (dragon == null) return;
			clientLook[0] = DragonswornDragon.brain(dragon).look.yaw(1.0);
			clientLook[1] = DragonswornDragon.brain(dragon).look.pitch(1.0);
		}));
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			double yaw = dragon == null ? Double.NaN : DragonswornDragon.brain(dragon).look.yaw(1.0);
			double pitch = dragon == null ? Double.NaN : DragonswornDragon.brain(dragon).look.pitch(1.0);
			REPORT.add(String.format(Locale.ROOT, "INFO hitboxes: head look yaw %.1f / %.1f, pitch %.1f / %.1f (server / client)",
					yaw, clientLook[0], pitch, clientLook[1]));
			check(Math.abs(yaw - clientLook[0]) < 4.0 && Math.abs(pitch - clientLook[1]) < 4.0, "server and client turn the head alike");
		});
	}

	/**
	 * The head and neck hitboxes stay on the model while its head turns to what it watches: a resting
	 * dragon on the ground follows the camera (the nearest player) round, and every tick the anchors as
	 * drawn are compared with the hitboxes' centres. Server and client must agree on the look too.
	 */
	/**
	 * The hitboxes are soft, as mobs push each other: a husk (moved by the server) and the player (moved
	 * by its client) put inside the resting dragon's chest slide out of every hitbox over a second or
	 * two, not at once, and the dragon is not moved by them.
	 */
	private static void collision(int cx, int y, int cz) {
		command(view(cx - 14, y + 6, cz - 14, cx, y + 3, cz), 20);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"],Rotation:[0f,0f]}", cx, y, cz), 6);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null, "a dragon rests on the ground for the collision test");
			if (dragon != null) GroundFightPhase.start(dragon, null);
		});
		STEPS.add(new Step(40, mc -> {}));
		double[] dragonAt = new double[3];
		double[] early = {Double.NaN};
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			Husk husk = EntityType.HUSK.create(level);
			if (dragon == null || husk == null) return;
			dragonAt[0] = dragon.getX();
			dragonAt[2] = dragon.getZ();
			AABB chest = dragon.getSubEntities()[2].getBoundingBox();
			// with AI: a NoAI mob never moves, so nothing pushes it (as with vanilla's mobs)
			husk.moveTo(chest.getCenter().x + 0.3, chest.minY, chest.getCenter().z + 0.2, 0.0F, 0.0F);
			husk.setSilent(true);
			husk.addTag("df_target");
			level.addFreshEntity(husk);
			REPORT.add(String.format(Locale.ROOT, "INFO collision: husk put %.2f blocks into the chest", inside(husk, dragon)));
		});
		STEPS.add(new Step(2, mc -> {}));
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			var husks = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_target"));
			if (dragon != null && !husks.isEmpty()) early[0] = inside(husks.get(0), dragon);
		});
		STEPS.add(new Step(40, mc -> {}));
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			var husks = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_target"));
			double depth = dragon == null || husks.isEmpty() ? Double.NaN : inside(husks.get(0), dragon);
			check(early[0] > 0.05, String.format(Locale.ROOT, "the push is soft: a few ticks in, the husk is still %.2f inside", early[0]));
			check(depth < 0.01, String.format(Locale.ROOT, "two seconds on, the husk has been pushed out of every hitbox (%.3f in)", depth));
			double moved = dragon == null ? Double.NaN : Math.hypot(dragon.getX() - dragonAt[0], dragon.getZ() - dragonAt[2]);
			check(moved < 0.01, String.format(Locale.ROOT, "the dragon stays put (moved %.3f)", moved));
		});
		command("kill @e[tag=df_target]", 2);
		// the player: its own client pushes it (tp'd into the chest; flying, so only the push moves it)
		STEPS.add(new Step(2, mc -> {
			EnderDragon dragon = clientAiDragon(mc);
			if (dragon == null) return;
			AABB chest = dragon.getSubEntities()[2].getBoundingBox();
			mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", chest.getCenter().x - 0.3, chest.minY + 0.2, chest.getCenter().z + 0.2));
		}));
		STEPS.add(new Step(60, mc -> {}));
		STEPS.add(new Step(1, mc -> {
			EnderDragon dragon = clientAiDragon(mc);
			double depth = dragon == null ? Double.NaN : inside(mc.player, dragon);
			check(depth < 0.01, String.format(Locale.ROOT, "the player put inside the chest is pushed out of every hitbox (%.3f in)", depth));
		}));
		shoot(view(cx - 10, y + 6, cz - 10, cx, y + 3, cz), "collision", 1);
		command("kill @e[tag=df_ai]", 2);
	}

	/** How deep {@code entity} is in the dragon's hitboxes: the deepest overlap (its thinnest axis), 0 when outside. */
	private static double inside(net.minecraft.world.entity.Entity entity, EnderDragon dragon) {
		AABB e = entity.getBoundingBox();
		double worst = 0.0;
		for (var part : dragon.getSubEntities()) {
			AABB p = part.getBoundingBox();
			double dx = Math.min(e.maxX, p.maxX) - Math.max(e.minX, p.minX);
			double dy = Math.min(e.maxY, p.maxY) - Math.max(e.minY, p.minY);
			double dz = Math.min(e.maxZ, p.maxZ) - Math.max(e.minZ, p.minZ);
			worst = Math.max(worst, Math.max(0.0, Math.min(dx, Math.min(dy, dz))));
		}
		return worst;
	}

	private static void hitboxes(int hx, int y, int hz) {
		command(view(hx - 14, y + 6, hz - 14, hx, y + 3, hz), 40);
		// yaw 0: the dragon faces north (-z); +x is its right
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"],Rotation:[0f,0f]}", hx, y, hz), 6);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null, "a dragon rests on the ground for the hitbox test");
			if (dragon != null) GroundFightPhase.start(dragon, null);
		});
		double[][] views = {{-12, 4, -14}, {-17, 3, -2}, {16, 2, -5}, {4, 14, -17}, {11, 1, -12}, {-6, 9, -20}};
		double[] worst = new double[3], sum = new double[3], most = {0.0};
		int[] samples = {0};
		for (int v = 0; v < views.length; v++) {
			double[] at = views[v];
			STEPS.add(new Step(1, mc -> {
				EnderDragon dragon = clientAiDragon(mc);
				double cx = dragon == null ? hx : dragon.getX(), cz = dragon == null ? hz : dragon.getZ();
				mc.player.connection.sendCommand(view(cx + at[0], y + at[1], cz + at[2], cx, y + 4, cz));
			}));
			for (int t = 0; t < 30; t++) {
				boolean shot = t == 29;
				int view = v;
				STEPS.add(new Step(1, mc -> {
					EnderDragon dragon = clientAiDragon(mc);
					double[] off = dragon == null ? null : LimbAnimator.hitboxOffsets(dragon);
					if (off == null) return;
					for (int k = 0; k < off.length; k++) {
						worst[k] = Math.max(worst[k], off[k]);
						sum[k] += off[k];
					}
					samples[0]++;
					most[0] = Math.max(most[0], Math.abs(DragonswornDragon.brain(dragon).look.yaw(1.0)));
					if (shot) Screenshot.grab(mc.gameDirectory, String.format(Locale.ROOT, "df-hitbox-look-%d.png", view),
							mc.getMainRenderTarget(), message -> LOG.info("{}", message.getString()));
				}));
			}
			// settled on the second view (the dragon still): server and client turn the head alike
			if (v == 1) lookAgrees();
		}
		STEPS.add(new Step(1, mc -> {
			int n = Math.max(1, samples[0]);
			REPORT.add(String.format(Locale.ROOT, "INFO hitboxes: drawn anchor to hitbox centre over %d frames, mean / worst (blocks):"
					+ " head %.3f / %.3f, neck %.3f / %.3f, mid neck %.3f / %.3f; the look turned the head up to %.0f degrees",
					samples[0], sum[0] / n, worst[0], sum[1] / n, worst[1], sum[2] / n, worst[2], most[0]));
			check(samples[0] > 100, "the head was measured (" + samples[0] + " frames)");
			check(most[0] > 25.0, String.format(Locale.ROOT, "the head turned to watch (%.0f degrees)", most[0]));
			check(sum[0] / n < 0.2 && worst[0] < 0.5, "the head's hitbox stays on the drawn head as it turns");
			check(sum[1] / n < 0.2 && worst[1] < 0.5 && sum[2] / n < 0.2 && worst[2] < 0.5, "the neck's hitboxes stay on the drawn neck");
		}));
		command("kill @e[tag=df_ai]", 2);
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
	 * The breath pass, live: a wild dragon is sent at a husk on open ground. It must come in over it,
	 * glide through the breath (no beats), keep its height, point its straight neck down at where the
	 * flames land, and burn the husk.
	 */
	private static void breathPass(int px, int y, int pz) {
		STEPS.add(new Step(1, mc -> mc.options.hideGui = true));
		command(view(px - 30, y + 14, pz + 20, px, y + 6, pz), 40);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"df_prey\"],"
				+ "attributes:[{id:\"minecraft:generic.max_health\",base:200.0}],Health:200f}", px, y, pz), 2);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"],Rotation:[180f,0f]}", px, y + 18, pz + 80), 60);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null && !prey(level).isEmpty() && BreathPassPhase.start(dragon, prey(level).get(0)), "the dragon goes for a breath pass at the husk");
		});
		boolean[] breathed = new boolean[1], beat = new boolean[1];
		float[] health = {200.0F};
		double[] low = {Double.MAX_VALUE}, worst = new double[1];
		int[] measured = new int[1];
		for (int i = 0; i < 280; i++) {
			int step = i;
			STEPS.add(new Step(1, mc -> {
				mc.getSingleplayerServer().executeBlocking(() -> {
					ServerLevel level = mc.getSingleplayerServer().overworld();
					EnderDragon dragon = aiDragon(level);
					if (dragon == null || prey(level).isEmpty()) return;
					DragonBrain brain = DragonswornDragon.brain(dragon);
					if (brain.action() != DragonAnim.GLIDE_BREATH) return;
					breathed[0] = true;
					health[0] = Math.min(health[0], prey(level).get(0).getHealth());
					double ticks = BreathPassPhase.breathTicks(dragon, 0.0F);
					if (BreathPass.streaming((int) Math.round(ticks))) {
						low[0] = Math.min(low[0], dragon.getY() - y);
						beat[0] |= brain.flightPlan().mode() != FlightModel.Mode.GLIDE;
					}
				});
				EnderDragon dragon = clientAiDragon(mc);
				if (dragon == null) return;
				// the camera beside the flight path, a little behind the dragon and below it
				double yaw = Math.toRadians(dragon.getYRot());
				double cx = dragon.getX() - Math.cos(yaw) * 26 + Math.sin(yaw) * 10, cz = dragon.getZ() - Math.sin(yaw) * 26 - Math.cos(yaw) * 10;
				mc.player.connection.sendCommand(view(cx, y + 6, cz, dragon.getX() - Math.sin(yaw) * 6, dragon.getY() - 2, dragon.getZ() + Math.cos(yaw) * 6));
				double ticks = BreathPassPhase.breathTicks(dragon, 0.0F);
				if (Double.isNaN(ticks)) return;
				if (step % 4 == 0) {
					Screenshot.grab(mc.gameDirectory, String.format(Locale.ROOT, "df-pass-%03d.png", step), mc.getMainRenderTarget(),
							message -> LOG.info("{}", message.getString()));
				}
				var aim = BreathPassPhase.aimPoint(dragon);
				// the neck's line once it has swung down straight (the pose's lunge and the model's blend)
				if (aim == null || ticks < BreathPass.WINDUP_TICKS + 8 || ticks >= BreathPass.WINDUP_TICKS + BreathPass.STREAM_TICKS - 6) return;
				var head = dragon.getSubEntities()[0].getBoundingBox().getCenter();
				var neck = dragon.getSubEntities()[8].getBoundingBox().getCenter();
				var line = head.subtract(neck).normalize();
				var to = aim.subtract(head).normalize();
				worst[0] = Math.max(worst[0], Math.toDegrees(Math.acos(Mth.clamp(line.dot(to), -1.0, 1.0))));
				measured[0]++;
			}));
		}
		STEPS.add(new Step(1, mc -> {
			REPORT.add(String.format(Locale.ROOT, "INFO breath pass: lowest %.1f blocks over the ground while pouring; the neck's line is off the aim by up to %.1f deg (%d ticks)",
					low[0], worst[0], measured[0]));
			check(breathed[0], "it glided in and breathed");
			check(!beat[0], "it glides through the stream (no wingbeats)");
			check(low[0] > 6.0 && low[0] < 16.0, String.format(Locale.ROOT, "it keeps to the pass's height (%.1f)", low[0]));
			check(measured[0] > 20 && worst[0] < 15.0, "the straight neck points down at where the flames land");
			check(health[0] < 200.0F, String.format(Locale.ROOT, "the flames burned the husk (%.0f health left)", health[0]));
		}));
		server(level -> {
			int fire = dragonFire(level, px, y, pz, 16);
			check(fire > 0, "the breath pass leaves dragon fire on the ground (" + fire + " blocks)");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
	}

	/**
	 * The death of a wild dragon (no altar): brought down by a player in the air, it is not dead yet but takes
	 * its last flight ({@code ai/DeathFlight}): straight up {@link DeathFlight#RISE}, and only then dies, the
	 * cocoon closing round it (photographed as vanilla's light bursts out and it floats up).
	 */
	private static void death(int x, int y, int z) {
		command(view(x - 34, y + 22, z, x, y + 22, z), 20);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"],Rotation:[90f,0f]}", x, y + 18, z), 80);
		double[] start = {Double.NaN}, died = {Double.NaN};
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null, "death: a wild dragon in the air");
			if (dragon == null) return;
			start[0] = dragon.getY();
			dragon.hurt(dragon.damageSources().playerAttack(level.players().get(0)), 10000.0F);
			check(dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING && !dragon.isDeadOrDying(),
					"brought down, it takes its last flight before it dies");
		});
		serverUntil(500, level -> {
			List<? extends EnderDragon> found = level.getEntities(EntityType.ENDER_DRAGON, e -> e.getTags().contains("df_ai"));
			if (found.isEmpty() || !found.get(0).isDeadOrDying()) return false;
			died[0] = found.get(0).getY();
			return true;
		});
		STEPS.add(new Step(1, mc -> {
			REPORT.add(String.format(Locale.ROOT, "INFO death: rose %.1f blocks before it died", died[0] - start[0]));
			check(!Double.isNaN(died[0]), "the last flight ends in death");
			check(died[0] - start[0] > DeathFlight.RISE - 3.0, String.format(Locale.ROOT, "it rose before it died (%.1f blocks)", died[0] - start[0]));
		}));
		boolean[] cocoon = {false};
		int[] at = {6, 30, 55, 90, 130, 175};
		int last = 0;
		for (int i = 0; i < at.length; i++) {
			int shot = i;
			STEPS.add(new Step(Math.max(3, at[i] - last - 3), mc -> {
				EnderDragon dragon = null;
				for (var entity : mc.level.entitiesForRendering()) if (entity instanceof EnderDragon d && d.isDeadOrDying()) dragon = d;
				if (dragon == null) return;
				cocoon[0] |= DragonswornDragon.brain(dragon).clock.anim() == DragonAnim.DEATH;
				double yaw = Math.toRadians(DragonswornDragon.brain(dragon).body.yaw(1.0F)), d = shot % 2 == 0 ? 24 : 18;
				// alternately from its left side and from in front, a little below
				double cx = shot % 2 == 0 ? dragon.getX() - Math.cos(yaw) * d : dragon.getX() + Math.sin(yaw) * d;
				double cz = shot % 2 == 0 ? dragon.getZ() - Math.sin(yaw) * d : dragon.getZ() - Math.cos(yaw) * d;
				mc.player.connection.sendCommand(view(cx, dragon.getY() + 1, cz, dragon.getX(), dragon.getY() + 3, dragon.getZ()));
			}));
			STEPS.add(new Step(2, mc -> Screenshot.grab(mc.gameDirectory, String.format(Locale.ROOT, "df-death-%d.png", shot),
					mc.getMainRenderTarget(), message -> LOG.info("{}", message.getString()))));
			last = at[i];
		}
		STEPS.add(new Step(40, mc -> check(cocoon[0], "dead, it plays the cocoon")));
	}

	/**
	 * Attacks from the air, where the dragon cannot land by its prey: a husk on a lone 16-block pillar (a
	 * player pillaring up to a crystal) and one hanging in the air (a player on elytra). Each gets the
	 * fly-by bite (it must hurt the husk, and on the pillar knock it off), the hover bite and the hover
	 * breath. Then the wild AI must pick such an attack by itself at each of them, and never try to land.
	 */
	private static void air(int x, int y, int z) {
		int top = y + 16, fx = x + 90, fy = y + 30;
		command(view(x - 34, y + 20, z - 10, x, top, z), 40);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", x, y, z, x, top - 1, z), 2);
		// with its AI (a knock must move it), slowed to a standstill so it stays on its pillar
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {PersistenceRequired:1b,Tags:[\"df_prey\"],"
				+ "attributes:[{id:\"minecraft:generic.max_health\",base:500.0}],Health:500f,"
				+ "active_effects:[{id:\"minecraft:slowness\",amplifier:10b,duration:-1,show_particles:0b}]}", x, top, z), 2);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Tags:[\"df_flier\"],"
				+ "attributes:[{id:\"minecraft:generic.max_health\",base:500.0}],Health:500f}", fx, fy, z), 2);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"],Rotation:[180f,0f]}", x, y + 24, z + 70), 60);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null && !prey(level).isEmpty() && !flier(level).isEmpty(), "air: a dragon, a husk on a pillar and a husk in the air");
			if (dragon == null || prey(level).isEmpty() || flier(level).isEmpty()) return;
			DragonBrain brain = DragonswornDragon.brain(dragon);
			check(!brain.airborne(prey(level).get(0)) && brain.airborne(flier(level).get(0)), "air: the husk on the pillar stands, the other is in the air");
			int[] site = new LandingSite(brain.grid()).find(x + 0.5, z + 0.5, 8, 17, 12, dragon.getX(), dragon.getZ());
			check(site == null || Math.abs(site[1] - top) > 5, "air: nowhere to land by the husk on the pillar");
		});
		Vec3[] pillar = {new Vec3(x + 0.5, top, z + 0.5)}, hanging = {new Vec3(fx + 0.5, fy, z + 0.5)};
		airAttack("flyby-pillar", Showcase::prey, pillar, (dragon, husk) -> FlybyBitePhase.start(dragon, husk), DragonAnim.GLIDE_BITE, true);
		airAttack("hoverbite-pillar", Showcase::prey, pillar, (dragon, husk) -> HoverAttackPhase.start(dragon, husk, HoverAttackPhase.Mode.BITE), DragonAnim.HOVER_BITE, false);
		airAttack("hoverbreath-pillar", Showcase::prey, pillar, (dragon, husk) -> HoverAttackPhase.start(dragon, husk, HoverAttackPhase.Mode.BREATH), DragonAnim.HOVER_BREATH, false);
		airAttack("flyby-air", Showcase::flier, hanging, (dragon, husk) -> FlybyBitePhase.start(dragon, husk), DragonAnim.GLIDE_BITE, false);
		airAttack("hoverbite-air", Showcase::flier, hanging, (dragon, husk) -> HoverAttackPhase.start(dragon, husk, HoverAttackPhase.Mode.BITE), DragonAnim.HOVER_BITE, false);
		airAttack("hoverbreath-air", Showcase::flier, hanging, (dragon, husk) -> HoverAttackPhase.start(dragon, husk, HoverAttackPhase.Mode.BREATH), DragonAnim.HOVER_BREATH, false);
		airChoice("pillar", Showcase::prey, pillar, Set.of(DragonPhases.FLYBY_BITE, DragonPhases.HOVER_ATTACK, DragonPhases.BREATH_PASS, DragonPhases.SNATCH));
		airChoice("air", Showcase::flier, hanging, Set.of(DragonPhases.FLYBY_BITE, DragonPhases.HOVER_ATTACK));
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
		command("kill @e[tag=df_flier]", 2);
	}

	private static List<? extends Husk> flier(ServerLevel level) {
		return level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_flier"));
	}

	/** Puts the husk back where it belongs, healed and still, and the dragon in the air 70 blocks off, roaming. */
	private static Husk reset(ServerLevel level, java.util.function.Function<ServerLevel, List<? extends Husk>> husks, Vec3 at, boolean dragonToo) {
		EnderDragon dragon = aiDragon(level);
		List<? extends Husk> found = husks.apply(level);
		if (dragon == null || found.isEmpty()) return null;
		Husk husk = found.get(0);
		husk.teleportTo(at.x, at.y, at.z);
		husk.setDeltaMovement(Vec3.ZERO);
		husk.setHealth(husk.getMaxHealth());
		husk.clearFire();
		if (dragonToo) {
			// a fresh roam (setting the phase it is in already would keep whatever it was hunting)
			dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
			dragon.getPhaseManager().setPhase(DragonPhases.ROAM);
			dragon.teleportTo(at.x - 10, at.y + 10, at.z + 70);
			dragon.setDeltaMovement(new Vec3(0.0, 0.0, -0.8));
		}
		return husk;
	}

	/**
	 * One attack, started by hand at the husk: it must play its animation and hurt the husk (and, when
	 * {@code knock}, knock it off its pillar). Frames are grabbed from beside the husk while it plays.
	 */
	private static void airAttack(String name, java.util.function.Function<ServerLevel, List<? extends Husk>> husks, Vec3[] at,
			java.util.function.BiPredicate<EnderDragon, Husk> start, DragonAnim anim, boolean knock) {
		float[] health = new float[2];
		boolean[] started = new boolean[1], played = new boolean[1];
		double[] moved = new double[1];
		String[] phase = {""};
		server(level -> {
			Husk husk = reset(level, husks, at[0], true);
			EnderDragon dragon = aiDragon(level);
			started[0] = husk != null && start.test(dragon, husk);
			if (husk != null) health[0] = health[1] = husk.getHealth();
			if (dragon != null) phase[0] = dragon.getPhaseManager().getCurrentPhase().getPhase().toString();
		});
		int max = 700, end = STEPS.size() + max;
		int[] frames = new int[1];
		for (int i = 0; i < max; i++) {
			STEPS.add(new Step(1, mc -> {
				mc.getSingleplayerServer().executeBlocking(() -> {
					ServerLevel level = mc.getSingleplayerServer().overworld();
					EnderDragon dragon = aiDragon(level);
					List<? extends Husk> found = husks.apply(level);
					if (dragon == null || found.isEmpty()) {
						index = end;
						return;
					}
					Husk husk = found.get(0);
					DragonBrain brain = DragonswornDragon.brain(dragon);
					played[0] |= brain.action() == anim;
					health[1] = Math.min(health[1], husk.getHealth());
					moved[0] = Math.max(moved[0], Math.hypot(husk.getX() - at[0].x, husk.getZ() - at[0].z));
					// over once the attack's phase is
					if (!dragon.getPhaseManager().getCurrentPhase().getPhase().toString().equals(phase[0])) index = end;
				});
				EnderDragon dragon = clientAiDragon(mc);
				if (dragon == null) return;
				// beside the husk, the dragon in view behind it
				Vec3 h = at[0];
				Vec3 away = new Vec3(dragon.getX() - h.x, 0.0, dragon.getZ() - h.z);
				Vec3 side = away.lengthSqr() > 1e-4 ? new Vec3(-away.z, 0.0, away.x).normalize() : new Vec3(1.0, 0.0, 0.0);
				mc.player.connection.sendCommand(view(h.x + side.x * 22 - away.normalize().x * 6, h.y + 6, h.z + side.z * 22 - away.normalize().z * 6,
						(h.x + dragon.getX()) / 2, (h.y + dragon.getY()) / 2 + 1, (h.z + dragon.getZ()) / 2));
				if (DragonswornDragon.brain(dragon).action() == anim && frames[0] < 30 && dragon.tickCount % 3 == 0) {
					Screenshot.grab(mc.gameDirectory, String.format(Locale.ROOT, "df-%s-%02d.png", name, frames[0]++), mc.getMainRenderTarget(),
							message -> LOG.info("{}", message.getString()));
				}
			}));
		}
		server(level -> {
			check(started[0], name + ": the attack starts");
			check(played[0], name + ": it plays " + anim.name().toLowerCase(Locale.ROOT));
			check(health[1] < health[0], String.format(Locale.ROOT, "%s: the husk is hurt (%.0f of %.0f health lost)", name, health[0] - health[1], health[0]));
			if (knock) check(moved[0] > 1.5, String.format(Locale.ROOT, "%s: the hit knocks it off its pillar (%.1f blocks)", name, moved[0]));
		});
	}

	/**
	 * The wild AI's own choice: the husk hurts the dragon (so it is the target) and the dragon must attack
	 * it with one of {@code allowed} (or its fireball barrage), without ever trying to land.
	 */
	private static void airChoice(String name, java.util.function.Function<ServerLevel, List<? extends Husk>> husks, Vec3[] at, Set<EnderDragonPhase<?>> allowed) {
		Set<String> seen = new LinkedHashSet<>();
		boolean[] landing = new boolean[1], chose = new boolean[1];
		server(level -> {
			Husk husk = reset(level, husks, at[0], true);
			EnderDragon dragon = aiDragon(level);
			if (husk == null || dragon == null) return;
			// still on its pillar for the choice (nothing shoves a mob without AI off it)
			husk.setNoAi(true);
			DragonswornDragon.brain(dragon).hurtBy(level.damageSources().mobAttack(husk), 2);
		});
		serverUntil(900, level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null) return true;
			var phase = dragon.getPhaseManager().getCurrentPhase();
			seen.add(phase.getPhase().toString().replaceAll(" .*", ""));
			boolean lands = phase.getPhase() == DragonPhases.GROUND_APPROACH || DragonswornDragon.brain(dragon).onGround();
			if (lands && !landing[0] && !husks.apply(level).isEmpty()) {
				Husk husk = husks.apply(level).get(0);
				REPORT.add(String.format(Locale.ROOT, "INFO air choice at the %s husk: it lands, the husk at %.1f %.1f %.1f (placed at %.1f %.1f %.1f)",
						name, husk.getX(), husk.getY(), husk.getZ(), at[0].x, at[0].y, at[0].z));
			}
			landing[0] |= lands;
			chose[0] = allowed.contains(phase.getPhase()) || phase instanceof RoamPhase roam && !roam.idle();
			if (chose[0] && !husks.apply(level).isEmpty()) {
				Husk husk = husks.apply(level).get(0);
				REPORT.add(String.format(Locale.ROOT, "INFO air choice at the %s husk: %s%s, %.0f blocks off, target airborne %b",
						name, phase.getPhase(), phase instanceof RoamPhase roam ? " " + roam.hunt() : "", husk.distanceTo(dragon),
						DragonswornDragon.brain(dragon).airborne(husk)));
			}
			return chose[0];
		});
		server(level -> {
			REPORT.add("INFO air choice at the " + name + " husk: phases " + seen);
			check(chose[0], "air: the wild dragon attacks the " + name + " husk from the air by itself");
			check(!landing[0], "air: and never tries to land by it");
		});
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
			if (i < 12) closeUp(19, String.format(Locale.ROOT, "snatch-reach-%02d", i), 1, 10.0);    // the legs thrown forward
			if (i >= 12 && i % 3 == 0) closeUp(0, String.format(Locale.ROOT, "snatch-close-%02d", i), 1, 9.0);
			if (i >= 10) talonView(String.format(Locale.ROOT, "snatch-talon-%02d", i));
			track(String.format(Locale.ROOT, "snatch-%02d", i), 5, 18, 2);
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				if (dragon == null || prey(level).isEmpty()) return;
				Grip.Hold hold = DragonswornDragon.brain(dragon).prey.hold();
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
			check(dragon != null && !prey(level).isEmpty() && PreyHold.carrier(prey(level).get(0)) == null
					&& DragonswornDragon.brain(dragon).prey.hold() == Grip.Hold.NONE, "and dropped it");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);

		// the seize, on the ground: a husk with plenty of health to chew on
		int sx = gx + 100;
		command(view(sx - 20, y + 8, gz, sx, y + 3, gz), 40);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"df_prey\"],"
				+ "attributes:[{id:\"minecraft:generic.max_health\",base:200.0}],Health:200f}", sx, y, gz + 8), 2);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"df_helper\"]}", sx - 6, y, gz - 6), 2);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"],Rotation:[180f,0f]}", sx, y, gz), 6);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon != null && !prey(level).isEmpty()) GroundFightPhase.start(dragon, prey(level).get(0));
		});
		STEPS.add(new Step(70, mc -> {}));      // its roar on landing
		// a seize is a bite first: the husk steps out of the jaws' way once the aim is committed, and nothing is held
		int[] after = {-1};
		boolean[] dodgeHeld = new boolean[1];
		float[] health = new float[1];
		server(level -> {
			if (fight(level) != null) fight(level).seizeNext();
		});
		serverUntil(160, level -> {
			GroundFightPhase fight = fight(level);
			if (fight == null || prey(level).isEmpty()) return true;
			if (after[0] < 0 && fight.seizeCommitted()) {
				Husk husk = prey(level).get(0);
				health[0] = husk.getHealth();
				husk.teleportTo(husk.getX() + 9.0, husk.getY(), husk.getZ());
				after[0] = 0;
			}
			if (after[0] >= 0) dodgeHeld[0] |= DragonswornDragon.brain(aiDragon(level)).prey.hold() != Grip.Hold.NONE;
			// the jaws close REACTION_TICKS after the commit: a few more and it is decided
			return after[0] >= 0 && ++after[0] > 15;
		});
		server(level -> {
			check(after[0] > 15, "the dragon goes for a seize");
			check(!dodgeHeld[0] && !prey(level).isEmpty() && PreyHold.carrier(prey(level).get(0)) == null,
					"dodged, its jaws close on nothing: the husk is not taken");
			check(!prey(level).isEmpty() && prey(level).get(0).getHealth() == health[0], "nor hurt by it");
			// back in front of it, and the next bite is a seize again
			EnderDragon dragon = aiDragon(level);
			if (dragon == null || prey(level).isEmpty() || fight(level) == null) return;
			// the dragon's head points along (sin yaw, -cos yaw), against vanilla's look vector
			double yaw = Math.toRadians(dragon.getYRot());
			prey(level).get(0).teleportTo(dragon.getX() + Math.sin(yaw) * 8.0, y, dragon.getZ() - Math.cos(yaw) * 8.0);
			fight(level).seizeNext();
		});
		serverUntil(140, level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null || prey(level).isEmpty() || DragonswornDragon.brain(dragon).prey.hold() != Grip.Hold.JAW) return false;
			health[0] = prey(level).get(0).getHealth();
			return true;
		});
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null && DragonswornDragon.brain(dragon).prey.hold() == Grip.Hold.JAW,
					"a bite that lands takes the husk in its jaws");
		});
		for (int i = 0; i < 10; i++) closeUp(0, String.format(Locale.ROOT, "seize-%02d", i), 3, 7.0);
		for (int i = 0; i < 3; i++) feetView(String.format(Locale.ROOT, "seize-feet-%d", i));
		STEPS.add(new Step(20, mc -> {}));
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = prey(level);
			check(dragon != null && DragonswornDragon.brain(dragon).prey.hold() == Grip.Hold.JAW && !prey.isEmpty() && PreyHold.carrier(prey.get(0)) == dragon,
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
			check(dragon != null && DragonswornDragon.brain(dragon).prey.hold() == Grip.Hold.NONE && !prey(level).isEmpty() && PreyHold.carrier(prey(level).get(0)) == null,
					"a blow at its head makes it drop the husk");
		});
		// a hold left alone ends with the prey flung off
		STEPS.add(new Step(40, mc -> {}));
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null || prey(level).isEmpty() || fight(level) == null) return;
			// the dragon's head points along (sin yaw, -cos yaw), against vanilla's look vector
			double yaw = Math.toRadians(dragon.getYRot());
			prey(level).get(0).teleportTo(dragon.getX() + Math.sin(yaw) * 8.0, y, dragon.getZ() - Math.cos(yaw) * 8.0);
			fight(level).seizeNext();
		});
		serverUntil(140, level -> aiDragon(level) != null && DragonswornDragon.brain(aiDragon(level)).prey.hold() == Grip.Hold.JAW);
		for (int i = 0; i < 6; i++) closeUp(0, String.format(Locale.ROOT, "seize-shake-%02d", i), 20, 7.0);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = prey(level);
			check(dragon != null && DragonswornDragon.brain(dragon).prey.hold() == Grip.Hold.NONE && !prey.isEmpty() && PreyHold.carrier(prey.get(0)) == null,
					"left alone, the hold ends: the husk is flung off");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
		command("kill @e[tag=df_helper]", 2);
	}

	/**
	 * A close look at what the AI dragon holds: the camera {@code distance} blocks off to the dragon's
	 * left of its held prey (or of part {@code part} when it holds nothing, or is still reaching), level with it.
	 */
	private static void closeUp(int part, String name, int ticks, double distance) {
		STEPS.add(new Step(Math.max(ticks, 3), mc -> {
			EnderDragon dragon = clientAiDragon(mc);
			if (dragon == null) return;
			var held = DragonswornDragon.brain(dragon).prey.holding() ? DragonswornDragon.brain(dragon).prey.prey() : null;
			var at = held != null ? held.position().add(0.0, held.getBbHeight() / 2.0, 0.0) : dragon.getSubEntities()[part].position();
			double yaw = Math.toRadians(DragonswornDragon.brain(dragon).body.yaw(1.0F));
			double cx = at.x - Math.cos(yaw) * distance + Math.sin(yaw) * 2.0, cz = at.z - Math.sin(yaw) * distance - Math.cos(yaw) * 2.0;
			mc.player.connection.sendCommand(view(cx, at.y + 1.5, cz, at.x, at.y, at.z));
		}));
		STEPS.add(new Step(2, mc -> Screenshot.grab(mc.gameDirectory, "df-" + name + ".png", mc.getMainRenderTarget(),
				message -> LOG.info("{}", message.getString()))));
	}

	/** The prey in the talons, from below and to the dragon's right, a little ahead: the gripping foot and its toes. */
	private static void talonView(String name) {
		boolean[] holding = new boolean[1];
		STEPS.add(new Step(3, mc -> {
			EnderDragon dragon = clientAiDragon(mc);
			holding[0] = dragon != null && DragonswornDragon.brain(dragon).prey.holding();
			if (!holding[0]) return;
			var held = DragonswornDragon.brain(dragon).prey.prey();
			var at = held.position().add(0.0, held.getBbHeight() / 2.0, 0.0);
			// the dragon's head points along (sin yaw, -cos yaw); its right is (cos yaw, sin yaw)
			double yaw = Math.toRadians(DragonswornDragon.brain(dragon).body.yaw(1.0F));
			double cx = at.x + Math.cos(yaw) * 7.0 + Math.sin(yaw) * 1.0, cz = at.z + Math.sin(yaw) * 7.0 - Math.cos(yaw) * 1.0;
			mc.player.connection.sendCommand(view(cx, at.y - 4.0, cz, at.x, at.y, at.z));
		}));
		STEPS.add(new Step(2, mc -> {
			if (holding[0]) Screenshot.grab(mc.gameDirectory, "df-" + name + ".png", mc.getMainRenderTarget(), message -> LOG.info("{}", message.getString()));
		}));
	}

	/** The hind feet on the ground, at their height, from the dragon's right: the toes. */
	private static void feetView(String name) {
		STEPS.add(new Step(3, mc -> {
			EnderDragon dragon = clientAiDragon(mc);
			if (dragon == null) return;
			double yaw = Math.toRadians(DragonswornDragon.brain(dragon).body.yaw(1.0F));
			// the hips are about two blocks behind the dragon's position
			double hx = dragon.getX() - Math.sin(yaw) * 1.5, hz = dragon.getZ() + Math.cos(yaw) * 1.5;
			mc.player.connection.sendCommand(view(hx + Math.cos(yaw) * 5.0, dragon.getY() + 0.8, hz + Math.sin(yaw) * 5.0, hx, dragon.getY() + 0.3, hz));
		}));
		STEPS.add(new Step(2, mc -> Screenshot.grab(mc.gameDirectory, "df-" + name + ".png", mc.getMainRenderTarget(),
				message -> LOG.info("{}", message.getString()))));
	}

	/**
	 * The config screen (whichever library builds it: {@code -Ddragonsworn.configScreen} picks one) photographed
	 * at its top and scrolled down, and the server config written to the config folder with every option.
	 */
	private static void configScreen() {
		STEPS.add(new Step(5, mc -> {
			Path file = mc.gameDirectory.toPath().resolve("config").resolve(DragonConfig.FILE);
			boolean written = Files.exists(file);
			REPORT.add((written ? "PASS" : "FAIL") + " config file written: " + file);
			if (!written) failed = true;
			REPORT.add("INFO config screen: " + ConfigScreens.library());
			mc.setScreen(ConfigScreens.create(null));
		}));
		STEPS.add(new Step(10, mc -> Screenshot.grab(mc.gameDirectory, "df-config-0.png", mc.getMainRenderTarget(),
				message -> LOG.info("{}", message.getString()))));
		for (int i = 1; i <= 3; i++) {
			int shot = i;
			STEPS.add(new Step(5, mc -> {
				if (mc.screen != null) mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, 0.0, -12.0 * shot);
			}));
			STEPS.add(new Step(5, mc -> Screenshot.grab(mc.gameDirectory, "df-config-" + shot + ".png", mc.getMainRenderTarget(),
					message -> LOG.info("{}", message.getString()))));
		}
		STEPS.add(new Step(2, mc -> {
			boolean open = mc.screen != null;
			REPORT.add((open ? "PASS" : "FAIL") + " config screen open: " + (open ? mc.screen.getClass().getSimpleName() : "none"));
			if (!open) failed = true;
			mc.setScreen(null);
		}));
	}

	/** The AI dragon's ground fight, or null when it is not fighting on the ground. */
	private static GroundFightPhase fight(ServerLevel level) {
		EnderDragon dragon = aiDragon(level);
		return dragon != null && dragon.getPhaseManager().getCurrentPhase() instanceof GroundFightPhase fight ? fight : null;
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
			check(DragonswornDragon.brain(dragon).context() == DragonBrain.Context.WILD, "a summoned dragon is wild");
			check(dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.HOVERING, "it left its hover to roam");
		});

		// the ground assault: the husk stands still, so the aimed blows land
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}", lx - 40, y, lz + 10), 5);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
			check(dragon != null && !prey.isEmpty() && DragonswornDragon.brain(dragon).tryGroundAssault(prey.get(0)),
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
			check(phases.contains("DragonswornGroundApproach"), "it flew down to the landing site");
			check(phases.contains("DragonswornGroundFight"), "it landed and fought on the ground");
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
			REPORT.add(String.format(Locale.ROOT, "INFO landing: speed %.2f, site %d %d %d", DragonswornDragon.brain(dragon).horizontalSpeed(), sx, sy, sz));
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
				DragonBrain brain = DragonswornDragon.brain(dragon);
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
			check(dragon != null && DragonswornDragon.brain(dragon).onGround(), "it stands on the ground after the landing");
			double off = Math.hypot(stop[0] - site[0] - 0.5, stop[1] - site[2] - 0.5);
			check(off < 2.5, String.format(Locale.ROOT, "it skidded to a stop on the site (%.1f blocks off)", off));
		});
		command("kill @e[tag=df_ai]", 2);
	}

	/**
	 * The lazy fight: a wild dragon lands to fight a husk on foot. Hurt too much there, it takes a break in
	 * the air (the hits are given to its brain as the husk's: vanilla lets only players hurt a dragon);
	 * hurt again up there, it lands beside the husk once more and fights on, on the ground.
	 */
	private static void stance(int sx, int y, int sz) {
		command(view(sx - 40, y + 20, sz, sx, y + 12, sz), 40);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", sx, y + 20, sz), 20);
		// invulnerable: a snatch's drop must not end the test; with its AI, so a drop falls to the ground
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {Invulnerable:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}", sx - 30, y, sz + 10), 5);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
			check(dragon != null && !prey.isEmpty() && DragonswornDragon.brain(dragon).tryGroundAssault(prey.get(0)),
					"stance: there is room to land beside the husk");
		});
		boolean[] landed = new boolean[3], lifted = new boolean[1];
		for (int i = 0; i < 30; i++) {
			track(String.format(Locale.ROOT, "stance-land-%02d", i), 10, 30);
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				landed[0] |= dragon != null && DragonswornDragon.brain(dragon).onGround();
			});
		}
		// the husk hurts it, a fifth of its health and more: up for a break
		STEPS.add(new Step(80, mc -> {}));
		server(level -> stanceHit(level, CombatStance.GROUND_LIMIT + 0.01));
		// watched for less than the shortest break (CombatStance.BREAK_MIN)
		for (int i = 0; i < 12; i++) {
			track(String.format(Locale.ROOT, "stance-up-%02d", i), 10, 34);
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				lifted[0] |= dragon != null && dragon.getPhaseManager().getCurrentPhase().getPhase() == DragonPhases.LIFTOFF;
			});
		}
		Set<String> breakPhases = new LinkedHashSet<>();
		for (int i = 0; i < 5; i++) {
			track(String.format(Locale.ROOT, "stance-air-%02d", i), 20, 34);
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				if (dragon == null) return;
				breakPhases.add(dragon.getPhaseManager().getCurrentPhase().getPhase().toString().replaceAll(" .*", ""));
				landed[1] |= DragonswornDragon.brain(dragon).onGround();
			});
		}
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(landed[0], "stance: it landed to fight the husk on foot");
			check(lifted[0], "stance: hurt too much on the ground, it took off");
			REPORT.add("INFO stance: phases on the break: " + breakPhases);
			check(!landed[1] && dragon != null && !DragonswornDragon.brain(dragon).stance.grounded(), "stance: it stayed in the air for its break");
		});
		// hurt in the air: back down to fight on foot
		server(level -> stanceHit(level, CombatStance.AIR_LIMIT + 0.01));
		for (int i = 0; i < 50; i++) {
			track(String.format(Locale.ROOT, "stance-down-%02d", i), 10, 34);
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				landed[2] |= dragon != null && DragonswornDragon.brain(dragon).onGround();
			});
		}
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
			if (dragon != null && !prey.isEmpty()) {
				Husk husk = prey.get(0);
				REPORT.add(String.format(Locale.ROOT, "INFO stance: at the end %s, %s; husk %.0f %.0f %.0f on ground %b, %.0f blocks off",
						dragon.getPhaseManager().getCurrentPhase().getPhase(), DragonswornDragon.brain(dragon).stance.stance(),
						husk.getX(), husk.getY(), husk.getZ(), husk.onGround(), husk.distanceTo(dragon)));
			}
			check(landed[2], "stance: hurt in the air, it landed again to fight on the ground");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
	}

	/** The husk hurts the AI dragon, {@code fraction} of its health, as far as its brain knows. */
	private static void stanceHit(ServerLevel level, double fraction) {
		EnderDragon dragon = aiDragon(level);
		List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
		if (dragon == null || prey.isEmpty()) return;
		var source = level.damageSources().mobAttack(prey.get(0));
		DragonBrain brain = DragonswornDragon.brain(dragon);
		brain.hurtBy(source, 2);
		brain.hit(source, (float) (fraction * dragon.getMaxHealth()));
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
				if (DragonswornDragon.brain(dragon).onGround()) {
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
			check(phases.contains("DragonswornGroundFight"), "it came down on its own with nobody to hunt");
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
			int fire = dragonFire(level, bx, y, bz - 14, 8);
			check(fire > 0, "the stream leaves dragon fire where it splashes (" + fire + " blocks)");
		});
		shoot(view(bx - 10, y + 5, bz - 4, bx, y, bz - 14), "breath-dragon-fire", 2);
		command("kill @e[tag=df_breath]", 2);
		command("kill @e[tag=df_target]", 2);
		command(String.format(Locale.ROOT, "summon minecraft:dragon_fireball %d %d %d {Motion:[0.0,-0.5,0.0]}", bx, y + 8, bz), 40);
		shoot(view(bx - 8, y + 4, bz - 8, bx, y, bz), "fireball-cloud", 20);
		server(level -> {
			var clouds = level.getEntities(EntityType.AREA_EFFECT_CLOUD, e -> true);
			check(!clouds.isEmpty() && clouds.stream().allMatch(c -> ((AreaEffectCloud) c).getParticle() == BreathParticles.VOID_FLAME),
					"the dragon fireball's cloud burns with void flame (" + clouds.size() + " clouds)");
			int fire = dragonFire(level, bx, y, bz, 3), outer = fire - dragonFire(level, bx, y, bz, 1);
			check(fire > 0 && fire <= 6 && outer == 0,
					"the fireball leaves a little dragon fire, only right where it bursts (" + fire + " blocks, " + outer + " out of the middle)");
		});
		// dragon fire burns three times what fire does: a husk standing in it loses three times what one in
		// vanilla fire beside it does (no armor, which takes a flat bit off each; with AI: a NoAI mob never moves, so it never touches the blocks it stands in)
		int fx = bx + 30;
		command(String.format(Locale.ROOT, "setblock %d %d %d dragonsworn:dragon_fire", fx, y, bz), 1);
		command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:fire", fx + 6, y, bz), 1);
		command(String.format(Locale.ROOT, "summon minecraft:husk %.1f %d %.1f {Silent:1b,PersistenceRequired:1b,Tags:[\"df_target\"],attributes:[{id:\"minecraft:generic.armor\",base:0.0}]}", fx + 0.5, y, bz + 0.5), 1);
		command(String.format(Locale.ROOT, "summon minecraft:husk %.1f %d %.1f {Silent:1b,PersistenceRequired:1b,Tags:[\"df_target\"],attributes:[{id:\"minecraft:generic.armor\",base:0.0}]}", fx + 6.5, y, bz + 0.5), 4);
		server(level -> {
			float[] lost = new float[2];
			for (var husk : level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_target"))) {
				lost[husk.getX() < fx + 3 ? 0 : 1] = husk.getMaxHealth() - husk.getHealth();
			}
			REPORT.add(String.format(Locale.ROOT, "INFO first touch: dragon fire took %.2f health, vanilla fire %.2f", lost[0], lost[1]));
			check(lost[1] > 0.0F && Math.abs(lost[0] / lost[1] - 3.0F) < 0.05F,
					String.format(Locale.ROOT, "dragon fire hurts three times what fire does (%.2f vs %.2f)", lost[0], lost[1]));
		});
		shoot(view(fx - 4, y + 2, bz - 4, fx, y, bz), "dragon-fire", 2);
		command("kill @e[tag=df_target]", 2);
	}

	/** How many dragon fire blocks are within {@code r} (a box) of a point, a few blocks up and down. */
	private static int dragonFire(ServerLevel level, int x, int y, int z, int r) {
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(x - r, y - 3, z - r, x + r, y + 4, z + r)) {
			if (level.getBlockState(pos).is(DragonFire.BLOCK)) n++;
		}
		return n;
	}

	/** Runs on the integrated server's thread and waits for it (so checks happen in script order). */
	private static void server(Consumer<ServerLevel> action) {
		STEPS.add(new Step(1, mc -> mc.getSingleplayerServer().executeBlocking(() -> action.accept(mc.getSingleplayerServer().overworld()))));
	}

	/**
	 * Runs {@code poll} on the server every tick until it returns true, for at most {@code max} ticks: the
	 * rest of the wait is skipped.
	 */
	private static void serverUntil(int max, Predicate<ServerLevel> poll) {
		int end = STEPS.size() + max;
		for (int i = 0; i < max; i++) server(level -> {
			if (poll.test(level)) index = end;
		});
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
			double yaw = Math.toRadians(DragonswornDragon.brain(dragon).body.yaw(1.0F));
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
