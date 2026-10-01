package crazylimits.dragonfall.mc;

import crazylimits.dragonfall.Dragonfall;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.Map;

/**
 * The dragon's own sounds, cut from vanilla's by {@code tools/sounds.py}: the roar, one swing of the
 * wings, a heavy step on hard ground. {@code DragonVoice} times them to the animations. They play at
 * volume 1 or less, so each is heard as far as its fixed range (also its {@code attenuation_distance} in
 * sounds.json) and a roar can fade. The loaders register {@link #ALL}: the server plays a few too.
 */
public final class DragonSounds {
	public static final SoundEvent ROAR = event("roar", 96.0F);
	public static final SoundEvent WING = event("wing", 64.0F);
	public static final SoundEvent STEP = event("step", 40.0F);

	public static final Map<ResourceLocation, SoundEvent> ALL = Map.of(
			ROAR.getLocation(), ROAR,
			WING.getLocation(), WING,
			STEP.getLocation(), STEP);

	private DragonSounds() {}

	private static SoundEvent event(String name, float range) {
		return SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(Dragonfall.MOD_ID, "entity.ender_dragon." + name), range);
	}
}
