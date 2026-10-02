package crazylimits.dragonsworn.mc;

import crazylimits.dragonsworn.body.BodyPush;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The dragon's hitboxes are soft, like mobs: what stands in one is pushed out of it as a sheep pushes a
 * cow ({@link BodyPush}), each tick after the parts are placed, while the dragon itself stays put. Each
 * side pushes what it simulates: the server its mobs, a client its own player (players move themselves).
 * The prey a dragon carries is left in its grip.
 */
public final class PartCollision {
	private PartCollision() {}

	public static void push(EnderDragon dragon, EnderDragonPart[] parts) {
		if (parts.length == 0 || dragon.isRemoved()) return;
		Level level = dragon.level();
		AABB all = parts[0].getBoundingBox();
		for (EnderDragonPart part : parts) all = all.minmax(part.getBoundingBox());
		List<Entity> near = level.getEntities(dragon, all, entity -> pushable(level, entity));
		if (near.isEmpty()) return;
		double[] box = new double[6], hitbox = new double[6], push = new double[2];
		for (Entity entity : near) {
			corners(entity.getBoundingBox(), box);
			double vx = 0.0, vz = 0.0;
			for (EnderDragonPart part : parts) {
				AABB p = part.getBoundingBox();
				corners(p, hitbox);
				if (!BodyPush.overlaps(box, hitbox)) continue;
				Vec3 c = p.getCenter();
				BodyPush.push(entity.getX(), entity.getZ(), c.x, c.z, dragon.getX(), dragon.getZ(), push);
				vx += push[0];
				vz += push[1];
			}
			if (vx != 0.0 || vz != 0.0) entity.push(vx, 0.0, vz);
		}
	}

	private static boolean pushable(Level level, Entity entity) {
		if (entity instanceof EnderDragonPart || entity instanceof EnderDragon) return false;
		if (!entity.isPushable() || entity.isPassenger() || PreyHold.carrier(entity) != null) return false;
		// the side that simulates it, as vanilla pushes mobs: a client its own player (and what it steers),
		// the server everything else
		if (level.isClientSide) return entity.isControlledByLocalInstance();
		return !(entity instanceof Player) && !(entity.getControllingPassenger() instanceof Player);
	}

	private static void corners(AABB box, double[] out) {
		out[0] = box.minX;
		out[1] = box.minY;
		out[2] = box.minZ;
		out[3] = box.maxX;
		out[4] = box.maxY;
		out[5] = box.maxZ;
	}
}
