package crazylimits.dragonsworn.anim;

import crazylimits.dragonsworn.body.DragonBody;
import crazylimits.dragonsworn.body.Strike;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The fly-by bite and the hover attacks: timing, reach and what a hit does. */
class AirAttackTest {
	private static DragonBody flying(double yaw, DragonBody.Mode mode) {
		DragonBody body = new DragonBody();
		body.tick(yaw, 0, 64, 0, mode);
		return body;
	}

	@Test
	void theFlybyHitsHarderAndFurtherTheFasterItFlies() {
		assertTrue(FlybyBite.damage(1.3) > FlybyBite.damage(0.9));
		assertTrue(FlybyBite.damage(10.0) <= FlybyBite.MAX_DAMAGE);
		double[] slow = FlybyBite.knockback(0.9, 0.0, 0.0), fast = FlybyBite.knockback(0.0, 0.0, -1.3);
		assertTrue(slow[0] > 0.0 && Math.abs(slow[2]) < 1e-9, "pushed along the flight");
		assertTrue(fast[2] < 0.0 && Math.abs(fast[0]) < 1e-9, "pushed along the flight");
		assertTrue(Math.abs(fast[2]) > Math.abs(slow[0]), "further the faster");
		assertTrue(fast[1] > slow[1] && slow[1] > 0.0, "and up");
	}

	@Test
	void theFlybyStartsItsLengthShortOfTheMeetingPoint() {
		double speed = 1.1, start = FlybyBite.startDistance(speed);
		assertEquals(speed * FlybyBite.HIT_TICKS, start, 1e-9);
		// along the line, 1 block across, facing it
		assertTrue(FlybyBite.due(start - 0.5, 1.0, 5.0, speed));
		assertFalse(FlybyBite.due(start + 2.0, 1.0, 5.0, speed), "too far yet");
		assertFalse(FlybyBite.due(start - 0.5, FlybyBite.OFF_LINE + 1.0, 5.0, speed), "off the line");
		assertFalse(FlybyBite.due(start - 0.5, 1.0, FlybyBite.LINE_UP + 5.0, speed), "not facing it");
		assertFalse(FlybyBite.due(-1.0, 1.0, 5.0, speed), "past it");
		double[] line = FlybyBite.alongLine(10.0, 2.0, 1.0, 0.0);
		assertEquals(10.0, line[0], 1e-9);
		assertEquals(2.0, line[1], 1e-9);
	}

	@Test
	void theFlybyBiteReachesThePreyBelowTheLine() {
		// the body passes CLEARANCE over (and PULL behind) where the bite's pose would carry it: the neck
		// reaches down to the prey, a little off the line too
		for (double yaw : new double[] {0.0, 90.0, 215.0}) {
			DragonBody body = flying(yaw, DragonBody.Mode.FLIGHT);
			double[] rest = Strike.rest(DragonAnim.GLIDE_BITE, body, 1.0F);
			double f = Math.toRadians(yaw), fx = Math.sin(f), fz = -Math.cos(f), rx = -fz, rz = fx, ahead = rest[0] - FlybyBite.PULL;
			for (double across : new double[] {0.0, 1.0, -1.0, 1.5, -1.5}) {
				double ax = fx * ahead + rx * (rest[2] + across), az = fz * ahead + rz * (rest[2] + across);
				Strike strike = new Strike();
				strike.aim(DragonAnim.GLIDE_BITE, ax, rest[1] - FlybyBite.CLEARANCE, az);
				double miss = strike.solve(body, 1.0F);
				assertTrue(miss < 0.15, "yaw " + yaw + ", " + across + " across: misses by " + miss);
			}
		}
	}

	@Test
	void theHoverBiteReachesFromItsSpot() {
		DragonBody body = flying(30.0, DragonBody.Mode.HOVER);
		double[] rest = Strike.rest(DragonAnim.HOVER_BITE, body, 1.0F);
		assertTrue(rest[0] > 2.0, "the jaws rest ahead of the dragon: " + rest[0]);
		// the spot puts the prey's middle where the jaws rest: from there a bite at it reaches exactly
		double f = Math.toRadians(30.0), fx = Math.sin(f), fz = -Math.cos(f);
		double[] spot = HoverAttack.biteSpot(10.0, 70.0, -4.0, fx, fz, rest);
		Strike strike = new Strike();
		strike.aim(DragonAnim.HOVER_BITE, 10.0 - spot[0], 70.0 - spot[1], -4.0 - spot[2]);
		assertTrue(strike.solve(body, 1.0F) < 0.05);
		// and a little off it too (the prey shifts, the hover drifts)
		strike.aim(DragonAnim.HOVER_BITE, 10.0 - spot[0] + 0.5, 70.0 - spot[1] - 0.5, -4.0 - spot[2]);
		assertTrue(strike.solve(body, 1.0F) < Strike.BITE_RADIUS * 0.5);
	}

	@Test
	void theHoverBreathsConeTakesPreyInTheAirAndBelow() {
		// level ahead, a little above, below; and to the side within the arc
		assertEquals(0.0, HoverAttack.angles(0.0F, 0.0, 0.0, -10.0)[1], 1e-9);
		assertTrue(HoverAttack.angles(0.0F, 0.0, 3.0, -10.0)[1] < 0.0, "up at prey above");
		assertEquals(HoverAttack.PITCH_MIN, HoverAttack.angles(0.0F, 0.0, 30.0, -5.0)[1], 1e-9, "never straight up");
		assertEquals(HoverAttack.PITCH_MAX, HoverAttack.angles(0.0F, 0.1, -30.0, -0.1)[1], 1e-9, "never under itself");
		assertEquals(HoverAttack.YAW_ARC, HoverAttack.angles(0.0F, 10.0, 0.0, 0.0)[0], 1e-9, "the side clamped to the arc");
	}

	@Test
	void theBreathSpotIsOffAndOverThePrey() {
		double[] s = HoverAttack.breathSpot(0.0, 64.0, 0.0, 1.0, 0.0);
		assertEquals(-HoverAttack.BREATH_DISTANCE, s[0], 1e-9);
		assertEquals(64.0 + HoverAttack.BREATH_RISE, s[1], 1e-9);
	}

	@Test
	void attacksStartOnTheBeat() {
		assertTrue(HoverAttack.onBeat(0.0));
		assertTrue(HoverAttack.onBeat(0.995));
		assertFalse(HoverAttack.onBeat(0.5));
		assertFalse(HoverAttack.onBeat(-1.0), "not hovering");
	}

	@Test
	void theFlyingBitesAndBreathAreAimed() {
		for (DragonAnim anim : new DragonAnim[] {DragonAnim.GLIDE_BITE, DragonAnim.HOVER_BITE, DragonAnim.HOVER_BREATH}) {
			assertTrue(Strike.strikes(anim), anim.name());
			for (int k = 0; k < 5; k++) assertEquals(1.0, Strike.weight(anim, Strike.hitSeconds(anim), k), 1e-9, anim.name());
		}
		assertTrue(DragonAnim.HOVER_BREATH.breathesInFlight());
		assertEquals(Strike.hitSeconds(DragonAnim.GLIDE_BREATH), Strike.hitSeconds(DragonAnim.HOVER_BREATH));
	}
}
