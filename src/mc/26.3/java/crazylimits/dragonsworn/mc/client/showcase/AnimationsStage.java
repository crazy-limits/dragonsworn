package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.body.Tail;
import crazylimits.dragonsworn.debug.DragonDebug;
import crazylimits.dragonsworn.mc.client.DragonRenderer;
import crazylimits.dragonsworn.mc.client.LimbAnimator;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

import java.util.Locale;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/** Full run only: every animation looped on a frozen dragon and photographed, then the tail kept out of a cage. */
final class AnimationsStage {
	private AnimationsStage() {
	}

	/**
	 * A frozen (NoAI) dragon in the yard at x, y, z, drawn by Dragonsworn's renderer; every animation forced
	 * on it in turn and photographed from the front three-quarter, the side and above (the flying ones on a
	 * second dragon in the air), then the tail in a cage.
	 */
	static void build(int x, int y, int z) {
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {NoAI:1b}", x, y, z), 40);
		STEPS.add(new Step(1, mc -> {
			Script.hideGui(mc, true);
			int dragons = 0;
			boolean ours = true;
			for (var entity : mc.level.entitiesForRendering()) {
				if (entity instanceof EnderDragon dragon) {
					dragons++;
					ours &= mc.getEntityRenderDispatcher().getRenderer(dragon) instanceof DragonRenderer;
				}
			}
			check(dragons == 1, "one dragon in the client world (found " + dragons + ")");
			check(ours, "the Ender Dragon is drawn by Dragonsworn's renderer");
		}));

		// A NoAI dragon keeps yaw 0, so its head points north (-z).
		String front = view(x - 13, y + 5, z - 15, x, y + 3, z);
		String side = view(x - 24, y + 3, z + 2, x, y + 3, z + 2);
		String top = view(x - 12, y + 18, z + 12, x, y + 2, z);

		// Flight is shot on a second dragon summoned in the air, away from the first: a NoAI dragon ignores
		// teleports on the client (vanilla only interpolates its position when it has AI).
		int ax = x + 120, air = y + 16;
		String flight = view(ax - 22, air - 1, z + 6, ax, air + 3, z + 2);
		String flightFront = view(ax - 12, air + 2, z - 24, ax, air + 3, z);
		boolean[] airborne = {false};
		for (DragonAnim anim : DragonAnim.values()) {
			String name = anim.name().toLowerCase(Locale.ROOT);
			STEPS.add(new Step(10, mc -> DragonDebug.forcedAnimation = anim));
			switch (anim) {
				// The walk: four moments a quarter cycle apart (2.4 s cycle = 48 ticks).
				case WALK -> {
					for (int i = 0; i < 4; i++) shoot(front, name + "-front-" + i, 12);
				}
				// A wingbeat: four moments a quarter beat apart (1.6 s = 32 ticks), from the side.
				case FLY, FLAP, GLIDE, HOVER -> {
					if (!airborne[0]) {
						airborne[0] = true;
						command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {NoAI:1b}", ax, air, z), 30);
					}
					for (int i = 0; i < 4; i++) shoot(flight, name + "-side-" + i, 8);
					shoot(flightFront, name + "-front", 6);
				}
				// the takeoff and the landing: their moments from the side
				case TAKEOFF, LAND -> {
					for (int i = 0; i < 6; i++) shoot(side, name + "-side-" + i, anim == DragonAnim.LAND ? 11 : 4);
				}
				default -> {
					for (int i = 0; i < 2; i++) shoot(front, name + "-front-" + i, 15);
				}
			}
			boolean flying = anim == DragonAnim.FLY || anim == DragonAnim.FLAP || anim == DragonAnim.GLIDE || anim == DragonAnim.HOVER;
			if (!flying && anim != DragonAnim.TAKEOFF && anim != DragonAnim.LAND) shoot(side, name + "-side", 6);
			if (anim == DragonAnim.WALK || anim == DragonAnim.IDLE) shoot(top, name + "-top", 6);
		}
		tailCage(x - 60, y, z);
		STEPS.add(new Step(1, mc -> DragonDebug.forcedAnimation = null));
	}


	/**
	 * The procedural tail among blocks: a frozen dragon (facing north, its tail to the south) with a wall
	 * across behind it, a pillar beside its tail and a step under it. Through the idle, the tail strike
	 * and the walk the drawn tail must lie on the step and bend round the pillar and along the wall,
	 * never into a block ({@link Tail#overlap}, every tick).
	 */
	static void tailCage(int tx, int y, int tz) {
		command(view(tx - 22, y + 7, tz + 6, tx, y + 2, tz + 6), 40);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", tx - 9, y, tz + 8, tx + 9, y + 6, tz + 9), 2);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", tx + 2, y, tz + 4, tx + 3, y + 5, tz + 5), 2);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", tx - 4, y, tz + 3, tx + 1, y, tz + 7), 2);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {NoAI:1b}", tx, y, tz), 30);
		String side = view(tx - 22, y + 7, tz + 6, tx, y + 2, tz + 6);
		String top = view(tx - 6, y + 22, tz + 10, tx, y, tz + 5);
		String back = view(tx + 6, y + 9, tz + 22, tx, y + 2, tz + 4);
		double[] worst = new double[1];
		for (DragonAnim anim : new DragonAnim[]{DragonAnim.IDLE, DragonAnim.TAIL_SWEEP, DragonAnim.WALK}) {
			String name = "tail-cage-" + anim.name().toLowerCase(Locale.ROOT);
			STEPS.add(new Step(10, mc -> DragonDebug.forcedAnimation = anim));
			shoot(side, name + "-side", 12);
			shoot(top, name + "-top", 4);
			shoot(back, name + "-back", 4);
			for (int i = 0; i < 40; i++) {
				STEPS.add(new Step(1, mc -> {
					EnderDragon dragon = nearestDragon(mc, tx + 0.5, tz + 0.5);
					if (dragon != null) worst[0] = Math.max(worst[0], LimbAnimator.tailOverlap(dragon));
				}));
			}
		}
		STEPS.add(new Step(1, mc -> {
			REPORT.add(String.format(Locale.ROOT, "INFO tail cage: the drawn tail overlaps blocks by %.3f at worst", worst[0]));
			check(worst[0] < 0.05, "the tail bends round the wall, pillar and step behind it, never into them");
		}));
	}
}
