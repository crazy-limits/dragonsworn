package crazylimits.dragonfall.mc.client;

import crazylimits.dragonfall.anim.DragonAnim;
import crazylimits.dragonfall.anim.DragonDebug;
import crazylimits.dragonfall.body.PoseTrack;
import crazylimits.dragonfall.body.Tail;
import crazylimits.dragonfall.mc.DragonBrain;
import crazylimits.dragonfall.mc.DragonPhases;
import crazylimits.dragonfall.mc.DragonfallDragon;
import crazylimits.dragonfall.mc.breath.BreathParticles;
import crazylimits.dragonfall.mc.breath.BreathStreamPhase;
import crazylimits.dragonfall.mc.phase.GroundFightPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
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
				default -> {
					for (int i = 0; i < 2; i++) shoot(front, name + "-front-" + i, 15);
				}
			}
			boolean flying = anim == DragonAnim.FLY || anim == DragonAnim.FLAP || anim == DragonAnim.GLIDE || anim == DragonAnim.HOVER;
			if (!flying) shoot(side, name + "-side", 6);
			if (anim == DragonAnim.WALK || anim == DragonAnim.IDLE) shoot(top, name + "-top", 6);
		}
		tailCage(x - 60, y, z);
		STEPS.add(new Step(1, mc -> DragonDebug.forcedAnimation = null));
		liveDragon(x, y, z + 150);
		roaming(x, y, z + 300);
		hills(x, y, z + 450);
		breath(x, y, z);
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
		shoot(breathSide, "breath-inhale", 14);   // ~tick 16: embers in the parting jaw
		shoot(breathSide, "breath-stream-0", 16); // stream
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
		List<? extends EnderDragon> found = level.getEntities(EntityType.ENDER_DRAGON, e -> e.getTags().contains("df_ai"));
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
