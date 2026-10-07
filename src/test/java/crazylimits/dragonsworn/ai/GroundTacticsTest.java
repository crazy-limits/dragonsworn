package crazylimits.dragonsworn.ai;

import crazylimits.dragonsworn.ai.GroundTactics.Action;
import crazylimits.dragonsworn.ai.GroundTactics.Decision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GroundTacticsTest {
	private static Decision decide(double distance, double bearing, boolean bite, boolean tail, boolean ready, boolean provoked, double dice) {
		return GroundTactics.decide(distance, bearing, bite, tail, false, ready, false, provoked, dice);
	}

	@Test
	void bitesWhatIsInFrontAndInReach() {
		assertEquals(Action.BITE, decide(5, 10, true, false, true, false, 0.9).action());
		assertEquals(Action.NONE, decide(5, 10, false, false, true, false, 0.9).action(), "out of reach of the jaws");
	}

	@Test
	void oneBlowAtATime() {
		// recovering from a blow: nothing strikes, whatever reaches
		for (double bearing : new double[]{0, 90, 170}) {
			assertEquals(Action.NONE, decide(5, bearing, true, true, false, true, 0.1).action());
		}
	}

	@Test
	void theSideNearTheTailGetsTheTail() {
		assertEquals(Action.TAIL_STRIKE, decide(7, 110, true, true, true, false, 0.1).action());
	}

	@Test
	void theSideNearTheHeadRollsTheDice() {
		assertTrue(GroundTactics.nearerHead(5, 60));
		assertEquals(Action.BITE, decide(5, 60, true, true, true, false, 0.2).action());
		assertEquals(Action.TAIL_STRIKE, decide(5, 60, true, true, true, false, 0.8).action());
		// the picked blow does not reach: the other one does it
		assertEquals(Action.TAIL_STRIKE, decide(5, 60, false, true, true, false, 0.2).action());
		assertEquals(Action.BITE, decide(5, 60, true, false, true, false, 0.8).action());
	}

	@Test
	void behindItTurnsRoundUnlessHurtFromThere() {
		Decision turn = decide(6, 160, true, true, true, false, 0.0);
		assertEquals(Action.NONE, turn.action());
		assertTrue(turn.turn());
		assertFalse(turn.walk(), "turns on the spot");
		assertEquals(Action.TAIL_STRIKE, decide(8, 160, false, true, true, true, 0.0).action());
		Decision blind = decide(3, 170, false, false, true, true, 0.0);
		assertEquals(Action.NONE, blind.action(), "too close behind for the tail, and the wing buffet not in reach: a blind spot");
		assertTrue(blind.turn());
	}

	@Test
	void theWingsCoverTheBlindSpots() {
		// under the chin, close beside the flank, close behind the hips: neither jaws nor tail reach
		for (double bearing : new double[]{0, 80, 170}) {
			assertEquals(Action.WING_BUFFET, GroundTactics.decide(3, bearing, false, false, true, true, false, true, 0.5).action(), "at " + bearing);
			assertEquals(Action.NONE, GroundTactics.decide(3, bearing, false, false, true, false, false, true, 0.5).action(), "recovering, at " + bearing);
		}
		// what the jaws or the tail reach gets them
		assertEquals(Action.BITE, GroundTactics.decide(5, 5, true, false, true, true, false, false, 0.5).action());
		assertEquals(Action.TAIL_STRIKE, GroundTactics.decide(6, 160, false, true, true, true, false, true, 0.5).action());
	}

	@Test
	void outOfReachToTheSideItTurns() {
		Decision d = decide(12, 90, false, false, true, false, 0.0);
		assertEquals(Action.NONE, d.action());
		assertTrue(d.turn());
	}

	@Test
	void walksInFromAfar() {
		Decision far = decide(25, 20, false, false, true, false, 0.0);
		assertTrue(far.walk());
		assertTrue(far.turn());
		assertFalse(decide(25, 5, false, false, true, false, 0.0).turn(), "already facing it");
	}

	@Test
	void roarsAtWhatIsFarAndOutOfReach() {
		assertEquals(Action.ROAR, GroundTactics.decide(20, 5, false, false, false, true, true, false, 0.0).action());
		assertEquals(Action.ROAR, GroundTactics.decide(20, 90, false, false, false, true, true, false, 0.0).action(), "beside it too");
		assertEquals(Action.NONE, GroundTactics.decide(20, 170, false, false, false, true, true, false, 0.0).action(), "not behind it");
		assertEquals(Action.NONE, GroundTactics.decide(8, 5, false, false, false, true, true, false, 0.0).action(), "too close: no roar");
		assertEquals(Action.BITE, GroundTactics.decide(5, 5, true, false, false, true, true, false, 0.0).action(), "a bite first");
	}

	@Test
	void zones() {
		assertEquals(GroundTactics.Zone.FRONT, GroundTactics.zone(-30));
		assertEquals(GroundTactics.Zone.SIDE, GroundTactics.zone(-90));
		assertEquals(GroundTactics.Zone.BEHIND, GroundTactics.zone(150));
	}

	@Test
	void satUpItOnlyBitesAndTurns() {
		for (Foothold foothold : new Foothold[]{Foothold.UPRIGHT, Foothold.CLING}) {
			// beside it, near the tail: no tail strike up there, it turns to face it instead
			Decision side = GroundTactics.decide(foothold, 7, 110, true, true, true, true, true, false, false, 0.1);
			assertEquals(Action.BITE, side.action(), "the jaws reach it: " + foothold);
			Decision tail = GroundTactics.decide(foothold, 7, 110, false, true, true, true, true, false, false, 0.1);
			assertEquals(Action.NONE, tail.action());
			assertTrue(tail.turn());
			// in front and out of reach: no walking off the perch, no roar
			Decision far = GroundTactics.decide(foothold, 20, 20, false, false, false, true, true, true, false, 0.5);
			assertEquals(Action.NONE, far.action());
			assertFalse(far.walk());
			assertTrue(far.turn());
			// hurt from behind: no tail either (and no wing buffet up there, mobbed or not)
			assertEquals(Action.NONE, GroundTactics.decide(foothold, 6, 170, false, true, true, true, true, false, true, 0.5).action());
		}
		assertEquals(Action.ROAR, GroundTactics.decide(Foothold.STAND, 20, 0, false, false, false, false, true, true, false, 0.5).action());
	}

	@Test
	void mobbedItBuffetsFirst() {
		// in front and in reach of the jaws: mobbed, the wings throw them all off instead
		assertEquals(Action.WING_BUFFET, GroundTactics.decide(Foothold.STAND, 5, 10, true, true, true, true, true, false, false, 0.5).action());
		assertEquals(Action.BITE, GroundTactics.decide(Foothold.STAND, 5, 10, true, true, true, false, true, false, false, 0.5).action());
		// still one blow at a time
		assertEquals(Action.NONE, GroundTactics.decide(Foothold.STAND, 5, 10, true, true, true, true, false, false, false, 0.5).action());
	}
}
