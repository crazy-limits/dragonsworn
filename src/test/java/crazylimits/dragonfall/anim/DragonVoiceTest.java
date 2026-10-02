package crazylimits.dragonfall.anim;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonVoiceTest {
	/** Every cue over {@code seconds} of an animation sampled once a tick, with the time it fell on. */
	private static List<double[]> cues(DragonAnim anim, double seconds, DragonVoice.Cue cue) {
		List<double[]> out = new ArrayList<>();
		for (int tick = 0; tick < seconds * 20; tick++) {
			if (DragonVoice.due(anim, tick / 20.0, (tick + 1) / 20.0) == cue) out.add(new double[] {(tick + 1) / 20.0});
		}
		return out;
	}

	@Test
	void theGrowlWaitsForTheBlendAndTheRearUp() {
		// 6 blend ticks + 0.6 s of rearing: the jaw snaps open 18 ticks after the roar is chosen
		assertEquals(0.9, DragonVoice.ROAR_AT, 1e-9);
	}

	@Test
	void theGrowlSoundsExactlyOncePerRoar() {
		assertEquals(1, cues(DragonAnim.ROAR, 3.0, DragonVoice.Cue.ROAR).size());
		assertEquals(DragonVoice.Cue.ROAR, DragonVoice.due(DragonAnim.ROAR, 0.85, 0.9));
		assertNull(DragonVoice.due(DragonAnim.ROAR, 0.9, 0.95));
	}

	@Test
	void oneSwingPerWingbeatOnTheDownstroke() {
		List<double[]> swings = cues(DragonAnim.FLY, 10 * DragonAnim.FLAP_SECONDS, DragonVoice.Cue.WING);
		assertEquals(10, swings.size());
		for (double[] at : swings) {
			double phase = ((at[0] - DragonAnim.BLEND_TICKS / 20.0) / DragonAnim.FLAP_SECONDS) % 1.0;
			assertTrue(DragonAnim.downstroke(phase) > 0.0, "swing outside the downstroke at phase " + phase);
		}
	}

	@Test
	void aPushSwingsOncePerStrokeThoughItsClockWraps() {
		// the clock of a push restarts every stroke (AnimClock); two strokes, sampled as it reports them
		int swings = 0;
		double before = 0.0;
		for (int tick = 1; tick <= 2 * DragonAnim.PUSH_SECONDS * 20; tick++) {
			double after = (tick / 20.0) % DragonAnim.PUSH_SECONDS;
			if (DragonVoice.due(DragonAnim.FLAP, before, after) == DragonVoice.Cue.WING) swings++;
			before = after;
		}
		assertEquals(2, swings);
	}

	@Test
	void everyFootStepsOncePerWalkCycle() {
		// two hind feet and two hands: four cycles are eight of each
		assertEquals(8, cues(DragonAnim.WALK, 4 * 2.4, DragonVoice.Cue.STEP_HIND).size());
		assertEquals(8, cues(DragonAnim.WALK, 4 * 2.4, DragonVoice.Cue.STEP_FRONT).size());
	}

	@Test
	void theTakeoffPushesWithLegsAndWings() {
		assertEquals(1, cues(DragonAnim.TAKEOFF, DragonAnim.TAKEOFF_SECONDS, DragonVoice.Cue.WING).size());
		assertEquals(1, cues(DragonAnim.TAKEOFF, DragonAnim.TAKEOFF_SECONDS, DragonVoice.Cue.STEP_HIND).size());
	}

	@Test
	void theLandingBrakesStrikesAndPlantsItsHands() {
		assertEquals(1, cues(DragonAnim.LAND, DragonAnim.LAND_SECONDS + 0.5, DragonVoice.Cue.WING).size());
		List<double[]> strike = cues(DragonAnim.LAND, DragonAnim.LAND_SECONDS + 0.5, DragonVoice.Cue.STEP_HIND);
		assertEquals(1, strike.size());
		assertEquals(DragonAnim.LAND_TOUCH_SECONDS + DragonAnim.BLEND_TICKS / 20.0, strike.get(0)[0], 0.051, "with the feet");
		assertEquals(1, cues(DragonAnim.LAND, DragonAnim.LAND_SECONDS + 0.5, DragonVoice.Cue.STEP_FRONT).size());
	}

	@Test
	void stillAnimationsAreSilent() {
		for (DragonAnim anim : new DragonAnim[] {DragonAnim.ATTACK, DragonAnim.IDLE, DragonAnim.GLIDE}) {
			for (DragonVoice.Cue cue : DragonVoice.Cue.values()) assertEquals(0, cues(anim, 4.0, cue).size(), anim + " " + cue);
		}
	}

	@Test
	void aRoarLastsAsLongAsItsClip() {
		DragonVoice.Roar roar = new DragonVoice.Roar();
		roar.start(100, true);
		assertEquals(1.0, roar.volume(100));
		assertEquals(1.0, roar.volume(100 + DragonVoice.ROAR_OPEN_TICKS));
		assertEquals(0.0, roar.volume(100 + DragonVoice.ROAR_OPEN_TICKS + DragonVoice.ROAR_CLOSE_TICKS));
		assertEquals(DragonVoice.ROAR_JAW, roar.jaw(110), 2.0);
		assertEquals(0.0, roar.jaw(100 + DragonVoice.ROAR_OPEN_TICKS + DragonVoice.ROAR_CLOSE_TICKS), 1e-9);
	}

	@Test
	void anAttackFadesTheRoarOutSmoothly() {
		DragonVoice.Roar roar = new DragonVoice.Roar();
		roar.start(0, true);
		roar.fade(10);
		roar.fade(12);   // fading again does not restart the ramp
		double last = 1.0;
		for (int tick = 10; tick <= 10 + DragonVoice.ROAR_FADE_TICKS; tick++) {
			double v = roar.volume(tick);
			assertTrue(v <= last && last - v <= 1.0 / DragonVoice.ROAR_FADE_TICKS + 1e-9, "a step in the fade at " + tick);
			last = v;
		}
		assertEquals(0.0, last);
		assertEquals(0.0, roar.jaw(10 + DragonVoice.ROAR_FADE_TICKS), 1e-9);
	}

	@Test
	void theRoarAnimationKeepsItsOwnJaw() {
		DragonVoice.Roar roar = new DragonVoice.Roar();
		roar.start(0, false);
		assertEquals(0.0, roar.jaw(10));
		assertEquals(1.0, roar.volume(10));
	}

	@Test
	void attacksAreTheBiteTheSweepAndTheBreath() {
		assertTrue(DragonVoice.attacking(DragonAnim.ATTACK));
		assertTrue(DragonVoice.attacking(DragonAnim.TAIL_SWEEP));
		assertTrue(DragonVoice.attacking(DragonAnim.BREATH));
		assertTrue(!DragonVoice.attacking(DragonAnim.ROAR) && !DragonVoice.attacking(DragonAnim.FLY));
	}
}
