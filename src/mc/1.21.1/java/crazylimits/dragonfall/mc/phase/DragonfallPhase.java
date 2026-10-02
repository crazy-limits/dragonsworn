package crazylimits.dragonfall.mc.phase;

import crazylimits.dragonfall.flight.FlightModel;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

/** Extra hooks Dragonfall's own phases give the flight code. */
public interface DragonfallPhase {
	/** How the wings must work right now; NONE lets the flight model choose. */
	default FlightModel.Force flightForce() {
		return FlightModel.Force.NONE;
	}

	/** What a hovering dragon turns to face (its prey), or null: it faces where it drifts. */
	@Nullable
	default Vec3 hoverLook() {
		return null;
	}

	/** Whether movement is blocked by terrain (it flies around it) in this phase. */
	default boolean collides() {
		return true;
	}
}
