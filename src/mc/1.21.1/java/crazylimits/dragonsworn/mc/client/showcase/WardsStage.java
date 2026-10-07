package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.arena.CrystalWard;
import crazylimits.dragonsworn.mc.arena.Wards;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/**
 * Stage {@code wards}: a crystal's rune ward. A warded crystal (three rings of runed panes turning round it) and a
 * bare one stand on bedrock; arrows shot at the warded one from round it bounce off the ward's sphere
 * and it stands, an arrow at the bare one breaks it, and projectile damage does not hurt the warded one. Frames:
 * {@code df-ward-*.png}.
 */
final class WardsStage {
	/** Where the shots come from, round the warded crystal (dx, dy, dz from its ward's centre). */
	private static final double[][] FROM = {{12, 0.5, 0}, {-8, 4, 8}, {0, 2, -12}, {5, 10, 5}};
	private static final double SPEED = 2.5;

	private WardsStage() {
	}

	static void wards(int cx, int y, int cz) {
		// the bare one far off the line of fire: its blast would break the warded one (as vanilla's crystals set each other off)
		int bx = cx + 40;
		command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:bedrock", cx, y - 1, cz), 2);
		command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:bedrock", bx, y - 1, cz), 2);
		command(String.format(Locale.ROOT, "summon minecraft:end_crystal %.1f %d %.1f {DragonswornWarded:1b,Tags:[\"df_ward\"]}", cx + 0.5, y, cz + 0.5), 2);
		command(String.format(Locale.ROOT, "summon minecraft:end_crystal %.1f %d %.1f {Tags:[\"df_bare\"]}", bx + 0.5, y, cz + 0.5), 4);
		double wx = cx + 0.5, wy = y + CrystalWard.CENTER, wz = cz + 0.5;
		server(level -> {
			EndCrystal ward = crystal(level, "df_ward"), bare = crystal(level, "df_bare");
			check(ward != null && Wards.warded(ward), "the warded crystal stands, warded");
			check(bare != null && !Wards.warded(bare), "the bare crystal stands, unwarded");
		});
		// the rings, close and from above, a second apart (they turn)
		shoot(view(wx - 5, wy + 1.5, wz - 5, wx, wy, wz), "ward-close", 20);
		shoot(view(wx - 5, wy + 1.5, wz - 5, wx, wy, wz), "ward-close-later", 20);
		shoot(view(wx + 2, wy + 6, wz - 3, wx, wy, wz), "ward-above", 10);
		shoot(view(wx - 2.5, wy + 0.3, wz - 2.5, wx, wy, wz), "ward-panes", 10);

		// arrows at the ward from round it
		List<Integer> arrows = new ArrayList<>();
		double[] nearest = {Double.MAX_VALUE};
		command(view(wx - 9, wy + 4, wz - 9, wx, wy, wz), 10);
		server(level -> {
			for (double[] from : FROM) {
				Arrow arrow = EntityType.ARROW.create(level);
				if (arrow == null) continue;
				arrow.setPos(wx + from[0], wy + from[1], wz + from[2]);
				// aimed a little high: gravity pulls it down on the way
				Vec3 aim = new Vec3(-from[0], -from[1] + 0.03 * Math.hypot(from[0], from[2]), -from[2]);
				arrow.shoot(aim.x, aim.y, aim.z, (float) SPEED, 0.0F);
				arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
				arrow.addTag("df_shot");
				level.addFreshEntity(arrow);
				arrows.add(arrow.getId());
			}
		});
		for (int t = 0; t < 12; t++) {
			server(level -> {
				for (int id : arrows) {
					if (level.getEntity(id) instanceof Arrow arrow) {
						nearest[0] = Math.min(nearest[0], arrow.getBoundingBox().getCenter().distanceTo(new Vec3(wx, wy, wz)));
					}
				}
			});
			if (t == 3) shoot(view(wx - 9, wy + 4, wz - 9, wx, wy, wz), "ward-bounce", 1);
		}
		server(level -> {
			int away = 0;
			for (int id : arrows) {
				if (level.getEntity(id) instanceof Arrow arrow && arrow.position().distanceTo(new Vec3(wx, wy, wz)) > CrystalWard.RADIUS) away++;
			}
			check(away == arrows.size() && !arrows.isEmpty(), String.format(Locale.ROOT, "%d of %d arrows bounced off the ward and flew away", away, arrows.size()));
			check(nearest[0] > CrystalWard.RADIUS - 0.5, String.format(Locale.ROOT, "no arrow got through the ward (nearest %.2f from its centre)", nearest[0]));
			check(crystal(level, "df_ward") != null, "the warded crystal survived the arrows");
			EndCrystal ward = crystal(level, "df_ward");
			if (ward != null) {
				Arrow arrow = EntityType.ARROW.create(level);
				boolean hurt = arrow != null && ward.hurt(level.damageSources().arrow(arrow, null), 5.0F);
				check(!hurt && crystal(level, "df_ward") != null, "projectile damage does not hurt the warded crystal");
			}
		});
		// the bare crystal breaks
		server(level -> {
			Arrow arrow = EntityType.ARROW.create(level);
			if (arrow == null) return;
			arrow.setPos(bx + 0.5 + 8, y + 1, cz + 0.5);
			arrow.shoot(-1, 0.03, 0, (float) SPEED, 0.0F);
			arrow.addTag("df_shot");
			level.addFreshEntity(arrow);
		});
		STEPS.add(new Step(20, mc -> {}));
		server(level -> check(crystal(level, "df_bare") == null, "an arrow breaks the bare crystal"));
		command("kill @e[tag=df_shot]", 2);
		command("kill @e[tag=df_ward]", 2);
	}

	private static EndCrystal crystal(ServerLevel level, String tag) {
		List<? extends EndCrystal> found = level.getEntities(EntityType.END_CRYSTAL, e -> e.getTags().contains(tag) && e.isAlive());
		return found.isEmpty() ? null : found.get(0);
	}
}
