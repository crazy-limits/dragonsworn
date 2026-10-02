package crazylimits.dragonfall.ai;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CombatStanceTest {
	private final Random random = new Random(7);

	@Test
	void aFightStartsOnTheGroundAndTooMuchDamageSendsItUp() {
		CombatStance stance = new CombatStance();
		assertTrue(stance.grounded());
		stance.hurt(CombatStance.GROUND_LIMIT * 0.6, random);
		assertTrue(stance.grounded(), "a few hits are borne");
		stance.hurt(CombatStance.GROUND_LIMIT * 0.6, random);
		assertEquals(CombatStance.Stance.AIR, stance.stance());
	}

	@Test
	void theBreakInTheAirEndsWithTime() {
		CombatStance stance = new CombatStance();
		stance.overwhelmed(random);
		assertFalse(stance.grounded());
		for (int t = 0; t < CombatStance.BREAK_MIN - 1; t++) stance.tick(true);
		assertFalse(stance.grounded(), "a break lasts at least BREAK_MIN");
		for (int t = 0; t <= CombatStance.BREAK_MAX - CombatStance.BREAK_MIN; t++) stance.tick(true);
		assertTrue(stance.grounded(), "and at most BREAK_MAX");
	}

	@Test
	void hurtInTheAirItLandsAgain() {
		CombatStance stance = new CombatStance();
		stance.hurt(CombatStance.GROUND_LIMIT, random);
		assertFalse(stance.grounded());
		stance.hurt(CombatStance.AIR_LIMIT * 0.5, random);
		assertFalse(stance.grounded(), "what it lost on the ground does not count up here");
		stance.hurt(CombatStance.AIR_LIMIT * 0.5, random);
		assertTrue(stance.grounded());
		stance.hurt(CombatStance.GROUND_LIMIT * 0.9, random);
		assertTrue(stance.grounded(), "back down, the tally starts again");
	}

	@Test
	void withNobodyToFightItCalmsDown() {
		CombatStance stance = new CombatStance();
		stance.hurt(CombatStance.GROUND_LIMIT * 0.9, random);
		for (int t = 0; t < CombatStance.CALM_TICKS; t++) stance.tick(false);
		stance.hurt(CombatStance.GROUND_LIMIT * 0.5, random);
		assertTrue(stance.grounded(), "the old damage was forgotten");
	}
}
