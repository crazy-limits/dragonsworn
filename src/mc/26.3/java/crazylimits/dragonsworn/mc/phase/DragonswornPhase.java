package crazylimits.dragonsworn.mc.phase;

import crazylimits.dragonsworn.flight.FlightModel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Extra hooks Dragonsworn's own phases give the flight code. */
public interface DragonswornPhase {
	/** How the wings must work right now; NONE lets the flight model choose. */
	default FlightModel.Force flightForce() {
		return FlightModel.Force.NONE;
	}

	/** What a hovering dragon turns to face (its prey), or null: it faces where it drifts. */
	@Nullable
	default Vec3 hoverLook() {
		return null;
	}

	/** An attack in progress: the dragon does not roar through it. */
	default boolean attacks() {
		return false;
	}

	/** Whether movement is blocked by terrain (it flies around it) in this phase. */
	default boolean collides() {
		return true;
	}
}
