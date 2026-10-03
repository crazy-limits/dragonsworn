package crazylimits.dragonsworn.mc.breath;

import crazylimits.dragonsworn.attack.BreathAttack;
import crazylimits.dragonsworn.attack.FlamePuff;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A dragon's breath flames in flight (server). Every stream breath (perched, the breath pass, the hover's)
 * pours a puff ({@link FlamePuff}) each damage interval instead of burning its whole cone at once: the puff
 * flies as the client's flame particles do, hurts each living thing it passes through once, and where it
 * meets a block it splashes (everything round the impact burns) and leaves dragon fire. So damage and fire
 * come when the flames get there, and only where they get. Puffs still in the air when the breath ends fly
 * on and land.
 */
public final class BreathFlames {
	/** The splash sets this far round it alight, each column with this chance (per puff). */
	private static final double FIRE_RADIUS = 1.5;
	private static final float FIRE_CHANCE = 0.35F;

	private record Puff(FlamePuff motion, float damage, List<Entity> hit) {}

	private final EnderDragon dragon;
	private final List<Puff> puffs = new ArrayList<>();
	private final double[] from = new double[3];

	public BreathFlames(EnderDragon dragon) {
		this.dragon = dragon;
	}

	/**
	 * A puff from {@code mouth} along {@code dir} (unit), at the perched or the flying jet's speed; in flight it
	 * carries the dragon's own speed too, as the particles do. It burns out after {@code range} blocks at most.
	 */
	public void pour(Vec3 mouth, Vec3 dir, double range, float damage, boolean flying) {
		double jet = flying ? FlamePuff.FLYING_JET : FlamePuff.JET;
		Vec3 v = dir.scale(jet);
		if (flying) v = v.add(dragon.getX() - dragon.xo, dragon.getY() - dragon.yo, dragon.getZ() - dragon.zo);
		puffs.add(new Puff(new FlamePuff(new double[] {mouth.x, mouth.y, mouth.z}, new double[] {v.x, v.y, v.z}, range),
				damage, new ArrayList<>(2)));
	}

	/** Moves every puff one tick: what it passes through burns, a block stops it with a splash. */
	public void tick() {
		for (Iterator<Puff> it = puffs.iterator(); it.hasNext(); ) {
			Puff puff = it.next();
			FlamePuff motion = puff.motion();
			motion.step(from);
			Vec3 start = new Vec3(from[0], from[1], from[2]);
			double[] p = motion.pos();
			Vec3 end = new Vec3(p[0], p[1], p[2]);
			BlockHitResult block = dragon.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dragon));
			boolean splash = block.getType() == HitResult.Type.BLOCK;
			if (splash) end = block.getLocation();

			double radius = motion.radius();
			AABB area = new AABB(start, end).inflate(radius + (splash ? BreathAttack.SPLASH_RADIUS : 0.0));
			for (LivingEntity victim : dragon.level().getEntitiesOfClass(LivingEntity.class, area, EntitySelector.NO_CREATIVE_OR_SPECTATOR)) {
				if (victim == dragon || puff.hit().contains(victim)) continue;
				AABB box = victim.getBoundingBox().inflate(radius);
				boolean passed = box.contains(start) || box.clip(start, end).isPresent();
				boolean splashed = splash && victim.getBoundingBox().inflate(BreathAttack.SPLASH_RADIUS).contains(end);
				if (!passed && !splashed) continue;
				puff.hit().add(victim);
				victim.hurt(dragon.damageSources().dragonBreath(), puff.damage());
			}
			if (splash) {
				DragonFire.spread(dragon.level(), end, FIRE_RADIUS, FIRE_CHANCE);
				motion.stopAt(new double[] {end.x, end.y, end.z});
			}
			if (!motion.burning()) it.remove();
		}
	}
}
