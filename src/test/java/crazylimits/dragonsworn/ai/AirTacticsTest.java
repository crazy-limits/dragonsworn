package crazylimits.dragonsworn.ai;

import crazylimits.dragonsworn.ai.AirTactics.Attack;
import crazylimits.dragonsworn.ai.AirTactics.Reach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AirTacticsTest {
	private static final Set<Attack> HOVER = EnumSet.of(Attack.HOVER_BITE, Attack.HOVER_BREATH);

	@Test
	void whereTheTargetIsDecidesTheReach() {
		assertEquals(Reach.AIR, AirTactics.reach(true, true));
		assertEquals(Reach.AIR, AirTactics.reach(true, false));
		assertEquals(Reach.GROUND, AirTactics.reach(false, true));
		assertEquals(Reach.WALL, AirTactics.reach(false, false));
	}

	@Test
	void onOpenGroundItNeverHoversToAttack() {
		for (double roll = 0.0; roll < 1.0; roll += 0.01) {
			for (Attack a : AirTactics.choices(Reach.GROUND, roll)) {
				assertFalse(HOVER.contains(a) || a == Attack.FLYBY_BITE, roll + ": " + a);
			}
		}
	}

	@Test
	void whereItCannotLandItBitesInPassingBreathesAndHovers() {
		Set<Attack> wall = EnumSet.noneOf(Attack.class), air = EnumSet.noneOf(Attack.class);
		for (double roll = 0.0; roll < 1.0; roll += 0.01) {
			wall.add(AirTactics.choices(Reach.WALL, roll).get(0));
			air.add(AirTactics.choices(Reach.AIR, roll).get(0));
		}
		assertTrue(wall.containsAll(EnumSet.of(Attack.FLYBY_BITE, Attack.BREATH_PASS, Attack.HOVER_BITE, Attack.HOVER_BREATH)), wall.toString());
		assertTrue(air.containsAll(EnumSet.of(Attack.FLYBY_BITE, Attack.HOVER_BITE, Attack.HOVER_BREATH)), air.toString());
		// nothing that needs ground under the prey at a target in the air
		assertFalse(air.contains(Attack.SNATCH) || air.contains(Attack.BREATH_PASS) || air.contains(Attack.CHARGE), air.toString());
	}

	@Test
	void everyChoiceFallsBackToOneThatAlwaysStarts() {
		for (Reach reach : Reach.values()) {
			for (double roll = 0.0; roll < 1.0; roll += 0.01) {
				List<Attack> choices = AirTactics.choices(reach, roll);
				assertFalse(choices.isEmpty());
				assertEquals(Attack.BARRAGE, choices.get(choices.size() - 1), reach + " " + roll);
			}
		}
	}

	@Test
	void theOddsCoverEveryAttack() {
		// each attack of a repertoire is somebody's first choice
		assertEquals(Attack.SNATCH, AirTactics.choices(Reach.GROUND, 0.0).get(0));
		assertEquals(Attack.BARRAGE, AirTactics.choices(Reach.GROUND, 0.999).get(0));
		assertEquals(Attack.FLYBY_BITE, AirTactics.choices(Reach.AIR, 0.0).get(0));
		assertEquals(Attack.BARRAGE, AirTactics.choices(Reach.AIR, 0.999).get(0));
	}
}
