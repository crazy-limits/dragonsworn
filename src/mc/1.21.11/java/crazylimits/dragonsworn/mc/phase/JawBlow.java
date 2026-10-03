package crazylimits.dragonsworn.mc.phase;

import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.body.Strike;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A blow landing (a bite, or the tail's tip): where the strike's IK puts the jaws when they close on
 * {@code aim}, and what is there to be hit. Only what is at the jaws then is hit, so a dodge is a miss.
 */
final class JawBlow {
	private JawBlow() {
	}

	/**
	 * Where {@code anim}'s blow lands (world) with the strike aimed at {@code aim}: {@code probe} is a
	 * {@link Strike} of the phase's own, so asking does not touch the dragon's synced aim.
	 */
	static Vec3 landing(EnderDragon dragon, Strike probe, DragonAnim anim, Vec3 aim) {
		var body = DragonswornDragon.brain(dragon).body;
		probe.aim(anim, aim.x - dragon.getX(), aim.y - dragon.getY(), aim.z - dragon.getZ());
		probe.solve(body, 1.0F);
		double[] end = new double[3];
		probe.blow(body, 1.0F, end);
		return dragon.position().add(end[0], end[1], end[2]);
	}

	/** The living things (not creative or spectator players) whose box, grown by {@code radius}, holds {@code point}. */
	static List<LivingEntity> struck(EnderDragon dragon, Vec3 point, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		for (Entity e : dragon.level().getEntities(dragon, new AABB(point, point).inflate(radius + 2.0), EntitySelector.NO_CREATIVE_OR_SPECTATOR)) {
			if (e instanceof LivingEntity living && e.getBoundingBox().inflate(radius).contains(point)) out.add(living);
		}
		return out;
	}
}
