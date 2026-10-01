package crazylimits.dragonfall.anim;

import crazylimits.dragonfall.flight.FlightModel;
import org.junit.jupiter.api.Test;

import static crazylimits.dragonfall.anim.DragonAnimSelector.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DragonAnimSelectorTest {
	private static final FlightModel.Plan GLIDING = new FlightModel.Plan(FlightModel.Mode.GLIDE, 0, 3);

	private static DragonAnim pick(Kind kind, double speed) {
		return select(kind, null, 0, GLIDING, speed).anim();
	}

	@Test
	void dyingWinsOverEverything() {
		assertEquals(DragonAnim.DEATH, select(Kind.DYING, DragonAnim.ROAR, 1, GLIDING, 1).anim());
	}

	@Test
	void perchedDragonIdlesRoarsAndBreathes() {
		assertEquals(DragonAnim.IDLE, pick(Kind.PERCH_SCANNING, 0));
		assertEquals(DragonAnim.ROAR, pick(Kind.PERCH_ATTACKING, 0));
		assertEquals(DragonAnim.ATTACK, pick(Kind.PERCH_FLAMING, 0));
	}

	@Test
	void theBreathIsNeverAWalkEvenWhileTurning() {
		assertEquals(DragonAnim.BREATH, pick(Kind.PERCH_BREATH, 0.1));
	}

	@Test
	void groundedDragonWalksWhenItMovesAndIdlesWhenItStops() {
		assertEquals(DragonAnim.WALK, pick(Kind.GROUND, 0.1));
		assertEquals(DragonAnim.IDLE, pick(Kind.GROUND, 0));
	}

	@Test
	void anActionPlaysOverTheGroundState() {
		Choice bite = select(Kind.GROUND, DragonAnim.ATTACK, 7, GLIDING, 0.1);
		assertEquals(DragonAnim.ATTACK, bite.anim());
		assertNotEquals(bite, select(Kind.GROUND, DragonAnim.ATTACK, 8, GLIDING, 0.1), "a new bite restarts");
	}

	@Test
	void airborneFollowsTheFlightPlan() {
		assertEquals(DragonAnim.GLIDE, pick(Kind.AIR, 1));
		assertEquals(DragonAnim.FLY, select(Kind.AIR, null, 0, new FlightModel.Plan(FlightModel.Mode.FLY, 0, 4), 1).anim());
		assertEquals(DragonAnim.HOVER, select(Kind.AIR, null, 0, new FlightModel.Plan(FlightModel.Mode.HOVER, 0, 4), 0).anim());
		assertEquals(DragonAnim.FLAP, select(Kind.AIR, null, 0, new FlightModel.Plan(FlightModel.Mode.PUSH, 2, 5), 1).anim());
	}

	@Test
	void actionCodesRoundTrip() {
		int bits = encodeAction(DragonAnim.TAIL_SWEEP, 12345);
		assertEquals(DragonAnim.TAIL_SWEEP, actionAnim(bits));
		assertEquals(12345, actionSequence(bits));
		assertNull(actionAnim(encodeAction(null, 9)));
	}

	@Test
	void walkPlaybackMatchesGroundSpeed() {
		double keyed = DragonAnim.WALK_BLOCKS_PER_SECOND / 20.0;
		assertEquals(1.0, playbackSpeed(DragonAnim.WALK, keyed), 1e-9);
		assertEquals(2.0, playbackSpeed(DragonAnim.WALK, keyed * 2), 1e-9);
		assertEquals(1.0, playbackSpeed(DragonAnim.FLY, 5));
	}

	@Test
	void namesParse() {
		assertEquals(DragonAnim.WALK, DragonAnim.byName("walk"));
		assertEquals(DragonAnim.TAIL_SWEEP, DragonAnim.byName("animation.ender_dragon.tail_sweep"));
		assertNull(DragonAnim.byName("nope"));
	}

	@Test
	void aPushSettlesIntoTheGlide() {
		AnimClock clock = new AnimClock();
		FlightModel.Plan push = new FlightModel.Plan(FlightModel.Mode.PUSH, 1, 1);
		Choice choice = select(Kind.AIR, null, 0, push, 1);
		clock.tick(choice, push, 1);
		assertEquals(DragonAnim.FLAP, clock.anim());
		for (int i = 0; i < Math.ceil(DragonAnim.PUSH_SECONDS * 20) + 1; i++) clock.tick(choice, push, 1);
		assertEquals(DragonAnim.GLIDE, clock.anim());
	}
}
