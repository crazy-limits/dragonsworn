package crazylimits.dragonfall.nav;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NavTest {
	/** Flat ground at y = 64 (feet at 64), plus solid boxes. */
	static final class World implements BlockGrid {
		final Set<Long> solid = new HashSet<>();
		final Set<Long> water = new HashSet<>();

		void box(int x0, int y0, int z0, int x1, int y1, int z1) {
			for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) solid.add(AirPlanner.key(x, y, z));
		}

		@Override
		public boolean blocked(int x, int y, int z) {
			return y < 64 || solid.contains(AirPlanner.key(x, y, z));
		}

		@Override
		public int ground(int x, int z) {
			if (water.contains(AirPlanner.key(x, 0, z))) return NO_GROUND;
			int y = 64;
			while (blocked(x, y, z) && y < 200) y++;
			return y;
		}
	}

	@Test
	void openSkyIsAStraightLine() {
		AirPlanner air = new AirPlanner(new World());
		List<double[]> path = air.plan(new double[]{0, 100, 0}, new double[]{60, 100, 0}, 500);
		assertEquals(1, path.size());
	}

	@Test
	void flightGoesAroundAPillarInsteadOfThroughIt() {
		World w = new World();
		w.box(25, 64, -6, 35, 140, 6);                       // a tall pillar in the way
		AirPlanner air = new AirPlanner(w);
		double[] from = {0, 100, 0}, to = {60, 100, 0};
		assertFalse(air.lineClear(from, to));
		List<double[]> path = air.plan(from, to, 3000);
		assertFalse(path.isEmpty());
		double[] at = from;
		for (double[] p : path) {
			assertTrue(air.lineClear(at, p), "every leg is clear");
			at = p;
		}
		assertTrue(Math.hypot(at[0] - 60, at[2]) < 6, "it gets there");
	}

	@Test
	void groundPathWalksAroundAWall() {
		World w = new World();
		w.box(10, 64, -8, 11, 70, 8);                        // a wall across the way
		GroundPlanner ground = new GroundPlanner(w);
		List<int[]> path = ground.plan(0, 0, 20, 0, 1.5, 4000);
		assertFalse(path.isEmpty());
		int[] end = path.get(path.size() - 1);
		assertTrue(Math.hypot(end[0] - 20, end[2]) <= 1.5);
		int[] at = {0, 64, 0};
		for (int[] p : path) {
			assertTrue(ground.lineWalkable(at, p));
			at = p;
		}
	}

	@Test
	void theBodyDoesNotSqueezeThroughAGapOnlyItsFeetFit() {
		World w = new World();
		w.box(10, 64, -30, 11, 72, -2);                      // a wall with a 3-block gap at z = -1..1
		w.box(10, 64, 2, 11, 72, 30);
		GroundPlanner ground = new GroundPlanner(w);
		assertEquals(BlockGrid.NO_GROUND, ground.stand(10, 0), "the body is wider than the gap");
		List<int[]> path = ground.plan(0, 0, 20, 0, 1.5, 6000);
		for (int[] p : path) assertFalse(p[0] >= 8 && p[0] <= 13 && Math.abs(p[2]) < 30, "no squeezing through: " + p[0] + "," + p[2]);

		World wide = new World();
		wide.box(10, 64, -30, 11, 72, -4);                   // a 7-block gap: room enough
		wide.box(10, 64, 4, 11, 72, 30);
		GroundPlanner through = new GroundPlanner(wide);
		List<int[]> straight = through.plan(0, 0, 20, 0, 1.5, 6000);
		assertFalse(straight.isEmpty());
		int[] end = straight.get(straight.size() - 1);
		assertTrue(Math.hypot(end[0] - 20, end[2]) <= 1.5, "it walks through the wide gap");
		for (int[] p : straight) assertTrue(Math.abs(p[2]) <= 3, "straight through, not round: " + p[2]);
	}

	@Test
	void aLowStepFitsUnderTheBellyButAWallBesideItDoesNot() {
		World w = new World();
		w.box(2, 64, 0, 2, 64, 0);                           // a single block beside the feet
		GroundPlanner ground = new GroundPlanner(w);
		assertEquals(64, ground.stand(0, 0));
		World post = new World();
		post.box(2, 64, 0, 2, 66, 0);                        // a post where the body is
		assertEquals(BlockGrid.NO_GROUND, new GroundPlanner(post).stand(0, 0));
	}

	@Test
	void groundPathClimbsGentleStepsButNotCliffs() {
		World w = new World();
		w.box(5, 64, -20, 40, 64, 20);                       // one step up: fine
		GroundPlanner ground = new GroundPlanner(w);
		assertTrue(ground.lineWalkable(new int[]{0, 64, 0}, new int[]{10, 65, 0}));
		World cliff = new World();
		cliff.box(5, 64, -40, 40, 68, 40);                   // five blocks straight up
		assertFalse(new GroundPlanner(cliff).lineWalkable(new int[]{0, 64, 0}, new int[]{10, 69, 0}));
	}

	@Test
	void landingNeedsRoomAndGround() {
		World w = new World();
		LandingSite site = new LandingSite(w);
		assertEquals(64, site.fits(0, 0));
		w.box(2, 64, 2, 2, 66, 2);                           // a post under the body
		assertEquals(BlockGrid.NO_GROUND, site.fits(0, 0));
		World lake = new World();
		lake.water.add(AirPlanner.key(3, 0, 0));
		assertEquals(BlockGrid.NO_GROUND, new LandingSite(lake).fits(0, 0), "a foot in the water");
		World overhang = new World();
		overhang.box(-3, 78, -3, 3, 78, 3);                  // a roof above the descent
		assertEquals(BlockGrid.NO_GROUND, new LandingSite(overhang).fits(0, 0));
	}

	@Test
	void landingSiteSearchSkipsWhatDoesNotFit() {
		World w = new World();
		w.box(-30, 64, -30, 30, 90, -1);                     // the whole north half is a mountain
		int[] spot = new LandingSite(w).find(0, 0, 6, 15, 10, 0, 20);
		assertNotNull(spot);
		assertTrue(spot[2] >= 5, "lands south of the mountain: " + spot[2]);
		assertEquals(64, spot[1]);
	}

	@Test
	void aLedgeTooSmallForAllFourIsAnUprightFoothold() {
		// a 3 by 3 platform 10 blocks up a void (the flat world is far below: y < 64 only)
		BlockGrid ledge = new BlockGrid() {
			@Override
			public boolean blocked(int x, int y, int z) {
				return Math.abs(x) <= 1 && Math.abs(z) <= 1 && y >= 60 && y < 74 || y < 40;
			}

			@Override
			public int ground(int x, int z) {
				return Math.abs(x) <= 1 && Math.abs(z) <= 1 ? 74 : 40;
			}
		};
		LandingSite site = new LandingSite(ledge);
		assertEquals(BlockGrid.NO_GROUND, site.fits(0, 0), "no room for all four");
		assertEquals(74, site.fits(0, 0, Foothold.UPRIGHT));
		assertEquals(74, site.fits(0, 0, Foothold.CLING));
		assertEquals(BlockGrid.NO_GROUND, site.fits(1, 1, Foothold.UPRIGHT), "the corner: its feet over the edge");
	}

	@Test
	void aPillarsTopIsOnlyToClingTo() {
		BlockGrid pillar = new BlockGrid() {
			@Override
			public boolean blocked(int x, int y, int z) {
				return x == 0 && z == 0 && y < 74 || y < 40;
			}

			@Override
			public int ground(int x, int z) {
				return x == 0 && z == 0 ? 74 : 40;
			}
		};
		LandingSite site = new LandingSite(pillar);
		assertEquals(BlockGrid.NO_GROUND, site.fits(0, 0, Foothold.UPRIGHT));
		assertEquals(74, site.fits(0, 0, Foothold.CLING));
		// a wall beside it leaves no room for the wings
		BlockGrid walled = new BlockGrid() {
			@Override
			public boolean blocked(int x, int y, int z) {
				return x == 0 && z == 0 && y < 74 || x == 5 && y < 90 || y < 40;
			}

			@Override
			public int ground(int x, int z) {
				return x == 0 && z == 0 ? 74 : x == 5 ? 90 : 40;
			}
		};
		assertEquals(BlockGrid.NO_GROUND, new LandingSite(walled).fits(0, 0, Foothold.CLING));
	}

	@Test
	void aNarrowFootholdIsFoundBesideThePreyAtItsHeight() {
		// the prey on its own pillar at (0, 0), another pillar 5 blocks east, one far too low 5 blocks west
		BlockGrid pillars = new BlockGrid() {
			@Override
			public boolean blocked(int x, int y, int z) {
				return top(x, z) > 0 && y < top(x, z) || y < 40;
			}

			@Override
			public int ground(int x, int z) {
				return top(x, z) > 0 ? top(x, z) : 40;
			}

			int top(int x, int z) {
				if (z != 0) return 0;
				return x == 0 ? 74 : x == 5 ? 75 : x == -5 ? 50 : 0;
			}
		};
		int[] spot = new LandingSite(pillars).near(0.5, 74, 0.5, 4, 7.5, 5.5, -40, 0, Foothold.CLING);
		assertNotNull(spot);
		assertArrayEquals(new int[]{5, 75, 0}, spot);
		assertNull(new LandingSite(pillars).near(0.5, 74, 0.5, 4, 7.5, 5.5, -40, 0, Foothold.UPRIGHT));
	}
}
