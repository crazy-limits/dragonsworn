package crazylimits.dragonsworn.anim;

import crazylimits.dragonsworn.anim.DragonAnimSelector.Choice;
import crazylimits.dragonsworn.flight.FlightModel;
import crazylimits.dragonsworn.flight.Wingbeat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Characterization tests: pin what {@link AnimClock} does now. */
class AnimClockTest {
	private static final double EPS = 1e-9;
	private static final FlightModel.Plan GLIDING = new FlightModel.Plan(FlightModel.Mode.GLIDE, 0, 0);
	private static final Choice IDLE = new Choice(DragonAnim.IDLE, 0);
	private static final Choice ATTACK = new Choice(DragonAnim.ATTACK, 0);

	private static void run(AnimClock clock, Choice choice, FlightModel.Plan plan, double speed, int ticks) {
		for (int i = 0; i < ticks; i++) clock.tick(choice, plan, speed);
	}

	@Test
	void aFreshClockShowsTheGlideWithNothingToBlendFrom() {
		AnimClock clock = new AnimClock();
		assertNull(clock.choice());
		assertEquals(DragonAnim.GLIDE, clock.anim(), "no choice yet shows the glide");
		assertEquals(0.0, clock.seconds());
		assertEquals(0, clock.changes());
		assertNull(clock.from());
		assertEquals(0.0, clock.fromSeconds());
		assertEquals(1.0, clock.blend(0.0F), "nothing to blend from: fully blended");
		assertEquals(0.0, clock.shownSeconds(1.0F), "shown seconds never go below zero");
	}

	@Test
	void theFirstChoiceStartsAtZeroWithoutABlend() {
		AnimClock clock = new AnimClock();
		clock.tick(IDLE, GLIDING, 0);
		assertEquals(IDLE, clock.choice());
		assertEquals(DragonAnim.IDLE, clock.anim());
		assertEquals(0.0, clock.seconds(), "the tick that changes the choice does not advance it");
		assertEquals(1, clock.changes());
		assertNull(clock.from(), "the first choice blends from nothing");
		assertEquals(1.0, clock.blend(0.0F));
	}

	@Test
	void theSameChoiceAdvancesOneTwentiethOfASecondPerTick() {
		AnimClock clock = new AnimClock();
		clock.tick(IDLE, GLIDING, 0);
		run(clock, IDLE, GLIDING, 0, 10);
		assertEquals(0.5, clock.seconds(), EPS);
		assertEquals(1, clock.changes(), "the same choice is no change");
	}

	@Test
	void oneShotsAndLoopsAlikeRunOnPastTheirLength() {
		// the clock neither wraps a loop nor ends a one-shot: only the push (FLAP) is special
		AnimClock clock = new AnimClock();
		clock.tick(ATTACK, GLIDING, 0);
		run(clock, ATTACK, GLIDING, 0, 400);
		assertEquals(DragonAnim.ATTACK, clock.anim(), "a one-shot is not settled into its then() by the clock");
		assertEquals(20.0, clock.seconds(), 1e-6);
		AnimClock loop = new AnimClock();
		loop.tick(IDLE, GLIDING, 0);
		run(loop, IDLE, GLIDING, 0, 400);
		assertEquals(20.0, loop.seconds(), 1e-6, "a loop's seconds are not wrapped");
	}

	@Test
	void theWalkAdvancesWithItsPlaybackSpeed() {
		Choice walk = new Choice(DragonAnim.WALK, 0);
		double keyed = DragonAnim.WALK_BLOCKS_PER_SECOND / 20.0;
		AnimClock clock = new AnimClock();
		clock.tick(walk, GLIDING, keyed * 2);
		run(clock, walk, GLIDING, keyed * 2, 20);
		assertEquals(2.0, clock.seconds(), 1e-6, "twice the keyed speed: two seconds per second");
		AnimClock crawl = new AnimClock();
		crawl.tick(walk, GLIDING, 0.021);
		run(crawl, walk, GLIDING, 0.021, 20);
		assertEquals(0.35, crawl.seconds(), 1e-6, "a crawl still steps, at its clamp");
		run(crawl, walk, GLIDING, 0, 20);
		assertEquals(0.35, crawl.seconds(), 1e-6, "a stalled walk stands mid-stride: no restart, no steps in place");
		AnimClock other = new AnimClock();
		other.tick(IDLE, GLIDING, keyed * 2);
		run(other, IDLE, GLIDING, keyed * 2, 20);
		assertEquals(1.0, other.seconds(), 1e-6, "only the walk follows the ground speed");
	}

	@Test
	void aChangeRemembersWhatShowedAndRestarts() {
		AnimClock clock = new AnimClock();
		clock.tick(IDLE, GLIDING, 0);
		run(clock, IDLE, GLIDING, 0, 30);
		clock.tick(ATTACK, GLIDING, 0);
		assertEquals(DragonAnim.ATTACK, clock.anim());
		assertEquals(0.0, clock.seconds());
		assertEquals(2, clock.changes());
		assertEquals(DragonAnim.IDLE, clock.from());
		assertEquals(1.5, clock.fromSeconds(), EPS);
		assertEquals(1.5 - (DragonAnim.BLEND_TICKS - 1) / 20.0, clock.fromShownSeconds(), EPS,
				"the model showed the old animation BLEND_TICKS - 1 ticks behind");
	}

	@Test
	void aNewKeyWithTheSameAnimationIsAChange() {
		AnimClock clock = new AnimClock();
		clock.tick(new Choice(DragonAnim.ATTACK, 1), GLIDING, 0);
		run(clock, new Choice(DragonAnim.ATTACK, 1), GLIDING, 0, 10);
		clock.tick(new Choice(DragonAnim.ATTACK, 2), GLIDING, 0);
		assertEquals(2, clock.changes());
		assertEquals(0.0, clock.seconds(), "a new key restarts the same animation");
		assertEquals(DragonAnim.ATTACK, clock.from(), "and blends from itself");
		assertEquals(0.5, clock.fromSeconds(), EPS);
	}

	@Test
	void theBlendRisesOverBlendTicksAndHolds() {
		AnimClock clock = new AnimClock();
		clock.tick(IDLE, GLIDING, 0);
		run(clock, IDLE, GLIDING, 0, 5);
		clock.tick(ATTACK, GLIDING, 0);
		assertEquals(0.0, clock.blend(0.0F), EPS);
		assertEquals(0.5 / DragonAnim.BLEND_TICKS, clock.blend(0.5F), 1e-6, "ticks + partialTick are summed as a float");
		for (int k = 1; k <= DragonAnim.BLEND_TICKS + 3; k++) {
			clock.tick(ATTACK, GLIDING, 0);
			assertEquals(Math.min(1.0, (double) k / DragonAnim.BLEND_TICKS), clock.blend(0.0F), 1e-6, "tick " + k);
			assertEquals(Math.min(1.0, (k + 0.25) / DragonAnim.BLEND_TICKS), clock.blend(0.25F), 1e-6, "tick " + k + " + 0.25");
		}
	}

	@Test
	void theModelShowsTheAnimationBlendTicksLate() {
		AnimClock clock = new AnimClock();
		clock.tick(IDLE, GLIDING, 0);
		run(clock, IDLE, GLIDING, 0, 20);
		assertEquals(1.0 - DragonAnim.BLEND_TICKS / 20.0, clock.shownSeconds(0.0F), EPS);
		assertEquals(1.0 + (0.5 - DragonAnim.BLEND_TICKS) / 20.0, clock.shownSeconds(0.5F), EPS);
		AnimClock early = new AnimClock();
		early.tick(IDLE, GLIDING, 0);
		run(early, IDLE, GLIDING, 0, DragonAnim.BLEND_TICKS - 2);
		assertEquals(0.0, early.shownSeconds(0.0F), "still blending in: the first frame");
		assertEquals(0.0, early.shownSeconds(1.0F));
	}

	@Test
	void fromShownSecondsNeverGoesBelowZero() {
		AnimClock clock = new AnimClock();
		clock.tick(IDLE, GLIDING, 0);
		run(clock, IDLE, GLIDING, 0, 2);
		clock.tick(ATTACK, GLIDING, 0);
		assertEquals(0.1, clock.fromSeconds(), EPS);
		assertEquals(0.0, clock.fromShownSeconds());
	}

	@Test
	void aPushRestartsEveryStrokeThenSettlesIntoTheGlide() {
		FlightModel.Plan push = new FlightModel.Plan(FlightModel.Mode.PUSH, 2, 1);
		Choice flap = new Choice(DragonAnim.FLAP, 1);
		AnimClock clock = new AnimClock();
		clock.tick(flap, push, 1);
		run(clock, flap, push, 1, 20);          // 1.0 s: first stroke
		assertEquals(DragonAnim.FLAP, clock.anim());
		assertEquals(1.0, clock.seconds(), 1e-6);
		run(clock, flap, push, 1, 20);          // 2.0 s: second stroke, its clock restarted
		assertEquals(DragonAnim.FLAP, clock.anim());
		assertEquals(2.0 - Wingbeat.PUSH_SECONDS, clock.seconds(), 1e-6);
		run(clock, flap, push, 1, 30);          // 3.5 s: past both strokes
		assertEquals(DragonAnim.GLIDE, clock.anim());
		assertEquals(3.5 - 2 * Wingbeat.PUSH_SECONDS, clock.seconds(), 1e-6, "the glide's seconds count from the end of the strokes");
		assertEquals(flap, clock.choice(), "the choice is still the push");
	}

	@Test
	void aPushOfNoFlapsStillMakesOneStroke() {
		FlightModel.Plan none = new FlightModel.Plan(FlightModel.Mode.PUSH, 0, 1);
		Choice flap = new Choice(DragonAnim.FLAP, 1);
		AnimClock clock = new AnimClock();
		clock.tick(flap, none, 1);
		run(clock, flap, none, 1, 20);
		assertEquals(DragonAnim.FLAP, clock.anim());
		run(clock, flap, none, 1, 20);
		assertEquals(DragonAnim.GLIDE, clock.anim(), "2.0 s is past one 1.6 s stroke");
	}

	@Test
	void theStrokeCountIsReadOnlyWhenTheChoiceChanges() {
		Choice flap = new Choice(DragonAnim.FLAP, 1);
		AnimClock clock = new AnimClock();
		clock.tick(flap, new FlightModel.Plan(FlightModel.Mode.PUSH, 1, 1), 1);
		run(clock, flap, new FlightModel.Plan(FlightModel.Mode.PUSH, 3, 1), 1, 40);
		assertEquals(DragonAnim.GLIDE, clock.anim(), "a later plan's flaps do not lengthen the push");
	}

	@Test
	void aChangeAfterASettledPushBlendsFromTheGlide() {
		FlightModel.Plan push = new FlightModel.Plan(FlightModel.Mode.PUSH, 1, 1);
		Choice flap = new Choice(DragonAnim.FLAP, 1);
		AnimClock clock = new AnimClock();
		clock.tick(flap, push, 1);
		run(clock, flap, push, 1, 40);          // 2.0 s, settled
		clock.tick(new Choice(DragonAnim.GLIDE, 0), GLIDING, 1);
		assertEquals(DragonAnim.GLIDE, clock.from());
		assertEquals(2.0 - Wingbeat.PUSH_SECONDS, clock.fromSeconds(), 1e-6);
	}
}
