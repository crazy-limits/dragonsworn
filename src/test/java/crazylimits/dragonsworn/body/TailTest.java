package crazylimits.dragonsworn.body;

import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.flight.Wingbeat;
import crazylimits.dragonsworn.nav.BlockGrid;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TailTest {
	private static final int N = TailChain.SEGMENTS;

	private static TailMotion.Pose motion(DragonAnim anim, double t) {
		TailMotion.Pose pose = new TailMotion.Pose();
		TailMotion.sample(anim, t, pose);
		return pose;
	}

	/** Blocks for the tests: a floor at y < 64 (the dragon stands on it at y = 64) plus whatever is added. */
	private static class Blocks implements BlockGrid {
		final Set<Long> solid = new HashSet<>();
		int floor = 64;

		Blocks fill(int x0, int y0, int z0, int x1, int y1, int z1) {
			for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) solid.add(key(x, y, z));
			return this;
		}

		static long key(int x, int y, int z) {
			return ((long) x & 0x1FFFFF) << 42 | ((long) y & 0x1FFFFF) << 21 | ((long) z & 0x1FFFFF);
		}

		@Override
		public boolean blocked(int x, int y, int z) {
			return y < floor || solid.contains(key(x, y, z));
		}

		@Override
		public int ground(int x, int z) {
			return floor;
		}
	}

	private static DragonBody standing(double yaw) {
		DragonBody body = new DragonBody();
		body.tick(yaw, 0.5, 64, 0.5, DragonBody.Mode.GROUND);
		return body;
	}

	/** Solves the tail of {@code anim} at {@code t} for a dragon at (0.5, 64, 0.5) among {@code blocks}. */
	private static double[][] solve(Tail tail, DragonAnim anim, double t, DragonBody body, BlockGrid blocks, double time) {
		double[] frame = new double[PoseTrack.POINTS * 3];
		PoseTrack.sample(anim, t, frame);
		TailChain chain = new TailChain().frame(frame);
		double[] x = new double[N], y = new double[N];
		Tail.World world = blocks == null ? null : new Tail.World().set(blocks, body, 1.0F, 0.5, 64, 0.5, time);
		tail.solve(chain, motion(anim, t), null, null, world, x, y);
		return new double[][]{x, y};
	}

	/** Every capsule of the solved tail in world coordinates: the deepest it reaches into {@code blocks}. */
	private static double deepest(DragonAnim anim, double t, DragonBody body, double[][] bends, BlockGrid blocks) {
		double[] frame = new double[PoseTrack.POINTS * 3];
		PoseTrack.sample(anim, t, frame);
		TailChain chain = new TailChain().frame(frame);
		chain.pose(bends[0], bends[1]);
		double worst = 0;
		double[] cap = new double[6], w = new double[6];
		for (int s = 0; s < N; s++) {
			chain.capsule(s, cap);
			PartSolver.toWorld(body, 1.0F, cap, 0, w, 0);
			PartSolver.toWorld(body, 1.0F, cap, 1, w, 1);
			double r = TailChain.radius(s);
			for (int i = 1; i <= 20; i++) {
				double k = i / 20.0;
				double px = 0.5 + w[0] + (w[3] - w[0]) * k, py = 64 + w[1] + (w[4] - w[1]) * k, pz = 0.5 + w[2] + (w[5] - w[2]) * k;
				for (int cx = (int) Math.floor(px - r); cx <= Math.floor(px + r); cx++) {
					for (int cy = (int) Math.floor(py - r); cy <= Math.floor(py + r); cy++) {
						for (int cz = (int) Math.floor(pz - r); cz <= Math.floor(pz + r); cz++) {
							if (!blocks.blocked(cx, cy, cz)) continue;
							double qx = Math.max(cx, Math.min(cx + 1, px)), qy = Math.max(cy, Math.min(cy + 1, py)), qz = Math.max(cz, Math.min(cz + 1, pz));
							double d = Math.sqrt((px - qx) * (px - qx) + (py - qy) * (py - qy) + (pz - qz) * (pz - qz));
							worst = Math.max(worst, r - d);
						}
					}
				}
			}
		}
		return worst;
	}

	private static double tipZ(DragonAnim anim, double t, DragonBody body, double[][] bends) {
		double[] frame = new double[PoseTrack.POINTS * 3], cap = new double[6], w = new double[3];
		PoseTrack.sample(anim, t, frame);
		TailChain chain = new TailChain().frame(frame);
		chain.pose(bends[0], bends[1]);
		chain.capsule(N - 1, cap);
		PartSolver.toWorld(body, 1.0F, cap, 1, w, 0);
		return w[2];
	}

	@Test
	void inFlightTheBodysMotionRunsDownTheTailAsAWave() {
		// over one beat each segment swings, and the swing peaks later toward the tip
		int root = peakTime(0), tip = peakTime(7);
		assertNotEquals(root, tip);
		assertTrue(Math.floorMod(tip - root, 40) < 20, "the tip follows the root: " + root + " -> " + tip);
		for (int k = 0; k < 9; k++) {
			double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
			for (int i = 0; i < 40; i++) {
				double x = motion(DragonAnim.FLY, i * Wingbeat.FLAP_SECONDS / 40).x[k];
				lo = Math.min(lo, x);
				hi = Math.max(hi, x);
			}
			assertTrue(hi - lo > 0.3 && hi - lo < 30, "segment " + k + " swings " + (hi - lo));
		}
		// hovering it hangs; gliding it sways, the tip most; in flight it does not lie down
		double hang = 0;
		for (int i = 0; i < 40; i++) for (double x : motion(DragonAnim.HOVER, i * Wingbeat.FLAP_SECONDS / 40).x) hang += x;
		assertTrue(hang > 0, "the hover's tail hangs");
		TailMotion.Pose glide = motion(DragonAnim.GLIDE, 0.5);
		assertTrue(Math.abs(glide.y[8]) > Math.abs(glide.y[0]) && Math.abs(glide.y[8]) > 0.5);
		assertEquals(0.0, motion(DragonAnim.FLY, 0.3).rest, 1e-6);
	}

	@Test
	void takeoffAndLandingLayTheTailDownWhileTheFeetAreOnTheGround() {
		assertEquals(1.0, motion(DragonAnim.TAKEOFF, 0.2).rest, 0.02, "crouched");
		assertEquals(0.0, motion(DragonAnim.TAKEOFF, PoseTrack.length(DragonAnim.TAKEOFF)).rest, 0.02, "in the air");
		assertEquals(0.0, motion(DragonAnim.LAND, 1.0).rest, 0.02, "flaring");
		assertEquals(1.0, motion(DragonAnim.LAND, DragonAnim.LAND_TOUCH_SECONDS + 0.8).rest, 0.02, "landed");
		assertTrue(motion(DragonAnim.LAND, 1.2).x[0] + motion(DragonAnim.LAND, 1.2).x[1] > 0.5, "the flare drops the tail as an air brake");
	}

	private static int peakTime(int segment) {
		int best = 0;
		double max = -Double.MAX_VALUE;
		for (int i = 0; i < 40; i++) {
			double x = motion(DragonAnim.FLY, i * Wingbeat.FLAP_SECONDS / 40).x[segment];
			if (x > max) {
				max = x;
				best = i;
			}
		}
		return best;
	}

	@Test
	void theDyingTailTucksBetweenTheLegsAndStaysThere() {
		TailMotion.Pose start = motion(DragonAnim.DEATH, 0), wrapped = motion(DragonAnim.DEATH, DragonAnim.DEATH_WRAP_SECONDS);
		assertEquals(0.0, start.x[0], 1e-9);
		assertArrayEquals(TailMotion.TUCK, wrapped.x, 1e-9);
		assertArrayEquals(TailMotion.TUCK, motion(DragonAnim.DEATH, DragonAnim.DEATH_SECONDS + 5).x, 1e-9, "held to the end");
		double curl = 0;
		for (double x : TailMotion.TUCK) curl += x;
		assertTrue(curl > 150, "it curls round forward, under the body (sat up 50 degrees)");
		assertEquals(0.0, wrapped.rest, "not laid on the ground");
	}

	@Test
	void theMotionIsTheOldKeyframes() {
		// values the keyframes had (tail_1, tail_3, tail_6, tail_9), read from the animation file they replace
		TailMotion.Pose walk = motion(DragonAnim.WALK, 0);
		assertArrayEquals(new double[]{-1.5, 0.67, 0.66}, new double[]{walk.x[0], walk.x[2], walk.x[5]}, 0.01);
		assertArrayEquals(new double[]{1.36, 2.52, 3.0}, new double[]{walk.y[0], walk.y[2], walk.y[5]}, 0.01);
		TailMotion.Pose idle = motion(DragonAnim.IDLE, 1);
		assertArrayEquals(new double[]{2.5, 1.93, 0.91}, new double[]{idle.y[0], idle.y[2], idle.y[5]}, 0.01);
		assertArrayEquals(new double[]{0, 2.0, 2.25}, new double[]{idle.x[0], idle.x[2], idle.x[5]}, 0.01, "the curl, before the lay");
		assertEquals(1.0, idle.rest);
		assertEquals(0.0, walk.rest, "a walking tail does not lie down");
		assertEquals(26.0, motion(DragonAnim.TAIL_SWEEP, 0.9).lift, 1.0, "the strike's raised tail");
	}

	@Test
	void oneShotsCarryOnIntoTheirNextAnimation() {
		TailMotion.Pose after = motion(DragonAnim.ROAR, PoseTrack.length(DragonAnim.ROAR) + 1.0), idle = motion(DragonAnim.IDLE, 1.0);
		assertArrayEquals(idle.y, after.y, 1e-9);
		TailMotion.Pose hover = motion(DragonAnim.TAKEOFF, PoseTrack.length(DragonAnim.TAKEOFF) + 0.4);
		assertArrayEquals(motion(DragonAnim.HOVER, 0.4).x, hover.x, 1e-9);
	}

	@Test
	void aBlendStartsFromTheLastAnimation() {
		TailMotion.Pose pose = new TailMotion.Pose();
		TailMotion.sample(DragonAnim.IDLE, 0.0, DragonAnim.GLIDE, 0.0, 0.0, pose);
		assertArrayEquals(motion(DragonAnim.GLIDE, 0.0).y, pose.y, 1e-9);
		TailMotion.sample(DragonAnim.IDLE, 0.0, DragonAnim.GLIDE, 0.0, 0.5, pose);
		assertEquals(0.5, pose.rest, 1e-9, "half laid down");
	}

	@Test
	void onFlatGroundTheTailRestsWhereTheKeyframesLaidIt() {
		double[][] bends = solve(new Tail(), DragonAnim.IDLE, 0, standing(0), null, 0);
		// the keyframes' lay solve put tail_rot1 at -22.5 (two segments of -11.25) in the idle's first frame
		assertEquals(-11.25, bends[0][0], 3.0);
		// in a world of flat ground it lies the same way: on the blocks, not in them
		Blocks flat = new Blocks();
		double[][] onBlocks = solve(new Tail(), DragonAnim.IDLE, 0, standing(0), flat, 0);
		assertEquals(bends[0][0], onBlocks[0][0], 1.5);
		assertTrue(deepest(DragonAnim.IDLE, 0, standing(0), onBlocks, flat) < 0.03);
	}

	@Test
	void itDroopsOverALedgeAndRisesOnAStep() {
		DragonBody body = standing(0);
		double flat = solve(new Tail(), DragonAnim.IDLE, 0, body, new Blocks(), 0)[0][0];
		// yaw 0 faces -z: the tail lies toward +z. Cut the ground away behind the hind feet...
		Blocks ledge = new Blocks() {
			@Override
			public boolean blocked(int x, int y, int z) {
				return z < 3 ? y < 64 : y < 60;
			}
		};
		assertTrue(solve(new Tail(), DragonAnim.IDLE, 0, body, ledge, 0)[0][0] > flat + 3, "it hangs down over the ledge");
		// ...or raise it a block
		Blocks step = new Blocks() {
			@Override
			public boolean blocked(int x, int y, int z) {
				return z < 4 ? y < 64 : y < 65;
			}
		};
		double[][] onStep = solve(new Tail(), DragonAnim.IDLE, 0, body, step, 0);
		assertTrue(onStep[0][0] < flat - 2, "it lies higher on the step");
		assertTrue(deepest(DragonAnim.IDLE, 0, body, onStep, step) < 0.03);
	}

	@Test
	void aWallBehindBendsTheTailAway() {
		DragonBody body = standing(0);
		for (DragonAnim anim : new DragonAnim[]{DragonAnim.IDLE, DragonAnim.WALK, DragonAnim.HOVER, DragonAnim.TAIL_SWEEP}) {
			for (int wall = 6; wall <= 10; wall += 2) {
				Blocks blocks = new Blocks().fill(-20, 64, wall, 20, 80, wall + 2);
				double[][] free = solve(new Tail(), anim, 0.5, body, new Blocks(), 0);
				assertTrue(tipZ(anim, 0.5, body, free) > wall, "without the wall the tail reaches past z = " + wall);
				Tail tail = new Tail();
				double[][] bends = solve(tail, anim, 0.5, body, blocks, 0);
				double deep = deepest(anim, 0.5, body, bends, blocks);
				assertTrue(deep < 0.05, anim + ", wall at z = " + wall + ": into it by " + deep);
				assertEquals(tail.overlap(), deep, 0.05);
			}
		}
	}

	@Test
	void boxedInByWallsAndAFloorItStillFitsWithoutClipping() {
		DragonBody body = standing(0);
		// a narrow corridor behind it, turning left: walls on both sides, then a wall ahead
		Blocks blocks = new Blocks().fill(-6, 64, 3, -2, 70, 12).fill(2, 64, 3, 6, 70, 12).fill(-1, 64, 8, 1, 70, 12);
		double[][] bends = solve(new Tail(), DragonAnim.IDLE, 0.0, body, blocks, 0);
		assertTrue(deepest(DragonAnim.IDLE, 0.0, body, bends, blocks) < 0.05);
	}

	@Test
	void itLetsGoOfAnAvoidingBendGraduallyAndOnlyWhenClear() {
		DragonBody body = standing(0);
		Blocks wall = new Blocks().fill(-20, 64, 7, 20, 80, 9);
		Tail tail = new Tail();
		double[][] bent = solve(tail, DragonAnim.IDLE, 0, body, wall, 0);
		double[][] free = solve(new Tail(), DragonAnim.IDLE, 0, body, new Blocks(), 0);
		// the wall is gone: the next tick it has let go of only a little, after a few seconds of all of it
		double[][] next = solve(tail, DragonAnim.IDLE, 0, body, new Blocks(), 1);
		double before = change(bent, free), after = change(next, free);
		assertTrue(before > 10, "the wall bent it: " + before);
		assertTrue(after < before && after > 0.7 * before, "let go gradually: " + before + " -> " + after);
		double[][] later = null;
		for (int t = 2; t < 80; t++) later = solve(tail, DragonAnim.IDLE, 0, body, new Blocks(), t);
		assertTrue(change(later, free) < 0.5, "back to its own pose");
	}

	private static double change(double[][] a, double[][] b) {
		double sum = 0;
		for (int i = 0; i < N; i++) sum += Math.abs(a[0][i] - b[0][i]) + Math.abs(a[1][i] - b[1][i]);
		return sum;
	}

	@Test
	void theTailHitboxesFollowTheSolvedTail() {
		DragonBody body = standing(0);
		Blocks wall = new Blocks().fill(-20, 64, 7, 20, 80, 9);
		PartSolver solver = new PartSolver();
		double[] out = new double[PoseTrack.PARTS * 3];
		solver.solve(DragonAnim.IDLE, 0, body, null, 1.0F, null, new Tail.World().set(wall, body, 1.0F, 0.5, 64, 0.5, 0), out);
		// the tip's hitbox stays in front of the wall
		assertTrue(out[Strike.TAIL_TIP_PART * 3 + 2] + 0.5 < 7.0, "tip at z = " + (out[Strike.TAIL_TIP_PART * 3 + 2] + 0.5));
	}
}
