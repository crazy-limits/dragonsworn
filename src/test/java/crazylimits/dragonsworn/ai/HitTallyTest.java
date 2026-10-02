package crazylimits.dragonsworn.ai;

import crazylimits.dragonsworn.config.DragonConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HitTallyTest {
	@Test
	void tooManyHitsAtOnce() {
		HitTally tally = new HitTally();
		for (int i = 0; i < DragonConfig.OVERWHELM_HITS.get() - 1; i++) tally.hit(100 + i * 10);
		assertFalse(tally.overwhelmed(130));
		tally.hit(140);
		assertTrue(tally.overwhelmed(140));
		assertFalse(tally.overwhelmed(100 + DragonConfig.OVERWHELM_WINDOW.get() + 1), "the first hit is old by then");
	}

	@Test
	void spreadOutHitsAreBorne() {
		HitTally tally = new HitTally();
		for (int i = 0; i < 10; i++) {
			tally.hit(i * 20);
			assertFalse(tally.overwhelmed(i * 20));
		}
	}

	@Test
	void clears() {
		HitTally tally = new HitTally();
		for (int i = 0; i < DragonConfig.OVERWHELM_HITS.get(); i++) tally.hit(i);
		tally.clear();
		assertFalse(tally.overwhelmed(5));
	}
}
