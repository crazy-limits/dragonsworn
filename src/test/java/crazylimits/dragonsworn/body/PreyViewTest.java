package crazylimits.dragonsworn.body;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreyViewTest {
	/** A player: 0.6 x 1.8, eyes at 1.62. */
	private static final double HEIGHT = 1.8, EYES = 1.62;
	private static final double[] FEET = {10.0, 64.0, -5.0};
	private static final double[] STANDING = {FEET[0], FEET[1] + EYES, FEET[2]};

	@Test
	void lyingEyesAreLevelWithTheMiddleTowardTheHead() {
		// yaw 0: the head toward +z (Minecraft's facing is (-sin, cos))
		double[] eyes = PreyView.lyingEyes(FEET[0], FEET[1], FEET[2], EYES, HEIGHT, 0.0);
		assertArrayEquals(new double[]{10.0, 64.9, -5.0 + 0.72}, eyes, 1e-9);
		// yaw 90: toward -x
		eyes = PreyView.lyingEyes(FEET[0], FEET[1], FEET[2], EYES, HEIGHT, 90.0);
		assertArrayEquals(new double[]{10.0 - 0.72, 64.9, -5.0}, eyes, 1e-9);
		// as far from the middle as standing eyes are above it, whatever the yaw
		for (double yaw = -180; yaw <= 180; yaw += 15) {
			eyes = PreyView.lyingEyes(FEET[0], FEET[1], FEET[2], EYES, HEIGHT, yaw);
			assertEquals(EYES - HEIGHT / 2.0, Math.hypot(eyes[0] - FEET[0], eyes[2] - FEET[2]), 1e-9);
			assertEquals(FEET[1] + HEIGHT / 2.0, eyes[1], 1e-12);
		}
	}

	@Test
	void firstPersonAtTheStandingEyesSeesFromTheLyingHead() {
		double[] lying = PreyView.lyingEyes(FEET[0], FEET[1], FEET[2], EYES, HEIGHT, 30.0);
		assertArrayEquals(lying, PreyView.camera(STANDING.clone(), STANDING, lying, false), 1e-12);
		// the game's sum rounded differently (float eye height) is still the game's placement
		double[] rounded = {STANDING[0], STANDING[1] + 2e-5, STANDING[2]};
		assertArrayEquals(lying, PreyView.camera(rounded, STANDING, lying, false), 1e-4);
	}

	@Test
	void firstPersonPlacedByAnotherModIsLeftAlone() {
		double[] lying = PreyView.lyingEyes(FEET[0], FEET[1], FEET[2], EYES, HEIGHT, 30.0);
		// a body camera mod put it at the drawn head already
		assertNull(PreyView.camera(lying.clone(), STANDING, lying, false));
		assertNull(PreyView.camera(new double[]{STANDING[0], STANDING[1] - 0.3, STANDING[2]}, STANDING, lying, false));
	}

	@Test
	void thirdPersonKeepsItsOffsetRoundTheEyes() {
		double[] lying = PreyView.lyingEyes(FEET[0], FEET[1], FEET[2], EYES, HEIGHT, -60.0);
		// vanilla's: straight back 4 blocks; a shoulder camera mod's: back, up and to the side, dragged
		double[][] offsets = {{0.0, 0.0, -4.0}, {-0.75, 0.25, -4.0}, {1.3, -0.4, -2.2}, {0.0, 0.0, 0.0}};
		for (double[] o : offsets) {
			double[] camera = {STANDING[0] + o[0], STANDING[1] + o[1], STANDING[2] + o[2]};
			double[] moved = PreyView.camera(camera, STANDING, lying, true);
			assertNotNull(moved);
			assertArrayEquals(new double[]{lying[0] + o[0], lying[1] + o[1], lying[2] + o[2]}, moved, 1e-12);
		}
	}

	@Test
	void nothingIsKeptBetweenFrames() {
		double[] lying = PreyView.lyingEyes(FEET[0], FEET[1], FEET[2], EYES, HEIGHT, 0.0);
		double[] camera = {STANDING[0] - 0.75, STANDING[1] + 0.25, STANDING[2] - 4.0};
		double[] first = PreyView.camera(camera.clone(), STANDING, lying, true);
		// the same frame again (a camera mod that sets up twice, or the next frame) lands on the same spot: no drift
		assertArrayEquals(first, PreyView.camera(camera.clone(), STANDING, lying, true), 0.0);
		// once the hold ends the camera is the game's again: the inputs are untouched
		assertArrayEquals(new double[]{STANDING[0] - 0.75, STANDING[1] + 0.25, STANDING[2] - 4.0}, camera, 0.0);
	}
}
