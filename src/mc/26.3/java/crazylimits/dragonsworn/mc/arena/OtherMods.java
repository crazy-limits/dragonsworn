package crazylimits.dragonsworn.mc.arena;

import crazylimits.dragonsworn.Dragonsworn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether another mod rebuilds the same piece of the End's island (YUNG's Better End Island mixes into
 * {@code EndSpikeFeature.placeSpike} and {@code EndPlatformFeature.createEndPlatform} just as we do): any mixin
 * from outside Dragonsworn's own configs applied to the class means ours steps aside and lets it (or vanilla) build.
 */
public final class OtherMods {
	private static final Logger LOG = LoggerFactory.getLogger("dragonsworn/arena");
	private static final Map<Class<?>, Boolean> SEEN = new ConcurrentHashMap<>();

	private OtherMods() {}

	/** True if a mod other than Dragonsworn mixes into {@code target} (looked up once, logged once). */
	public static boolean rebuild(Class<?> target) {
		return SEEN.computeIfAbsent(target, OtherMods::lookUp);
	}

	private static boolean lookUp(Class<?> target) {
		ClassInfo info = ClassInfo.fromCache(target.getName().replace('.', '/'));
		if (info == null) return false;
		for (IMixinInfo mixin : info.getAppliedMixins()) {
			String config = mixin.getConfig().getName();
			if (config.startsWith(Dragonsworn.MOD_ID + ".")) continue;
			LOG.info("{} also changes {} ({}): Dragonsworn leaves it to that mod", config, target.getSimpleName(), mixin.getClassName());
			return true;
		}
		return false;
	}
}
