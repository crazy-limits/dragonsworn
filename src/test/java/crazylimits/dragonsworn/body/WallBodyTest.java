package crazylimits.dragonsworn.body;

import crazylimits.dragonsworn.limb.BodyFrame;
import crazylimits.dragonsworn.nav.Surface;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** On a wall the body is turned onto the face: hitboxes, the frame the feet stand in and the drawn model agree. */
class WallBodyTest {
	private static DragonBody standing(Surface.Face face, double yaw) {
		DragonBody body = new DragonBody();
		body.surface.reset(face);
		for (int i = 0; i < 60; i++) body.tick(yaw, 0.0, 64.0, 0.0, DragonBody.Mode.GROUND);
		return body;
	}

	@Test
	void onAWallTheBodysUpIsTheFacesNormalAndItsHeadGoesUpTheWall() {
		// facing the east face (yaw -90: facing -x) it climbs straight up it
		DragonBody body = standing(Surface.Face.EAST, -90.0);
		double[] model = {0.0, 5.0, 0.0, 0.0, 0.0, -6.0};
		double[] out = new double[6];
		PartSolver.toWorld(body, 1.0F, model, 0, out, 0);
		PartSolver.toWorld(body, 1.0F, model, 1, out, 1);
		double[] base = new double[3];
		PartSolver.toWorld(body, 1.0F, new double[]{0.0, 0.0, 0.0}, 0, base, 0);
		assertEquals(5.0, out[0] - base[0], 1e-9, "model up is out of the face (+x)");
		assertEquals(0.0, out[1] - base[1], 1e-9);
		assertTrue(out[4] - base[1] > 5.9, "the nose (-z) points up the wall: " + (out[4] - base[1]));
	}

	@Test
	void hitboxesAndTheDrawnFrameAgreeAndMapBack() {
		DragonBody body = standing(Surface.Face.NORTH, 30.0);
		double[] model = {1.5, 2.0, -3.0};
		double[] w = new double[3];
		PartSolver.toWorld(body, 1.0F, model, 0, w, 0);
		double[] back = new double[3];
		PartSolver.toModel(body, 1.0F, w[0], w[1], w[2], back);
		assertArrayEquals(model, back, 1e-9);
		// the renderer's frame (BodyFrame, pixels) puts the same point at the same place
		BodyFrame frame = new BodyFrame().set(0.0, 64.0, 0.0, body.yaw(1.0F), body.pitch(1.0F), body.roll(1.0F))
				.surface(body.surface.rotation(1.0F), body.lift(1.0F));
		double[] drawn = frame.toWorld(new double[]{model[0] * 16, model[1] * 16, model[2] * 16}, new double[3]);
		assertArrayEquals(new double[]{w[0], w[1] + 64.0, w[2]}, drawn, 1e-9);
		assertArrayEquals(new double[]{model[0] * 16, model[1] * 16, model[2] * 16}, frame.toModel(drawn, new double[3]), 1e-9);
	}

	@Test
	void onTheGroundNothingChanges() {
		DragonBody body = standing(Surface.Face.FLOOR, 45.0);
		double[] model = {1.0, 2.0, 3.0};
		double[] world = new double[3], surface = new double[3];
		PartSolver.toWorld(body, 1.0F, model, 0, world, 0);
		PartSolver.toSurface(body, 1.0F, model, 0, surface, 0);
		assertArrayEquals(surface, world, 1e-12);
	}
}
