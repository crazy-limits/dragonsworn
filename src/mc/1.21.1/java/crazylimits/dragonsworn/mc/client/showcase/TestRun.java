package crazylimits.dragonsworn.mc.client.showcase;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/**
 * One in-game test run, driven from the client tick: from the title screen it creates a fresh flat creative
 * world, queues its script once the player is in ({@link Setup#script}), plays the {@link Script}'s steps
 * one by one, then writes the report and quits the game. An exception anywhere fails the run.
 */
final class TestRun {
	/**
	 * What a run needs: its name (for the log), the world's name and difficulty, whether time and weather
	 * stand still, the ticks to let the world settle before the first step, the report's file name (in the
	 * game directory), and what queues the steps.
	 */
	record Setup(String name, String world, Difficulty difficulty, boolean still, int settleTicks, String report,
			Consumer<Minecraft> script) {
	}

	private final Setup setup;
	/** 0: at the title screen; 1: creating the world; 2: playing the steps; 3: done. */
	private int stage, delay;

	TestRun(Setup setup) {
		this.setup = setup;
	}

	void tick(Minecraft mc) {
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
						setup.script().accept(mc);
						delay = setup.settleTicks();
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
					step.action().accept(mc);
					delay = Math.max(1, step.ticks());
				}
				default -> {}
			}
		} catch (Throwable t) {
			LOG.error("{} failed", setup.name(), t);
			REPORT.add("FAIL exception: " + t);
			failed = true;
			finish(mc);
			stage = 3;
		}
	}

	private void createWorld(Minecraft mc) throws IOException {
		Path saves = mc.gameDirectory.toPath().resolve("saves").resolve(setup.world());
		if (Files.exists(saves)) {
			try (Stream<Path> walk = Files.walk(saves)) {
				walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
			}
		}
		GameRules rules = new GameRules();
		if (setup.still()) {
			rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
			rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
		}
		rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
		LevelSettings settings = new LevelSettings(setup.world(), GameType.CREATIVE, false, setup.difficulty(), true, rules,
				WorldDataConfiguration.DEFAULT);
		mc.createWorldOpenFlows().createFreshLevel(setup.world(), settings, new WorldOptions(1L, false, false),
				registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
						.value().createWorldDimensions(),
				new TitleScreen());
	}

	private void finish(Minecraft mc) {
		REPORT.add(failed ? "RESULT FAIL" : "RESULT PASS");
		try {
			Files.write(mc.gameDirectory.toPath().resolve(setup.report()), REPORT);
		} catch (IOException e) {
			LOG.error("Could not write the {} report", setup.name(), e);
		}
		REPORT.forEach(line -> LOG.info("{}", line));
		mc.stop();
	}
}
