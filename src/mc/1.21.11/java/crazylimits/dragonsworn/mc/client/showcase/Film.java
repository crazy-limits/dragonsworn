package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.attack.BreathAttack;
import crazylimits.dragonsworn.body.Grip;
import crazylimits.dragonsworn.body.Parts;
import crazylimits.dragonsworn.mc.DragonPhases;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.LevelGrid;
import crazylimits.dragonsworn.mc.PreyHold;
import crazylimits.dragonsworn.mc.breath.DragonFire;
import crazylimits.dragonsworn.mc.phase.BreathPassPhase;
import crazylimits.dragonsworn.mc.phase.GroundApproachPhase;
import crazylimits.dragonsworn.mc.phase.GroundFightPhase;
import crazylimits.dragonsworn.mc.phase.SnatchPhase;
import crazylimits.dragonsworn.nav.BlockGrid;
import crazylimits.dragonsworn.nav.LandingSite;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/**
 * The in-game film: short clips of the dragon on the End island, every tick a frame, for the mod's page.
 * Off unless the JVM runs with {@code -Ddragonsworn.film=true} ({@code ./gradlew :<target>:runClient
 * -Pdragonsworn.film}, or {@code -Pdragonsworn.film=walk,jaws} for some scenes only).
 *
 * <p>It creates a fresh world, goes to the End, ends the vanilla fight (the dragons here are wild ones),
 * levels a pad on the main island between two spires and films one scene after another there: the flight
 * and the running landing, the takeoff, the walk, the stalk, the stream breath, the breath pass, the claws'
 * snatch and the jaws' seize. The player is the prey (in survival, unhurtable): seen in third person by a
 * free camera ({@link FilmCamera}) or through its own eyes. Frames go to
 * {@code <run>/screenshots/film-<scene>-<n>.png}; {@code tools/film_gifs.py} makes them the GIFs.
 */
public final class Film {
	public static final boolean ENABLED = Boolean.getBoolean("dragonsworn.film");
	/** The scenes to film ({@code -Pdragonsworn.film=walk,jaws}); all when empty. */
	private static final List<String> ONLY = Arrays.stream(System.getProperty("dragonsworn.film.only", "").split(","))
			.map(String::trim).filter(s -> !s.isEmpty()).toList();
	private static final TestRun RUN = new TestRun(new TestRun.Setup("Film", "dragonsworn_film", Difficulty.NORMAL,
			true, 40, "film-report.txt", Film::buildScript));

	/** The pad's middle: on the main island between two spires, 70 blocks out at 198 degrees. */
	private static final double ANGLE = Math.toRadians(198.0), RADIUS = 70.0;
	private static final int PAD = 24;
	/** Along the island's rim at the pad (unit, horizontal): the scenes' line. */
	private static final Vec3 ALONG = new Vec3(-Math.sin(ANGLE), 0.0, Math.cos(ANGLE));
	private static int px, py, pz;
	private static final Map<String, Integer> FRAMES = new HashMap<>();
	/** Each clip's scene (what {@code -Pdragonsworn.film=} names). */
	private static final Map<String, String> SCENES = Map.ofEntries(Map.entry("flight", "flight"), Map.entry("landing", "flight"),
			Map.entry("takeoff", "flight"), Map.entry("walk", "walk"), Map.entry("stalk", "stalk"), Map.entry("breath", "breath"),
			Map.entry("breath-first-person", "breath"), Map.entry("breath-pass", "pass"), Map.entry("claw-grab", "claws"),
			Map.entry("claw-grab-first-person", "claws"), Map.entry("jaw-grab", "jaws"), Map.entry("jaw-grab-first-person", "jaws"));

	private Film() {
	}

	public static void tick(Minecraft mc) {
		if (ENABLED) RUN.tick(mc);
	}

	private static boolean wanted(String scene) {
		return ONLY.isEmpty() || scene == null || ONLY.contains(scene);
	}

	private static void buildScript(Minecraft mc) {
		// the last frames of the scenes filmed now (the others' stay, for the GIFs)
		File[] old = new File(mc.gameDirectory, "screenshots").listFiles((dir, name) -> name.matches("film-.+-\\d{4}\\.png")
				&& wanted(SCENES.get(name.substring(5, name.length() - 9))));
		if (old != null) for (File f : old) f.delete();
		px = Mth.floor(Math.cos(ANGLE) * RADIUS);
		pz = Mth.floor(Math.sin(ANGLE) * RADIUS);
		py = onServer(mc, server -> levelPad(server.getLevel(Level.END)));
		REPORT.add(String.format(Locale.ROOT, "INFO the pad: %d %d %d", px, py, pz));

		command("gamerule sendCommandFeedback false", 1);
		command("gamemode spectator", 1);
		command(String.format(Locale.ROOT, "execute in minecraft:the_end run tp @s %d %d %d", px, py + 12, pz), 100);
		// the End fight's dragon: once it is there, the fight is over (else it would adopt the film's dragons)
		STEPS.add(new Step(1, m -> m.options.hideGui = true));
		int[] waited = {0};
		roll(null, 300, m -> {}, level -> !level.getDragons().isEmpty() || ++waited[0] > 290);
		end(level -> {
			var fight = level.getDragonFight();
			for (EnderDragon dragon : List.copyOf(level.getDragons())) {
				if (fight != null) fight.setDragonKilled(dragon);
				dragon.discard();
			}
		});
		STEPS.add(new Step(20, m -> {}));

		if (wanted("flight")) flight();
		if (wanted("walk")) walk();
		if (wanted("stalk")) stalk();
		if (wanted("breath")) {
			breath("breath", false);
			breath("breath-first-person", true);
		}
		if (wanted("pass")) breathPass();
		if (wanted("claws")) {
			claws("claw-grab", false);
			claws("claw-grab-first-person", true);
		}
		if (wanted("jaws")) {
			jaws("jaw-grab", false);
			jaws("jaw-grab-first-person", true);
		}
		reset();
		STEPS.add(new Step(1, m -> REPORT.add("INFO frames: " + FRAMES)));
	}

	// ---------------------------------------------------------------- the scenes

	/** A dragon flies in along the island's rim from over the void and lands running on the pad; then takes off again. */
	private static void flight() {
		reset();
		Vec3 from = pad(-150.0, 30.0);
		summon(from, ALONG);
		STEPS.add(new Step(5, m -> {}));
		boolean[] sent = new boolean[1];
		end(level -> {
			EnderDragon dragon = aiDragon(level);
			int y = new LandingSite(new LevelGrid(level)).fits(px, pz);
			if (dragon == null || y == BlockGrid.NO_GROUND) return;
			GroundApproachPhase.start(dragon, new int[]{px, y, pz}, null);
			sent[0] = true;
		});
		end(level -> check(sent[0], "the dragon is sent to land on the pad"));
		// behind and to the left, above: the wings against the void, then the island coming up
		roll("flight", 240, m -> chase(m, -20.0, -11.0, 6.0, 0.08), level -> landing(level) || grounded(level));
		// beside the runway as it comes in, strikes and skids
		int[] after = {0};
		roll("landing", 220, m -> side(m, 20.0, 3.5, 0.08), level -> grounded(level) && ++after[0] > 50);
		end(level -> check(grounded(level), "it landed on the pad"));
		// the takeoff, from in front and to the left, low: the crouch, the jump, the climb
		Vec3[] eye = new Vec3[1];
		STEPS.add(new Step(1, m -> FilmCamera.off()));
		end(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon != null) dragon.getPhaseManager().setPhase(EnderDragonPhase.TAKEOFF);
		});
		roll("takeoff", 110, m -> {
			EnderDragon dragon = clientAiDragon(m);
			if (dragon == null) return;
			if (eye[0] == null) {
				Vec3 f = ahead(dragon);
				eye[0] = dragon.position().add(f.scale(20.0)).add(right(f).scale(-20.0)).add(0.0, 2.5, 0.0);
			}
			pan(m, eye[0], 0.2);
		}, null);
	}

	/** A dragon walks the length of the pad (to an invisible husk at the far end), the camera low beside it. */
	private static void walk() {
		reset();
		Vec3 husk = pad(20.0, 0.0);
		command(String.format(Locale.ROOT, "summon minecraft:husk %.1f %.1f %.1f {NoAI:1b,Invulnerable:1b,Silent:1b,PersistenceRequired:1b,"
				+ "Tags:[\"df_prey\"],active_effects:[{id:\"minecraft:invisibility\",duration:-1,show_particles:0b}]}", husk.x, husk.y, husk.z), 2);
		summon(pad(-20.0, 0.0), ALONG);
		STEPS.add(new Step(5, m -> {}));
		end(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon != null && !prey(level).isEmpty()) GroundFightPhase.start(dragon, prey(level).get(0), false);
		});
		// ahead of it and to its left, low, turning after it: it walks at the camera and past
		Vec3 eye = pad(14.0, 2.5).add(right(ALONG).scale(-11.0));
		roll(null, 40, m -> pan(m, eye, 0.3), null);
		roll("walk", 260, m -> pan(m, eye, 0.3), level -> {
			EnderDragon dragon = aiDragon(level);
			return dragon == null || prey(level).isEmpty() || dragon.distanceTo(prey(level).get(0)) < 10.0;
		});
	}

	/** First person: a dragon on the ground comes for the player and bites. */
	private static void stalk() {
		reset();
		actor(pad(12.0, 0.0), true);
		summon(pad(-14.0, 0.0), ALONG);
		STEPS.add(new Step(5, m -> {}));
		end(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon != null) GroundFightPhase.start(dragon, player(level), false);
		});
		roll("stalk", 230, m -> gaze(m, 0.3), null);
	}

	/** The stream breath poured at the player: in third person over its shoulder, or through its eyes. */
	private static void breath(String scene, boolean eyes) {
		reset();
		actor(pad(15.0, 0.0), eyes);
		summon(pad(0.0, 0.0), ALONG);
		STEPS.add(new Step(5, m -> {}));
		end(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null) return;
			dragon.getPhaseManager().setPhase(DragonPhases.BREATH_STREAM);
			dragon.getPhaseManager().getPhase(DragonPhases.BREATH_STREAM).setTarget(player(level));
		});
		// through its eyes the fire fills the view soon after it comes: a little of it is enough
		roll(scene, BreathAttack.WINDUP_TICKS + (eyes ? 30 : BreathAttack.STREAM_TICKS + 25), m -> {
			if (eyes) gaze(m, 0.3);
			else across(m, 0.15);
		}, null);
	}

	/** A breath pass over the player, from behind it on the ground. */
	private static void breathPass() {
		reset();
		actor(pad(0.0, 0.0), false);
		summon(pad(-70.0, 26.0), ALONG);
		STEPS.add(new Step(5, m -> {}));
		end(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null && BreathPassPhase.start(dragon, player(level)), "the dragon starts a breath pass at the player");
		});
		roll(null, 500, m -> shoulder(m, 0.15), level -> closing(level, 60.0));
		boolean[] breathed = new boolean[1];
		int[] after = {0};
		roll("breath-pass", 180, m -> shoulder(m, 0.15), level -> {
			EnderDragon dragon = aiDragon(level);
			boolean now = dragon != null && Double.isFinite(BreathPassPhase.breathTicks(dragon, 0.0F));
			breathed[0] |= now;
			return breathed[0] && !now && ++after[0] > 25;
		});
	}

	/** The snatch: the dive at the player, the talons, the climb and the drop. */
	private static void claws(String scene, boolean eyes) {
		reset();
		actor(pad(0.0, 0.0), eyes);
		summon(pad(-70.0, 24.0), ALONG);
		STEPS.add(new Step(5, m -> {}));
		end(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null && SnatchPhase.start(dragon, player(level)), scene + ": the dragon goes for a snatch at the player");
		});
		Consumer<Minecraft> camera = m -> {
			if (eyes) gaze(m, 0.3);
			else carried(m);
		};
		roll(null, 500, camera, level -> closing(level, 55.0));
		boolean[] held = new boolean[1];
		int[] after = {0};
		roll(scene, 330, camera, level -> {
			Grip.Hold hold = hold(level);
			held[0] |= hold == Grip.Hold.TALON;
			return held[0] && hold != Grip.Hold.TALON && ++after[0] > 40;
		});
		end(level -> check(held[0], scene + ": it took the player in its talons"));
	}

	/** The seize: a bite that keeps the player, shaken and chewed, then flung. */
	private static void jaws(String scene, boolean eyes) {
		reset();
		actor(pad(9.0, 0.0), eyes);
		summon(pad(0.0, 0.0), ALONG);
		STEPS.add(new Step(5, m -> {}));
		end(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null) return;
			GroundFightPhase.start(dragon, player(level), false);
			if (fight(level) != null) fight(level).seizeNext();
		});
		boolean[] held = new boolean[1];
		int[] after = {0};
		roll(scene, 300, m -> {
			if (eyes) gaze(m, 0.3);
			else carried(m);
		}, level -> {
			Grip.Hold hold = hold(level);
			held[0] |= hold == Grip.Hold.JAW;
			return held[0] && hold != Grip.Hold.JAW && ++after[0] > 30;
		});
		end(level -> check(held[0], scene + ": it took the player in its jaws"));
	}

	// ---------------------------------------------------------------- set-up

	/** Levels a round pad (end stone, air over it; never a spire's obsidian or bedrock) at the ground's height in its middle. */
	private static int levelPad(ServerLevel end) {
		for (int cx = (px - PAD) >> 4; cx <= (px + PAD) >> 4; cx++) {
			for (int cz = (pz - PAD) >> 4; cz <= (pz + PAD) >> 4; cz++) end.getChunk(cx, cz);
		}
		int y = end.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
		for (int x = px - PAD; x <= px + PAD; x++) {
			for (int z = pz - PAD; z <= pz + PAD; z++) {
				if (Math.hypot(x - px, z - pz) > PAD) continue;
				for (int h = y; h < y + 30; h++) clear(end, new BlockPos(x, h, z), Blocks.AIR.defaultBlockState());
				for (int h = y - 1; h > y - 10; h--) {
					BlockPos at = new BlockPos(x, h, z);
					if (!end.getBlockState(at).isAir()) break;
					clear(end, at, Blocks.END_STONE.defaultBlockState());
				}
			}
		}
		return y;
	}

	private static void clear(ServerLevel end, BlockPos at, BlockState to) {
		BlockState was = end.getBlockState(at);
		if (!was.is(Blocks.OBSIDIAN) && !was.is(Blocks.BEDROCK)) end.setBlock(at, to, 2);
	}

	/** Between scenes: no dragon, husk or fire left, the free camera off, the player a spectator over the pad. */
	private static void reset() {
		STEPS.add(new Step(1, m -> {
			FilmCamera.off();
			m.options.setCameraType(CameraType.FIRST_PERSON);
			m.options.hideGui = true;
		}));
		command("gamemode spectator", 1);
		command(String.format(Locale.ROOT, "tp @s %d %d %d", px, py + 12, pz), 2);
		end(level -> {
			player(level).removeAllEffects();
			player(level).clearFire();
			for (Entity e : level.getAllEntities()) {
				if (e instanceof EnderDragon || e.getType() == EntityType.HUSK || e.getType() == EntityType.AREA_EFFECT_CLOUD
						|| e.getType() == EntityType.ITEM || e.getType() == EntityType.DRAGON_FIREBALL) e.discard();
			}
			for (BlockPos at : BlockPos.betweenClosed(px - PAD - 20, py - 4, pz - PAD - 20, px + PAD + 20, py + 6, pz + PAD + 20)) {
				BlockState s = level.getBlockState(at);
				if (s.is(DragonFire.BLOCK) || s.is(Blocks.FIRE)) level.removeBlock(at, false);
			}
		});
		STEPS.add(new Step(10, m -> {}));
	}

	/**
	 * The player as the prey: in survival, unhurtable (resistance), a sword in hand, standing at {@code at}
	 * facing back along the line; seen through its own eyes (the game's HUD on) or by the free camera.
	 */
	private static void actor(Vec3 at, boolean eyes) {
		command("gamemode survival", 1);
		command("effect give @s minecraft:resistance infinite 255 true", 1);
		command("effect give @s minecraft:saturation infinite 255 true", 1);
		command("item replace entity @s weapon.mainhand with minecraft:diamond_sword", 1);
		// the sword's name shows over the hotbar for a while: gone before the shot
		command(String.format(Locale.ROOT, "tp @s %.1f %d %.1f facing %.1f %d %.1f", at.x, py, at.z, at.x - ALONG.x * 10.0, py + 2, at.z - ALONG.z * 10.0), 50);
		STEPS.add(new Step(1, m -> {
			m.options.hideGui = !eyes;
			if (!eyes) m.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}));
	}

	/** A wild dragon at {@code at} facing along {@code facing} (horizontal unit). */
	private static void summon(Vec3 at, Vec3 facing) {
		// the dragon's head points along (sin yaw, -cos yaw), against vanilla's look vector
		float yaw = (float) Math.toDegrees(Math.atan2(facing.x, -facing.z));
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %.1f %.1f %.1f {Tags:[\"df_ai\"],Rotation:[%.1ff,0f]}", at.x, at.y, at.z, yaw), 1);
	}

	/** A point {@code along} blocks along the scenes' line from the pad's middle, {@code up} over the pad. */
	private static Vec3 pad(double along, double up) {
		return new Vec3(px + 0.5 + ALONG.x * along, py + up, pz + 0.5 + ALONG.z * along);
	}

	// ---------------------------------------------------------------- filming

	/**
	 * Up to {@code max} ticks of a shot: each tick the camera, then (with a {@code scene}) a frame; it ends
	 * early once {@code done} (asked on the server, in the End) says so.
	 */
	private static void roll(String scene, int max, Consumer<Minecraft> camera, Predicate<ServerLevel> done) {
		int end = STEPS.size() + max;
		for (int i = 0; i < max; i++) {
			STEPS.add(new Step(1, mc -> {
				camera.accept(mc);
				if (scene != null) frame(mc, scene);
				if (done != null && onServer(mc, server -> done.test(server.getLevel(Level.END)))) index = end;
			}));
		}
	}

	private static void frame(Minecraft mc, String scene) {
		mc.getToastManager().clear();
		int n = FRAMES.merge(scene, 1, Integer::sum);
		Screenshot.grab(mc.gameDirectory, String.format(Locale.ROOT, "film-%s-%04d.png", scene, n), mc.getMainRenderTarget(), 1, message -> {});
	}

	/**
	 * The free camera on {@code anchor} (see {@link FilmCamera#move}): its eye at {@code anchor + eye}, looking
	 * at {@code anchor + at}; a spectator player waits just behind it (out of the shot).
	 */
	private static void look(Minecraft mc, Vec3 anchor, Vec3 eye, Vec3 at, double ease) {
		if (mc.options.getCameraType().isFirstPerson()) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		FilmCamera.move(anchor, eye, at, ease);
		if (mc.player.isSpectator()) {
			Vec3 behind = FilmCamera.eye(1.0F).subtract(FilmCamera.forward().scale(3.0));
			mc.player.snapTo(behind.x, behind.y - mc.player.getEyeHeight(), behind.z, mc.player.getYRot(), mc.player.getXRot());
			mc.player.setDeltaMovement(Vec3.ZERO);
		}
	}

	/** A camera standing still at {@code eye}, turning after the dragon. */
	private static void pan(Minecraft mc, Vec3 eye, double ease) {
		EnderDragon dragon = clientAiDragon(mc);
		if (dragon != null) look(mc, Vec3.ZERO, eye, focus(dragon), ease);
	}

	/** Behind ({@code back} < 0), to the side ({@code side} < 0: left) and over the dragon, looking ahead of it. */
	private static void chase(Minecraft mc, double back, double side, double up, double ease) {
		EnderDragon dragon = clientAiDragon(mc);
		if (dragon == null) return;
		Vec3 f = ahead(dragon);
		look(mc, dragon.position(), f.scale(back).add(right(f).scale(side)).add(0.0, up, 0.0), f.scale(8.0), ease);
	}

	/** From the dragon's left, {@code distance} off and a little ahead, {@code up} over its feet. */
	private static void side(Minecraft mc, double distance, double up, double ease) {
		EnderDragon dragon = clientAiDragon(mc);
		if (dragon == null) return;
		Vec3 f = ahead(dragon);
		look(mc, dragon.position(), right(f).scale(-distance).add(f.scale(distance * 0.3)).add(0.0, up, 0.0),
				focus(dragon).subtract(dragon.position()), ease);
	}

	/** Over the player's shoulder at the dragon: the player in the foreground, looking at it. */
	private static void shoulder(Minecraft mc, double ease) {
		EnderDragon dragon = clientAiDragon(mc);
		if (dragon == null) return;
		gaze(mc, 0.3);
		Vec3 me = mc.player.position(), to = focus(dragon).subtract(me);
		Vec3 flat = new Vec3(to.x, 0.0, to.z).normalize();
		look(mc, me, flat.scale(-6.5).add(right(flat).scale(2.5)).add(0.0, 2.6, 0.0), new Vec3(0.0, 1.2, 0.0).lerp(to, 0.55), ease);
	}

	/** From the side of the line between the player and the dragon, both in the shot. */
	private static void across(Minecraft mc, double ease) {
		EnderDragon dragon = clientAiDragon(mc);
		if (dragon == null) return;
		gaze(mc, 0.3);
		Vec3 me = mc.player.position(), to = focus(dragon).subtract(me);
		Vec3 flat = new Vec3(to.x, 0.0, to.z).normalize();
		Vec3 middle = new Vec3(to.x * 0.4, 1.5, to.z * 0.4);
		look(mc, me, middle.add(right(flat).scale(16.0)).add(flat.scale(-4.0)).add(0.0, 6.0, 0.0), middle, ease);
	}

	/**
	 * Third person through a grab: over the player's shoulder until it is held, then close on the held
	 * player (in the talons from the dragon's left and below; in the jaws from in front of the head); after
	 * the drop, on the player again.
	 */
	private static void carried(Minecraft mc) {
		EnderDragon dragon = clientAiDragon(mc);
		if (dragon == null) return;
		if (DragonswornDragon.brain(dragon).prey.holding()) {
			Vec3 f = ahead(dragon), held = mc.player.position().add(0.0, 0.6, 0.0);
			boolean jaws = DragonswornDragon.brain(dragon).prey.hold() == Grip.Hold.JAW;
			Vec3 eye = jaws ? right(f).scale(-5.5).add(f.scale(6.0)).add(0.0, 1.4, 0.0)
					: right(f).scale(-9.0).add(f.scale(3.0)).add(0.0, -2.0, 0.0);
			look(mc, held, eye, jaws ? Vec3.ZERO : focus(dragon).subtract(held).scale(0.3), 0.15);
		} else {
			shoulder(mc, 0.15);
		}
	}

	/** The player turns its head toward the dragon (held: looking ahead and down past the talons, or at the head shaking it). */
	private static void gaze(Minecraft mc, double ease) {
		EnderDragon dragon = clientAiDragon(mc);
		if (dragon == null) return;
		Vec3 at = focus(dragon);
		if (DragonswornDragon.brain(dragon).prey.hold() == Grip.Hold.TALON) {
			at = mc.player.getEyePosition().add(ahead(dragon).scale(10.0)).add(0.0, -9.0, 0.0);
		} else if (PreyHold.carrier(mc.player) != null) {
			at = mc.player.getEyePosition().add(ahead(dragon).scale(10.0)).add(0.0, -3.0, 0.0);
		}
		Vec3 d = at.subtract(mc.player.getEyePosition());
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
		float y = mc.player.getYRot() + Mth.wrapDegrees(yaw - mc.player.getYRot()) * (float) ease;
		float p = mc.player.getXRot() + (pitch - mc.player.getXRot()) * (float) ease;
		mc.player.setYRot(y);
		mc.player.setXRot(p);
		mc.player.setYHeadRot(y);
	}

	/** Where to look at the dragon: between its body and its head. */
	private static Vec3 focus(EnderDragon dragon) {
		return dragon.position().add(0.0, 2.5, 0.0).lerp(dragon.getSubEntities()[Parts.HEAD].position(), 0.35);
	}

	/** The way the dragon's head points (horizontal unit): (sin yaw, -cos yaw) of its drawn body. */
	private static Vec3 ahead(EnderDragon dragon) {
		double yaw = Math.toRadians(DragonswornDragon.brain(dragon).body.yaw(1.0F));
		return new Vec3(Math.sin(yaw), 0.0, -Math.cos(yaw));
	}

	/** The right of a horizontal heading. */
	private static Vec3 right(Vec3 f) {
		return new Vec3(-f.z, 0.0, f.x);
	}

	// ---------------------------------------------------------------- server-side questions

	private static void end(Consumer<ServerLevel> action) {
		STEPS.add(new Step(1, mc -> mc.getSingleplayerServer().executeBlocking(() -> action.accept(mc.getSingleplayerServer().getLevel(Level.END)))));
	}

	private static <T> T onServer(Minecraft mc, java.util.function.Function<MinecraftServer, T> task) {
		MinecraftServer server = mc.getSingleplayerServer();
		return server.submit(() -> task.apply(server)).join();
	}

	private static Player player(ServerLevel level) {
		return level.getServer().getPlayerList().getPlayers().get(0);
	}

	private static Grip.Hold hold(ServerLevel level) {
		EnderDragon dragon = aiDragon(level);
		return dragon == null ? Grip.Hold.NONE : DragonswornDragon.brain(dragon).prey.hold();
	}

	private static boolean landing(ServerLevel level) {
		EnderDragon dragon = aiDragon(level);
		return dragon != null && dragon.getPhaseManager().getCurrentPhase() instanceof GroundApproachPhase approach && approach.landing();
	}

	private static boolean grounded(ServerLevel level) {
		EnderDragon dragon = aiDragon(level);
		return dragon != null && DragonswornDragon.brain(dragon).onGround();
	}

	/** The dragon is within {@code distance} of the player (across the ground) and flying toward it. */
	private static boolean closing(ServerLevel level, double distance) {
		EnderDragon dragon = aiDragon(level);
		if (dragon == null) return true;
		Vec3 to = player(level).position().subtract(dragon.position());
		Vec3 v = dragon.getDeltaMovement();
		return Math.hypot(to.x, to.z) < distance && to.x * v.x + to.z * v.z > 0.0;
	}

}
