package crazylimits.dragonsworn.mc;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.joml.Vector3f;

/**
 * What the server tells clients about each dragon beyond the vanilla phase: its flight plan (so the
 * wings beat when the dragon is pushed) and its current one-shot action (bite, roar, tail sweep,
 * takeoff), what its head turns to and where a bite or tail strike is aimed. Defined after vanilla's own dragon data, the same way on
 * both sides, by the dragon's own class ({@code EnderDragonMixin}).
 */
public final class DragonData {
	public static EntityDataAccessor<Integer> FLIGHT;
	public static EntityDataAccessor<Integer> ACTION;
	/** The entity the head turns to (its prey, a player close by), -1 for none. */
	public static EntityDataAccessor<Integer> LOOK;
	/** Counts the roars the server starts in flight: each new value is one (the jaw opens with it). */
	public static EntityDataAccessor<Integer> VOICE;
	/** Where the bite or tail strike playing is aimed, relative to the dragon's position (NaN: no aim). */
	public static EntityDataAccessor<Vector3f> STRIKE;
	/** What the talons or jaws hold or reach for, and the prey's id ({@code body/Grip#encode}); 0: nothing. */
	public static EntityDataAccessor<Integer> GRIP;
	/** Counts the fireballs the server starts charging: each new value is one (the heat glow plays fast). */
	public static EntityDataAccessor<Integer> FIREBALL;
	/** How it stands on the ground ({@code nav/Foothold}'s ordinal): on all fours, sat up or clinging. */
	public static EntityDataAccessor<Integer> FOOTHOLD;
	/** What it stands on ({@code nav/Surface.Face}'s ordinal): the ground, or a wall by the way its face looks. */
	public static EntityDataAccessor<Integer> SURFACE;
	public static final Vector3f NO_STRIKE = new Vector3f(Float.NaN, Float.NaN, Float.NaN);

	private DragonData() {}

	/**
	 * Checks the data is defined (by {@code EnderDragonMixin} as the dragon's class initializes: synced data
	 * NeoForge accepts only from the entity's own class), after vanilla's, so it gets the same ids on both
	 * sides. Must run before the first dragon is created.
	 */
	public static void init() {
		EntityDataAccessor<Integer> vanilla = EnderDragon.DATA_PHASE;
		if (FLIGHT == null || FLIGHT.id() <= vanilla.id()) throw new IllegalStateException("Dragonsworn dragon data not defined after vanilla's");
	}
}
