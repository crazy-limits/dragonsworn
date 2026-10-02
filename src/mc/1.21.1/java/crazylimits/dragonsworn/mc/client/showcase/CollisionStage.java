package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.body.Parts;
import crazylimits.dragonsworn.mc.phase.GroundFightPhase;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.AABB;

import java.util.Locale;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/** Stage {@code collision}: the soft hitboxes push the player and mobs out of the dragon. */
final class CollisionStage {
	private CollisionStage() {
	}


	/**
	 * The head and neck hitboxes stay on the model while its head turns to what it watches: a resting
	 * dragon on the ground follows the camera (the nearest player) round, and every tick the anchors as
	 * drawn are compared with the hitboxes' centres. Server and client must agree on the look too.
	 */
	/**
	 * The hitboxes are soft, as mobs push each other: a husk (moved by the server) and the player (moved
	 * by its client) put inside the resting dragon's chest slide out of every hitbox over a second or
	 * two, not at once, and the dragon is not moved by them.
	 */
	static void collision(int cx, int y, int cz) {
		command(view(cx - 14, y + 6, cz - 14, cx, y + 3, cz), 20);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"],Rotation:[0f,0f]}", cx, y, cz), 6);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null, "a dragon rests on the ground for the collision test");
			if (dragon != null) GroundFightPhase.start(dragon, null);
		});
		STEPS.add(new Step(40, mc -> {}));
		double[] dragonAt = new double[3];
		double[] early = {Double.NaN};
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			Husk husk = EntityType.HUSK.create(level);
			if (dragon == null || husk == null) return;
			dragonAt[0] = dragon.getX();
			dragonAt[2] = dragon.getZ();
			AABB chest = dragon.getSubEntities()[Parts.CHEST].getBoundingBox();
			// with AI: a NoAI mob never moves, so nothing pushes it (as with vanilla's mobs)
			husk.moveTo(chest.getCenter().x + 0.3, chest.minY, chest.getCenter().z + 0.2, 0.0F, 0.0F);
			husk.setSilent(true);
			husk.addTag("df_target");
			level.addFreshEntity(husk);
			REPORT.add(String.format(Locale.ROOT, "INFO collision: husk put %.2f blocks into the chest", inside(husk, dragon)));
		});
		STEPS.add(new Step(2, mc -> {}));
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			var husks = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_target"));
			if (dragon != null && !husks.isEmpty()) early[0] = inside(husks.get(0), dragon);
		});
		STEPS.add(new Step(40, mc -> {}));
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			var husks = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_target"));
			double depth = dragon == null || husks.isEmpty() ? Double.NaN : inside(husks.get(0), dragon);
			check(early[0] > 0.05, String.format(Locale.ROOT, "the push is soft: a few ticks in, the husk is still %.2f inside", early[0]));
			check(depth < 0.01, String.format(Locale.ROOT, "two seconds on, the husk has been pushed out of every hitbox (%.3f in)", depth));
			double moved = dragon == null ? Double.NaN : Math.hypot(dragon.getX() - dragonAt[0], dragon.getZ() - dragonAt[2]);
			check(moved < 0.01, String.format(Locale.ROOT, "the dragon stays put (moved %.3f)", moved));
		});
		command("kill @e[tag=df_target]", 2);
		// the player: its own client pushes it (tp'd into the chest; flying, so only the push moves it)
		STEPS.add(new Step(2, mc -> {
			EnderDragon dragon = clientAiDragon(mc);
			if (dragon == null) return;
			AABB chest = dragon.getSubEntities()[Parts.CHEST].getBoundingBox();
			mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", chest.getCenter().x - 0.3, chest.minY + 0.2, chest.getCenter().z + 0.2));
		}));
		STEPS.add(new Step(60, mc -> {}));
		STEPS.add(new Step(1, mc -> {
			EnderDragon dragon = clientAiDragon(mc);
			double depth = dragon == null ? Double.NaN : inside(mc.player, dragon);
			check(depth < 0.01, String.format(Locale.ROOT, "the player put inside the chest is pushed out of every hitbox (%.3f in)", depth));
		}));
		shoot(view(cx - 10, y + 6, cz - 10, cx, y + 3, cz), "collision", 1);
		command("kill @e[tag=df_ai]", 2);
	}

	/** How deep {@code entity} is in the dragon's hitboxes: the deepest overlap (its thinnest axis), 0 when outside. */
	static double inside(net.minecraft.world.entity.Entity entity, EnderDragon dragon) {
		AABB e = entity.getBoundingBox();
		double worst = 0.0;
		for (var part : dragon.getSubEntities()) {
			AABB p = part.getBoundingBox();
			double dx = Math.min(e.maxX, p.maxX) - Math.max(e.minX, p.minX);
			double dy = Math.min(e.maxY, p.maxY) - Math.max(e.minY, p.minY);
			double dz = Math.min(e.maxZ, p.maxZ) - Math.max(e.minZ, p.minZ);
			worst = Math.max(worst, Math.max(0.0, Math.min(dx, Math.min(dy, dz))));
		}
		return worst;
	}
}
