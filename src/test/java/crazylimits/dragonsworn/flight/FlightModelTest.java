package crazylimits.dragonsworn.flight;

import crazylimits.dragonsworn.flight.FlightModel.Force;
import crazylimits.dragonsworn.flight.FlightModel.Mode;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

class FlightModelTest {
	private final SplittableRandom random = new SplittableRandom(1);

	@Test
	void climbingBeatsAndDescendingGlides() {
		FlightModel m = new FlightModel();
		assertEquals(Mode.FLY, m.update(0, 0.2, 0, 1.0, Force.NONE, random).mode());
		// the beat in progress finishes before the glide starts
		assertEquals(Mode.FLY, m.update(5, -0.2, 0, 1.0, Force.NONE, random).mode());
		assertEquals(Mode.GLIDE, m.update(Math.round(FlightModel.BEAT_TICKS), -0.2, 0, 1.0, Force.NONE, random).mode());
	}

	@Test
	void theWingsGoStillOnlyOnceTheStrokeUnderWayIsDone() {
		FlightModel m = new FlightModel();
		assertEquals(0, m.ticksToChange(0), "gliding: any time");
		m.update(0, 0.2, 0, 1.0, Force.NONE, random);
		long beat = Math.round(FlightModel.BEAT_TICKS);
		assertEquals(beat - 5, m.ticksToChange(5), "a beat: to its end");
		assertEquals(0, m.ticksToChange(beat), "on the boundary");
		FlightModel p = new FlightModel();
		p.update(0, 0, 0, 1.2, Force.NONE, random);
		FlightModel.Plan push = p.update(1, 0, 0, 0.85, Force.NONE, random);
		assertEquals(Mode.PUSH, push.mode());
		assertEquals(Math.round(push.flaps() * FlightModel.PUSH_TICKS) - 9, p.ticksToChange(10), "a push: to its last stroke's end");
	}

	@Test
	void sharpTurnsBeatAndGentleTurnsGlide() {
		FlightModel m = new FlightModel();
		assertEquals(Mode.GLIDE, m.update(0, 0, 1.5, 1.2, Force.NONE, random).mode());
		assertEquals(Mode.FLY, m.update(1, 0, 4.5, 1.2, Force.NONE, random).mode());
	}

	@Test
	void levelFlightPushesOnlyWhenSlow() {
		FlightModel m = new FlightModel();
		assertEquals(Mode.GLIDE, m.update(0, 0, 0, 1.2, Force.NONE, random).mode());
		FlightModel.Plan push = m.update(1, 0, 0, 0.85, Force.NONE, random);
		assertEquals(Mode.PUSH, push.mode());
		// it cannot be cut off, and no push follows straight away
		assertEquals(Mode.PUSH, m.update(20, 0, 0, 1.2, Force.NONE, random).mode());
		long done = 1 + Math.round(push.flaps() * FlightModel.PUSH_TICKS);
		assertEquals(Mode.GLIDE, m.update(done, 0, 0, 0.85, Force.NONE, random).mode());
	}

	@Test
	void slowFlightBeatsToStayUp() {
		FlightModel m = new FlightModel();
		assertEquals(Mode.FLY, m.update(0, 0, 0, 0.4, Force.NONE, random).mode());
		// beating continuously when nearly still, the beats hold the height on average
		double net = 0;
		for (long t = 0; t < Math.round(FlightModel.BEAT_TICKS); t++) net += m.lift(t, 0.05);
		assertEquals(0.0, net / FlightModel.BEAT_TICKS, 0.003);
		// gliding that slowly it would drop
		FlightModel glide = new FlightModel();
		assertTrue(glide.lift(0, 0.05) < -0.015);
		assertEquals(0.0, glide.lift(0, 1.2));
	}

	@Test
	void turningBleedsSpeed() {
		assertEquals(1.0, FlightModel.turnDrag(0));
		assertTrue(FlightModel.turnDrag(-3) < 1.0);
		assertEquals(1.0 - FlightModel.TURN_BRAKE, FlightModel.turnDrag(20), 1e-9);
	}

	@Test
	void thrustComesOnlyFromDownstrokes() {
		FlightModel m = new FlightModel();
		m.update(0, 0.2, 0, 1.0, Force.NONE, random);
		double total = 0, max = 0;
		int zero = 0;
		for (long t = 0; t < FlightModel.BEAT_TICKS; t++) {
			double thrust = m.thrust(t);
			total += thrust;
			max = Math.max(max, thrust);
			if (thrust == 0) zero++;
		}
		double upstroke = 1.0 - (Wingbeat.DOWNSTROKE_END - Wingbeat.DOWNSTROKE_START);
		assertTrue(zero >= Math.floor(FlightModel.BEAT_TICKS * upstroke), "the upstroke gives nothing: " + zero);
		assertEquals(FlightModel.FLY_THRUST, max, 0.01);
		assertTrue(total > 0.15, "a beat accelerates");
		FlightModel glide = new FlightModel();
		assertEquals(0.0, glide.thrust(10));
	}

	@Test
	void hoverIsForcedAndSequencesChange() {
		FlightModel m = new FlightModel();
		FlightModel.Plan a = m.update(0, 0, 0, 0, Force.HOVER, random);
		assertEquals(Mode.HOVER, a.mode());
		FlightModel.Plan b = m.update(Math.round(FlightModel.BEAT_TICKS), 0.2, 0, 1, Force.NONE, random);
		assertEquals(Mode.FLY, b.mode());
		assertNotEquals(a.sequence(), b.sequence());
	}

	@Test
	void plansEncode() {
		FlightModel.Plan p = new FlightModel.Plan(Mode.PUSH, 2, 99999);
		assertEquals(p, FlightModel.Plan.decode(p.encode()));
	}
}
