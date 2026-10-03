package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.body.Parts;
import crazylimits.dragonsworn.mc.DragonPhases;
import crazylimits.dragonsworn.mc.client.LimbContact;
import crazylimits.dragonsworn.mc.phase.GroundFightPhase;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Locale;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/** Stage {@code walls}: flying and walking round stone walls, turning on the spot without the feet sliding. */
final class WallsStage {
	private WallsStage() {
	}


	/**
	 * Stone stops it. In the air: a wild dragon sent at a husk behind a wide, tall stone wall must fly
	 * round or over it, never through. On the ground: a dragon standing with its back to a stone wall,
	 * the husk behind the wall, must turn round on the spot (stepping round: see the turn frames), then
	 * walk round the wall's end without climbing it or walking into it.
	 */
	static void walls(int wx, int y, int wz) {
		// the flight: a wall across the way, 61 wide and 46 high
		command(view(wx - 60, y + 30, wz - 20, wx, y + 20, wz), 40);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", wx - 30, y, wz, wx + 30, y + 45, wz + 2), 2);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}", wx, y, wz + 30), 2);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", wx, y + 22, wz - 60), 50);
		int[] flight = {0, 0};     // ticks with the body in stone, worst overlap
		boolean[] beyond = {false};
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
			check(dragon != null && !prey.isEmpty(), "the wall-flight dragon and its husk are there");
			if (dragon == null || prey.isEmpty()) return;
			dragon.getPhaseManager().setPhase(DragonPhases.ROAM);
			dragon.getPhaseManager().getPhase(DragonPhases.ROAM).startPass(prey.get(0));
		});
		for (int i = 0; i < 300; i++) {
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				if (dragon == null) return;
				int n = inStone(level, dragon);
				if (n > 0) flight[0]++;
				flight[1] = Math.max(flight[1], n);
				if (dragon.getZ() > wz + 8) beyond[0] = true;
			});
			if (i % 30 == 0) track(String.format(Locale.ROOT, "walls-flight-%02d", i / 30), 3, 40, 12);
		}
		server(level -> {
			REPORT.add(String.format(Locale.ROOT, "INFO wall flight: %d ticks in stone, at worst %d blocks", flight[0], flight[1]));
			check(flight[0] == 0, "flying at a target behind a stone wall, the body never goes into the stone");
			check(beyond[0], "it found its way round or over the wall to the far side");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);

		// on the ground: back to a 25-wide wall, the husk behind it
		int gx = wx + 120;
		command(view(gx - 30, y + 20, wz - 20, gx, y + 2, wz - 10), 40);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", gx - 12, y, wz, gx + 12, y + 9, wz + 2), 2);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}", gx, y, wz + 12), 2);
		// yaw 0 faces north (-z): its back to the wall
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"],Rotation:[0f,0f]}", gx, y, wz - 9), 10);
		int[] ground = {0, 0};
		double[] highest = {Double.NEGATIVE_INFINITY};
		double[] closest = {Double.MAX_VALUE};
		boolean[] round = {false};
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
			if (dragon == null || prey.isEmpty()) return;
			dragon.setYRot(0.0F);
			GroundFightPhase.start(dragon, prey.get(0), false);
		});
		STEPS.add(new Step(1, mc -> LimbContact.start()));
		// the turn on the spot: a frame every few ticks, from the side and from above
		for (int i = 0; i < 12; i++) {
			shoot(view(gx - 14, y + 6, wz - 9, gx, y + 2, wz - 9), String.format(Locale.ROOT, "walls-turn-side-%02d", i), 3);
			shoot(view(gx - 4, y + 18, wz - 6, gx, y, wz - 9), String.format(Locale.ROOT, "walls-turn-top-%02d", i), 3);
		}
		STEPS.add(new Step(1, mc -> turnSlide(LimbContact.stop())));
		for (int i = 0; i < 700; i++) {
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				if (dragon == null) return;
				int n = inStone(level, dragon);
				if (n > 0) ground[0]++;
				ground[1] = Math.max(ground[1], n);
				// on its feet (once the fight is over it takes off: that is no climb)
				if (dragon.getPhaseManager().getCurrentPhase().getPhase() == DragonPhases.GROUND_FIGHT) {
					highest[0] = Math.max(highest[0], dragon.getY() - y);
					if (dragon.getZ() > wz + 3) round[0] = true;
				}
				List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
				if (!prey.isEmpty()) closest[0] = Math.min(closest[0], dragon.distanceTo(prey.get(0)));
			});
			if (i % 50 == 0) track(String.format(Locale.ROOT, "walls-walk-%02d", i / 50), 3, 30, 14);
		}
		server(level -> {
			REPORT.add(String.format(Locale.ROOT, "INFO wall walk: %d ticks in stone (worst %d), highest %.1f above the ground, closest %.1f to the husk",
					ground[0], ground[1], highest[0], closest[0]));
			check(ground[0] == 0, "walking to a target behind a stone wall, the body never goes into the stone");
			check(highest[0] < 1.5, "on its feet it does not climb the wall");
			check(round[0] && closest[0] < 12.0, "it walked round the wall to the husk's side");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
	}

	/**
	 * Turning on the spot, a planted foot (not in a step) must not slide over the ground: the most any
	 * one moved in a tick while the body turned, per limb.
	 */
	static void turnSlide(List<LimbContact.Sample> samples) {
		double[] worst = new double[4];
		double turned = 0.0;
		int steps = 0, ticks = 0;
		for (int k = 1; k < samples.size(); k++) {
			LimbContact.Sample a = samples.get(k - 1), b = samples.get(k);
			if (b.tick() != a.tick() + 1 || !b.anim().equals("idle") || !a.anim().equals("idle")) continue;
			double dyaw = Math.abs(Mth.wrapDegrees(b.yaw() - a.yaw()));
			if (dyaw < 0.2) continue;
			turned += dyaw;
			ticks++;
			for (int i = 0; i < 4; i++) {
				if (a.feet()[i] == null || b.feet()[i] == null) continue;
				if (b.stepping()[i] && !a.stepping()[i]) steps++;
				if (a.stepping()[i] || b.stepping()[i]) continue;
				worst[i] = Math.max(worst[i], Math.hypot(b.feet()[i][0] - a.feet()[i][0], b.feet()[i][2] - a.feet()[i][2]));
			}
		}
		REPORT.add(String.format(Locale.ROOT, "INFO turn on the spot: %.0f degrees over %d ticks, %d steps; planted feet slid at most %.3f %.3f %.3f %.3f blocks a tick (%s)",
				turned, ticks, steps, worst[0], worst[1], worst[2], worst[3], String.join(", ", LimbContact.LIMBS)));
		check(turned > 60 && steps >= 4, "it turned round on the spot stepping (" + steps + " steps)");
		double most = Math.max(Math.max(worst[0], worst[1]), Math.max(worst[2], worst[3]));
		check(most < 0.03, "planted feet and wrists stay where they stand while the body turns over them");
	}

	/** Parts whose boxes are the solid body: head, necks, chest, hips. */
	private static final int[] SOLID = {Parts.HEAD, Parts.NECK_UPPER, Parts.NECK_LOWER, Parts.CHEST, Parts.HIPS};

	/** How many stone blocks the dragon's body is in (its boxes shrunk a little: brushing is not being in). */
	static int inStone(ServerLevel level, EnderDragon dragon) {
		int n = 0;
		for (int part : SOLID) {
			net.minecraft.world.phys.AABB box = dragon.getSubEntities()[part].getBoundingBox().deflate(0.3);
			n += count(level, Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
					Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ), Blocks.STONE);
		}
		return n;
	}
}
