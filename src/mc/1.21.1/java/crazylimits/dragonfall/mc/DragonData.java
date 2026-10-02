package crazylimits.dragonfall.mc;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.joml.Vector3f;

/**
 * What the server tells clients about each dragon beyond the vanilla phase: its flight plan (so the
 * wings beat when the dragon is pushed) and its current one-shot action (bite, roar, tail sweep,
 * takeoff), what its head turns to and where a bite or tail strike is aimed. Defined after vanilla's own dragon data, the same way on both sides.
 */
public final class DragonData {
	public static final EntityDataAccessor<Integer> FLIGHT;
	public static final EntityDataAccessor<Integer> ACTION;
	/** The entity the head turns to (its prey, a player close by), -1 for none. */
	public static final EntityDataAccessor<Integer> LOOK;
	/** Counts the roars the server starts in flight: each new value is one (the jaw opens with it). */
	public static final EntityDataAccessor<Integer> VOICE;
	/** Where the bite or tail strike playing is aimed, relative to the dragon's position (NaN: no aim). */
	public static final EntityDataAccessor<Vector3f> STRIKE;
	/** What the talons or jaws hold or reach for, and the prey's id ({@code body/Grip#encode}); 0: nothing. */
	public static final EntityDataAccessor<Integer> GRIP;
	/** Counts the fireballs the server starts charging: each new value is one (the heat glow plays fast). */
	public static final EntityDataAccessor<Integer> FIREBALL;
	/** How it stands on the ground ({@code nav/Foothold}'s ordinal): on all fours, sat up or clinging. */
	public static final EntityDataAccessor<Integer> FOOTHOLD;
	public static final Vector3f NO_STRIKE = new Vector3f(Float.NaN, Float.NaN, Float.NaN);

	static {
		// vanilla's dragon data first, so ours always gets the same ids after it
		EntityDataAccessor<Integer> vanilla = EnderDragon.DATA_PHASE;
		FLIGHT = SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.INT);
		ACTION = SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.INT);
		LOOK = SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.INT);
		VOICE = SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.INT);
		STRIKE = SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.VECTOR3);
		GRIP = SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.INT);
		FIREBALL = SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.INT);
		FOOTHOLD = SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.INT);
		if (FLIGHT.id() <= vanilla.id()) throw new IllegalStateException("Dragonfall dragon data defined too early");
	}

	private DragonData() {}

	/** Defines the data (class initialization); must run before the first dragon is created. */
	public static void init() {}
}
