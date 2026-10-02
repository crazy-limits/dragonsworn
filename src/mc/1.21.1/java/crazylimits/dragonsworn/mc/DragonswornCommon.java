package crazylimits.dragonsworn.mc;

import com.mojang.logging.LogUtils;
import crazylimits.dragonsworn.config.DragonConfig;
import org.slf4j.Logger;

import java.nio.file.Path;

/** Loader-independent setup, run on both sides at mod init (before any dragon exists). */
public final class DragonswornCommon {
	private static final Logger LOG = LogUtils.getLogger();
	private static Path configDir;

	private DragonswornCommon() {}

	public static void init() {
		// Entities size their synced data when they are created, before defineSynchedData runs: the
		// dragon's extra data must be defined before the first dragon exists.
		DragonData.init();
		DragonPhases.register();
	}

	/**
	 * Reads the server config ({@link DragonConfig}) from the loader's config folder: at mod init, as a server
	 * starts and on {@code /reload}. What is wrong with the file is logged; the AI runs on whatever it gets.
	 */
	public static void loadConfig(Path dir) {
		configDir = dir;
		reloadConfig();
	}

	/** Reads the config again from the folder given last (a server start, {@code /reload}). */
	public static void reloadConfig() {
		if (configDir == null) return;
		Path file = configDir.resolve(DragonConfig.FILE);
		for (String problem : DragonConfig.load(file)) LOG.warn("[Dragonsworn] {}: {}", file.getFileName(), problem);
	}
}
