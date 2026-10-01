package crazylimits.dragonfall.mc.phase;

import crazylimits.dragonfall.flight.FlightModel;

/** Extra hooks Dragonfall's own phases give the flight code. */
public interface DragonfallPhase {
	/** How the wings must work right now; NONE lets the flight model choose. */
	default FlightModel.Force flightForce() {
		return FlightModel.Force.NONE;
	}

	/** Whether movement is blocked by terrain (it flies around it) in this phase. */
	default boolean collides() {
		return true;
	}
}
