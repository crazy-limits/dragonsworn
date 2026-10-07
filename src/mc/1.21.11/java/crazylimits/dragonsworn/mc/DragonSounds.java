package crazylimits.dragonsworn.mc;

import crazylimits.dragonsworn.Dragonsworn;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.Map;

/**
 * The dragon's own sounds, cut from vanilla's by {@code tools/sounds.py}: the roar, one swing of the
 * wings, a heavy step on hard ground. {@code DragonVoice} times them to the animations. They play at
 * volume 1 or less, so each is heard as far as its fixed range (also its {@code attenuation_distance} in
 * sounds.json) and a roar can fade. The loaders register {@link #ALL}: the server plays a few too.
 *
 * <p>The attacks' sounds are vanilla's (a ravager's bite, a sweep, the dragon's shot, a blaze's), played
 * through events of the dragon's own (sounds.json points each at vanilla's event), so every sound the
 * dragon makes has a subtitle that says what it does: never "Ravager bites" or "Blaze shoots". These are
 * heard as far as vanilla's, by their volume.
 */
public final class DragonSounds {
	public static final SoundEvent ROAR = fixed("roar", 96.0F);
	public static final SoundEvent WING = fixed("wing", 64.0F);
	public static final SoundEvent STEP = fixed("step", 40.0F);
	/** A bite (on the ground, in flight, hovering). */
	public static final SoundEvent BITE = variable("bite");
	/** The tail strike. */
	public static final SoundEvent TAIL = variable("tail");
	/** The talons closing on prey (the snatch). */
	public static final SoundEvent SNATCH = variable("snatch");
	/** Chewing prey held in the jaws (the seize). */
	public static final SoundEvent CHEW = variable("chew");
	/** Prey thrown from the jaws. */
	public static final SoundEvent FLING = variable("fling");
	/** The void-flame breath bursting out (perched, the pass, hovering). */
	public static final SoundEvent BREATH = variable("breath");
	/** The stream of void flames pouring. */
	public static final SoundEvent FLAMES = variable("flames");
	/** The wing buffet's blast. */
	public static final SoundEvent BUFFET = variable("buffet");
	/** A projectile bouncing off a crystal's rune ward (not the dragon's: its own subtitle all the same). */
	public static final SoundEvent WARD = SoundEvent.createVariableRangeEvent(
			Identifier.fromNamespaceAndPath(Dragonsworn.MOD_ID, "entity.end_crystal.ward"));

	public static final Map<Identifier, SoundEvent> ALL = Map.ofEntries(
			Map.entry(ROAR.location(), ROAR),
			Map.entry(WING.location(), WING),
			Map.entry(STEP.location(), STEP),
			Map.entry(BITE.location(), BITE),
			Map.entry(TAIL.location(), TAIL),
			Map.entry(SNATCH.location(), SNATCH),
			Map.entry(CHEW.location(), CHEW),
			Map.entry(FLING.location(), FLING),
			Map.entry(BREATH.location(), BREATH),
			Map.entry(FLAMES.location(), FLAMES),
			Map.entry(BUFFET.location(), BUFFET),
			Map.entry(WARD.location(), WARD));

	private DragonSounds() {}

	private static SoundEvent fixed(String name, float range) {
		return SoundEvent.createFixedRangeEvent(id(name), range);
	}

	private static SoundEvent variable(String name) {
		return SoundEvent.createVariableRangeEvent(id(name));
	}

	private static Identifier id(String name) {
		return Identifier.fromNamespaceAndPath(Dragonsworn.MOD_ID, "entity.ender_dragon." + name);
	}
}
