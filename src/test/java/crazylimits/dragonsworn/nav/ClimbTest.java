package crazylimits.dragonsworn.nav;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Walls as ground: the faces' frames, the wall seen from a face, hiding places, and where the dragon grips. */
class ClimbTest {
	@Test
	void aWallsFrameTurnsItsUpOutOfTheFaceAndTheWayInUpTheWall() {
		for (Surface.Face face : Surface.Face.values()) {
			if (!face.wall()) continue;
			double[] up = face.toWorld(new double[]{0, 1, 0}, new double[3]);
			assertArrayEquals(face.normal, up, 1e-12, face + ": local up is the face's normal");
			double t = Math.toRadians(face.tilt);
			assertArrayEquals(new double[]{face.nx * Math.sin(t), Math.cos(t), face.nz * Math.sin(t)}, up, 1e-12, face + ": out of the face, as steep as it is");
			// local "into the wall" is up the face (straight up a sheer one): heading that way climbs it
			assertArrayEquals(new double[]{-face.nx * Math.cos(t), Math.sin(t), -face.nz * Math.cos(t)},
					face.toWorld(new double[]{-face.nx, 0, -face.nz}, new double[3]), 1e-12);
			// the yaw of facing the wall is the yaw of climbing it
			float facing = (float) Math.toDegrees(Math.atan2(-face.nx, face.nz));
			assertEquals(0.0, wrap(face.yawOf(0, 1, 0) - facing), 1e-4, face + ": the yaw is kept as it grips the wall");
		}
	}

	@Test
	void wholeBlocksMapOntoWholeBlocks() {
		Random r = new Random(7);
		for (Surface.Face face : Surface.Face.values()) {
			// the floor and the sheer compass walls (diagonal and stepped faces are sampled, not mapped block for block)
			if (face.wall() && (face.tilt != 90.0 || face.nx != 0 && face.nz != 0)) continue;
			for (int i = 0; i < 200; i++) {
				int x = r.nextInt(400) - 200, y = r.nextInt(300) - 64, z = r.nextInt(400) - 200;
				int[] w = face.blockToWorld(x, y, z);
				assertArrayEquals(new int[]{x, y, z}, face.blockToLocal(w[0], w[1], w[2]));
				double[] c = face.toWorld(new double[]{x + 0.5, y + 0.5, z + 0.5}, new double[3]);
				assertArrayEquals(new double[]{w[0] + 0.5, w[1] + 0.5, w[2] + 0.5}, c, 1e-9);
			}
		}
	}

	@Test
	void theTurnOntoAWallEasesAndEndsExactlyOnTheFace() {
		Surface s = new Surface();
		s.tick(Surface.Face.FLOOR);
		assertArrayEquals(new double[]{1, 0, 0, 0, 1, 0, 0, 0, 1}, s.rotation(1.0F), 1e-12);
		double last = 0.0;
		for (int i = 0; i < Surface.TURN_TICKS; i++) {
			s.tick(Surface.Face.SOUTH);
			double w = s.wallness(1.0F);
			assertTrue(w >= last - 1e-9, "it turns one way only");
			assertTrue(s.wallness(0.5F) <= w + 1e-9 && s.wallness(0.5F) >= last - 1e-9, "between ticks it is between them");
			last = w;
		}
		assertFalse(s.turning());
		assertEquals(1.0, s.wallness(1.0F), 1e-9);
		double[] r = s.rotation(1.0F);
		double[] up = Surface.apply(r, new double[]{0, 1, 0}, new double[3]);
		assertArrayEquals(new double[]{0, 0, 1}, up, 1e-9, "the model's up is out of the south face");
		double[] back = Surface.applyInverse(r, Surface.apply(r, new double[]{1, 2, 3}, new double[3]), new double[3]);
		assertArrayEquals(new double[]{1, 2, 3}, back, 1e-9);
		// and back down to the floor
		for (int i = 0; i < Surface.TURN_TICKS; i++) s.tick(Surface.Face.FLOOR);
		assertEquals(0.0, s.wallness(1.0F), 1e-9);
	}

	/** A cliff: solid from x = 10 west, up to y = 100, its face looking east (x = 11 is open air). */
	private static NavTest.World cliff() {
		NavTest.World w = new NavTest.World();
		w.box(-10, 64, -30, 10, 100, 30);
		return w;
	}

	@Test
	void aWallSeenFromItsFaceIsGround() {
		NavTest.World w = cliff();
		Surface.Face east = Surface.Face.EAST;
		// a point on the face, 80 up: its local column
		double[] l = east.toLocal(new double[]{20.0, 80.5, 0.5}, new double[3]);
		BlockGrid grid = SurfaceGrid.around(w, east, l[1]);
		int g = grid.ground((int) Math.floor(l[0]), (int) Math.floor(l[2]));
		double[] at = east.toWorld(new double[]{Math.floor(l[0]) + 0.5, g, Math.floor(l[2]) + 0.5}, new double[3]);
		assertEquals(11.0, at[0], 1e-9, "the foot rests on the face (x = 11)");
		assertEquals(80.5, at[1], 1e-9);
		// above the cliff's top there is no wall to grip
		double[] high = east.toLocal(new double[]{20.0, 105.5, 0.5}, new double[3]);
		assertEquals(BlockGrid.NO_GROUND, grid.ground((int) Math.floor(high[0]), (int) Math.floor(high[2])));
	}

	@Test
	void preyInATunnelHidesAndItsMouthIsWhereTheHeadFits() {
		NavTest.World w = cliff();
		// a tunnel 1 wide, 2 high, dug 6 blocks into the face at y 80
		for (int x = 5; x <= 10; x++) {
			w.solid.remove(AirPlanner.key(x, 80, 0));
			w.solid.remove(AirPlanner.key(x, 81, 0));
		}
		Burrow b = Burrow.find(w, 6.5, 80.0, 0.5);
		assertNotNull(b);
		double[] mouth = b.mouth();
		assertTrue(mouth[0] >= 12.0, "the head fits only outside the face: " + mouth[0]);
		assertTrue(b.depth() >= 6.0, "the prey is deep in: " + b.depth());
		double[] axis = b.axis();
		assertTrue(axis[0] < -0.5, "the way in is west, into the cliff");
		assertTrue(b.reaches(6.5, 80.5, 0.5, 0.0, b.depth(), 0.6), "all the way in, the passage reaches the prey");
		assertFalse(b.reaches(6.5, 80.5, 0.5, 0.0, 2.0, 0.6), "two blocks in it does not");
		// out in front of the cliff, on a ledge, or on the open ground, nobody hides
		assertNull(Burrow.find(w, 15.5, 64.0, 0.5));
		w.box(11, 70, -3, 13, 70, 3);
		assertNull(Burrow.find(w, 12.5, 71.0, 0.5), "a ledge under the open sky");
	}

	@Test
	void itGripsTheCliffBesideATunnelsMouthWithinABiteOfIt() {
		NavTest.World w = cliff();
		for (int x = 5; x <= 10; x++) {
			w.solid.remove(AirPlanner.key(x, 80, 0));
			w.solid.remove(AirPlanner.key(x, 81, 0));
		}
		Burrow b = Burrow.find(w, 6.5, 80.0, 0.5);
		double[] m = b.mouth();
		assertNull(new SurfaceSites(w).near(m[0], m[1], m[2], 3.0, 7.0, 5.0, 4.0, true, 8, 40, 80, 0),
				"a sheer cliff: nothing for its feet, it does not land on it");
		w.box(11, 75, -3, 11, 75, 3);       // a ledge a block out of the face, its top at 76
		SurfaceSites.Site site = new SurfaceSites(w).near(m[0], m[1], m[2], 3.0, 7.0, 5.0, 4.0, true, 8, 40, 80, 0);
		assertNotNull(site);
		assertEquals(Surface.Face.EAST, site.face());
		assertEquals(Surface.Face.EAST.upYaw(), site.yaw(), 1e-4, "upright on the wall");
		double[] at = site.world();
		assertTrue(at[1] < m[1], "below the mouth: it reaches up to it head first");
		assertEquals(11.0, at[0], 1e-9, "on the face");
		assertEquals(76.0 + SurfaceSites.HEEL, at[1], 1e-9, "its heels on the ledge");
		double d = Math.hypot(at[1] - m[1], at[2] - m[2]);
		assertTrue(d >= 3.0 && d <= 7.5, "a bite from the mouth: " + d);
	}

	@Test
	void fromTheGroundItNeverHopsOntoAWallOnlyAcrossTheGround() {
		NavTest.World w = new NavTest.World();
		w.box(-6, 64, -6, 6, 110, 6);       // a pillar, 13 wide, its top at y 111
		SurfaceSites sites = new SurfaceSites(w);
		// at its foot, the prey on top: no hop gets it there (onto the side would; it may only fly there)
		assertNull(sites.toward(12.5, 64.0, 0.5, 0.5, 111.0, 0.5, 12.0, 3.0));
		// a low ledge on the way to prey beyond it: onto the ledge
		w.box(14, 64, -6, 30, 65, 6);
		SurfaceSites.Site up = sites.toward(9.5, 64.0, 0.5, 24.5, 66.0, 0.5, 12.0, 3.0);
		assertNotNull(up);
		assertEquals(Surface.Face.FLOOR, up.face());
		assertEquals(66, up.y());
	}

	@Test
	void aFootholdIsABlockOutOfTheFaceRightUnderAHindFoot() {
		NavTest.World w = cliff();
		Surface.Face east = Surface.Face.EAST;
		double[] l = east.toLocal(new double[]{11.5, 85.5, 0.5}, new double[3]);
		BlockGrid grid = SurfaceGrid.around(w, east, l[1]);
		int x = (int) Math.floor(l[0]), z = (int) Math.floor(l[2]);
		assertEquals(BlockGrid.NO_GROUND, SurfaceSites.fits(grid, east, x, z, east.upYaw(), 0), "sheer");
		// right under the hind feet (heels at 85.5 - HEEL, lowered a quarter block onto it: 83): a block out under one foot is enough
		w.box(11, 82, 1, 11, 82, 1);
		assertNotEquals(BlockGrid.NO_GROUND, SurfaceSites.fits(grid, east, x, z, east.upYaw(), 0), "a block under one foot");
		// one block higher it is where the foot is, not under it
		w.solid.clear();
		w.box(-10, 64, -30, 10, 100, 30);
		w.box(11, 83, -1, 11, 83, 1);
		assertEquals(BlockGrid.NO_GROUND, SurfaceSites.fits(grid, east, x, z, east.upYaw(), 0), "in the feet's way");
	}

	/** A wall by where its face is ({@code face(y, z)}: solid west of it), a tunnel 6 deep at y 86, z 0, and maybe a ledge at y 79. */
	private interface Shape {
		int face(int y, int z);
	}

	private static NavTest.World wall(Shape shape, boolean ledge) {
		return wall(shape, ledge, true);
	}

	private static NavTest.World wall(Shape shape, boolean ledge, boolean tunnel) {
		NavTest.World w = new NavTest.World();
		for (int y = 64; y <= 100; y++) for (int z = -30; z <= 30; z++) w.box(-40, y, z, shape.face(y, z) - 1, y, z);
		int f = shape.face(86, 0);
		for (int x = f - 6; x < f && tunnel; x++) {
			w.solid.remove(AirPlanner.key(x, 86, 0));
			w.solid.remove(AirPlanner.key(x, 87, 0));
		}
		if (ledge) for (int z = -5; z <= 5; z++) w.box(shape.face(79, z), 79, z, shape.face(79, z), 79, z);
		return w;
	}

	/** Where it grips {@code shape} (no tunnel, no ledge: nothing for its feet anywhere) to reach the point in front of it, 86 up. */
	private static SurfaceSites.Site gripSheer(Shape shape) {
		NavTest.World w = wall(shape, false, false);
		double fx = shape.face(86, 0) + 1.5;
		return new SurfaceSites(w).near(fx, 86.5, 0.5, 3.0, 7.0, 5.0, 5.0, true, 9, fx + 30, 92.5, 0.5);
	}

	/** Where it grips {@code shape} to reach the tunnel's mouth, as the wall approach asks. */
	private static SurfaceSites.Site grip(Shape shape, boolean ledge) {
		NavTest.World w = wall(shape, ledge);
		int f = shape.face(86, 0);
		double[] m = Burrow.find(w, f - 4.5, 86.0, 0.5).mouth();
		return new SurfaceSites(w).near(m[0], m[1], m[2], 3.0, 7.0, 5.0, 5.0, true, 9, m[0] + 30, m[1] + 6, m[2]);
	}

	/** It hangs upright on {@code site}, a hind heel resting on a block (its own block open), and its feet on the face. */
	private static void standsOn(Shape shape, boolean ledge, SurfaceSites.Site site) {
		NavTest.World w = wall(shape, ledge);
		assertEquals(site.face().upYaw(), site.yaw(), 1e-4, "upright on the face");
		assertTrue(site.drop() >= 0.0 && site.drop() < 1.0, "lowered less than a block onto its foothold: " + site.drop());
		double[] l = site.face().toLocal(site.world(), new double[3]);
		BlockGrid grid = SurfaceGrid.around(w, site.face(), l[1]);
		assertTrue(SurfaceSites.standsOn(grid, site.face(), l[0], l[1], l[2]), "a heel rests on its foothold: " + site);
	}

	private static final Shape SHEER = (y, z) -> 11, DIAGONAL = (y, z) -> 11 - Math.max(-12, Math.min(12, z)),
			STEPPED = (y, z) -> 22 - (y - 64) / 2, STAIR = (y, z) -> 33 - (y - 64);

	@Test
	void aSheerWallIsGrippedOnlyOverALedge() {
		assertNull(gripSheer(SHEER), "nothing for its feet");
		SurfaceSites.Site site = grip(SHEER, true);
		assertNotNull(site);
		assertEquals(Surface.Face.EAST, site.face());
		standsOn(SHEER, true, site);
		assertEquals(80.0 + SurfaceSites.HEEL, site.world()[1], 1e-9, "its heels on the ledge (its top at 80)");
	}

	@Test
	void aDiagonalWallIsGrippedFacingIt() {
		// a zigzag of whole blocks running north-east to south-west: its face looks south-east
		assertNull(gripSheer(DIAGONAL), "sheer: nothing for its feet");
		SurfaceSites.Site site = grip(DIAGONAL, true);
		assertNotNull(site);
		assertEquals(Surface.Face.SOUTH_EAST, site.face(), "the diagonal wall's own frame");
		standsOn(DIAGONAL, true, site);
		// it faces the wall square on: the yaw of facing south-east's opposite
		assertEquals(0.0, wrap(site.yaw() - Math.toDegrees(Math.atan2(-site.face().nx, site.face().nz))), 1e-4);
	}

	@Test
	void aSteppedWallIsGrippedLyingAlongItsSteps() {
		// back a block every two up: every step is a foothold, no ledge needed
		SurfaceSites.Site site = grip(STEPPED, false);
		assertNotNull(site);
		assertEquals(Surface.Face.EAST_STEEP, site.face(), "the stepped wall's own frame, not the sheer one");
		standsOn(STEPPED, false, site);
	}

	@Test
	void aStairOfCliffsIsGrippedToo() {
		// back a block every block up: the 45 degree frame
		SurfaceSites.Site site = grip(STAIR, false);
		assertNotNull(site);
		assertEquals(Surface.Face.EAST_SLOPE, site.face());
		standsOn(STAIR, false, site);
	}

	@Test
	void everyFaceKeepsTheYawOfFacingIt() {
		for (Surface.Face face : Surface.Face.values()) {
			if (!face.wall()) continue;
			double facing = Math.toDegrees(Math.atan2(-face.nx, face.nz));
			assertEquals(0.0, wrap(face.upYaw() - facing), 1e-4, face + ": gripping it from the hover turns no yaw");
		}
	}

	private static double wrap(double d) {
		d %= 360.0;
		if (d > 180) d -= 360;
		if (d < -180) d += 360;
		return d;
	}
}
