package crazylimits.dragonsworn.ai;

import crazylimits.dragonsworn.config.DragonConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrowdTest {
	@AfterEach
	void defaults() {
		DragonConfig.defaults();
	}

	@Test
	void morePlayersMakeItFaster() {
		assertEquals(1.0, Crowd.pace(0, false), 1e-9);
		assertEquals(1.0, Crowd.pace(1, false), 1e-9, "one player: the pace it always had");
		assertTrue(Crowd.pace(2, false) > 1.0);
		assertTrue(Crowd.pace(3, false) > Crowd.pace(2, false));
		assertEquals(DragonConfig.MAX_PACE.get(), Crowd.pace(50, false), 1e-9, "capped");
	}

	@Test
	void itsCrystalsComeFirst() {
		assertEquals(DragonConfig.GUARD_PACE.get(), Crowd.pace(1, true), 1e-9);
		assertEquals(Crowd.pace(50, false), Crowd.pace(50, true), 1e-9, "never slower for guarding");
	}

	@Test
	void offWhenTheServerSaysSo() {
		DragonConfig.PACE_PER_PLAYER.set(0.0);
		DragonConfig.AREA_BIAS.set(0.0);
		assertEquals(1.0, Crowd.pace(8, false), 1e-9);
		assertEquals(1.0, Crowd.areaBias(8), 1e-9);
	}

	@Test
	void cooldownsShrinkWithThePace() {
		Crowd crowd = new Crowd();
		crowd.update(List.of(), false);
		assertEquals(200, crowd.scale(200));
		crowd.update(List.of(new double[]{0, 0, 0}, new double[]{30, 0, 0}, new double[]{-30, 0, 0}), false);
		assertEquals(3, crowd.count());
		assertEquals((int) Math.round(200 / Crowd.pace(3, false)), crowd.scale(200));
		assertTrue(crowd.scale(200) < 200);
	}

	@Test
	void playersBunchedTogetherDrawAreaAttacks() {
		Crowd crowd = new Crowd();
		double r = DragonConfig.GROUP_RADIUS.get();
		crowd.update(List.of(new double[]{0, 64, 0}, new double[]{r * 0.5, 64, 0}, new double[]{0, 64, -r * 0.8}, new double[]{r * 5, 64, 0}), false);
		assertEquals(3, crowd.within(0, 64, 0, r));
		assertEquals(Crowd.areaBias(3), crowd.areaBias(0, 64, 0), 1e-9);
		assertEquals(1.0, crowd.areaBias(r * 5, 64, 0), 1e-9, "alone over there");
		assertTrue(Crowd.areaBias(3) > Crowd.areaBias(2));
	}

	@Test
	void oddsStayOutOfOne() {
		double[] alone = Crowd.odds(0.3, 0.25, 1.0);
		assertArrayEquals(new double[]{0.3, 0.25}, alone, 1e-9);
		double[] group = Crowd.odds(0.3, 0.25, 3.0);
		assertTrue(group[1] > alone[1] && group[0] < alone[0]);
		assertTrue(group[0] + group[1] <= 1.0 + 1e-9);
	}

	@Test
	void aGroupTipsTheAirAttacksToAreaOnes() {
		for (AirTactics.Reach reach : AirTactics.Reach.values()) {
			assertTrue(areaShare(reach, Crowd.areaBias(4)) > areaShare(reach, 1.0), reach.toString());
		}
	}

	/** The share of rolls whose first choice hits an area. */
	private static double areaShare(AirTactics.Reach reach, double bias) {
		int area = 0, n = 1000;
		for (int i = 0; i < n; i++) {
			if (AirTactics.choices(reach, (i + 0.5) / n, bias).get(0).area) area++;
		}
		return area / (double) n;
	}
}
