package crazylimits.dragonsworn.ai;

import crazylimits.dragonsworn.ai.CrystalGuard.Suspect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrystalGuardTest {
	@Test
	void nobodyNearACrystalIsNoThreat() {
		assertEquals(CrystalGuard.NONE, new CrystalGuard().pick(List.of(new Suspect(1, 60, 5), new Suspect(2, 80, 10))));
	}

	@Test
	void goesForWhoeverIsNearestACrystalAndItself() {
		CrystalGuard guard = new CrystalGuard();
		assertEquals(2, guard.pick(List.of(new Suspect(1, 20, 40), new Suspect(2, 5, 40))), "nearest a crystal");
		assertEquals(3, new CrystalGuard().pick(List.of(new Suspect(1, 10, 80), new Suspect(3, 12, 10))), "as near a crystal, and near itself");
	}

	@Test
	void oneAtATime() {
		CrystalGuard guard = new CrystalGuard();
		assertEquals(1, guard.pick(List.of(new Suspect(1, 6, 20), new Suspect(2, 10, 20))));
		// the other comes a little nearer a crystal: it stays on the first
		assertEquals(1, guard.pick(List.of(new Suspect(1, 8, 20), new Suspect(2, 3, 20))));
		// the first gets away from the crystals: on to the next
		assertEquals(2, guard.pick(List.of(new Suspect(1, 60, 20), new Suspect(2, 3, 20))));
	}

	@Test
	void drawnOffByOneRightAtAnotherCrystal() {
		CrystalGuard guard = new CrystalGuard();
		assertEquals(1, guard.pick(List.of(new Suspect(1, 20, 5), new Suspect(2, 23, 40))));
		assertEquals(2, guard.pick(List.of(new Suspect(1, 20, 5), new Suspect(2, 1, 40))), "far nearer another crystal");
		assertEquals(2, guard.current());
	}
}
