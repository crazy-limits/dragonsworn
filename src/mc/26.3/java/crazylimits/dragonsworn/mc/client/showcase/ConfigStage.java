package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.mc.client.config.ConfigScreens;
import net.minecraft.client.Screenshot;

import java.nio.file.Files;
import java.nio.file.Path;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/** Stage {@code config}: the config screen opens and the file is written. */
final class ConfigStage {
	private ConfigStage() {
	}


	/**
	 * The config screen (whichever library builds it: {@code -Ddragonsworn.configScreen} picks one) photographed
	 * at its top and scrolled down, and the server config written to the config folder with every option.
	 */
	static void configScreen() {
		STEPS.add(new Step(5, mc -> {
			Path file = mc.gameDirectory.toPath().resolve("config").resolve(DragonConfig.FILE);
			boolean written = Files.exists(file);
			REPORT.add((written ? "PASS" : "FAIL") + " config file written: " + file);
			if (!written) failed = true;
			REPORT.add("INFO config screen: " + ConfigScreens.library());
			mc.gui.setScreen(ConfigScreens.create(null));
		}));
		STEPS.add(new Step(10, mc -> Screenshot.grab(mc.gameDirectory, "df-config-0.png", mc.gameRenderer.mainRenderTarget(), 1,
				message -> LOG.info("{}", message.getString()))));
		for (int i = 1; i <= 3; i++) {
			int shot = i;
			STEPS.add(new Step(5, mc -> {
				if (mc.gui.screen() != null) mc.gui.screen().mouseScrolled(mc.gui.screen().width / 2.0, mc.gui.screen().height / 2.0, 0.0, -12.0 * shot);
			}));
			STEPS.add(new Step(5, mc -> Screenshot.grab(mc.gameDirectory, "df-config-" + shot + ".png", mc.gameRenderer.mainRenderTarget(), 1,
					message -> LOG.info("{}", message.getString()))));
		}
		STEPS.add(new Step(2, mc -> {
			boolean open = mc.gui.screen() != null;
			REPORT.add((open ? "PASS" : "FAIL") + " config screen open: " + (open ? mc.gui.screen().getClass().getSimpleName() : "none"));
			if (!open) failed = true;
			mc.gui.setScreen(null);
		}));
	}
}
