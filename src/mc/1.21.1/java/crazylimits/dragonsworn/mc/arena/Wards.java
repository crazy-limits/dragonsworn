package crazylimits.dragonsworn.mc.arena;

import crazylimits.dragonsworn.arena.CrystalWard;
import crazylimits.dragonsworn.mc.DragonSounds;
import crazylimits.dragonsworn.mc.arena.mixin.FireworkRocketEntityAccessor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The crystals' rune wards ({@link CrystalWard}) at work: a projectile about to fly into a warded crystal's sphere
 * bounces off it. Run at the end of {@code Projectile.tick} on both sides (the client knows the ward from the
 * synced flag, so its arrows bounce where the server's do), before the projectile's next step: 1.21.1 ticks
 * {@code Projectile.tick} before the step, so the step checked is the one about to be taken.
 */
public final class Wards {
	/** Glyph particles (the enchanting table's) where it bounces. */
	private static final int SPARKS = 24;

	private Wards() {}

	public static boolean warded(EndCrystal crystal) {
		return ((WardedCrystal) crystal).dragonsworn$warded();
	}

	public static void ward(EndCrystal crystal, boolean warded) {
		((WardedCrystal) crystal).dragonsworn$setWarded(warded);
	}

	/** Bounces {@code projectile} off the first warded crystal its next step would enter. */
	public static void deflect(Projectile projectile) {
		// a trident flying back to its thrower, a rocket carried by a gliding player: not shots at the crystal
		if (projectile instanceof AbstractArrow arrow && arrow.isNoPhysics()) return;
		if (projectile instanceof FireworkRocketEntityAccessor rocket && rocket.dragonsworn$attached()) return;
		Vec3 v = projectile.getDeltaMovement();
		if (v.lengthSqr() < 1.0E-6) return;
		AABB reach = projectile.getBoundingBox().expandTowards(v).inflate(CrystalWard.RADIUS + CrystalWard.CENTER + 1.0);
		Vec3 middle = projectile.getBoundingBox().getCenter();
		Vec3 offset = middle.subtract(projectile.position());
		for (EndCrystal crystal : projectile.level().getEntitiesOfClass(EndCrystal.class, reach, Wards::warded)) {
			double[] bounce = CrystalWard.bounce(middle.x, middle.y, middle.z, v.x, v.y, v.z,
					crystal.getX(), crystal.getY() + CrystalWard.CENTER, crystal.getZ());
			if (bounce == null) continue;
			projectile.setPos(bounce[0] - offset.x, bounce[1] - offset.y, bounce[2] - offset.z);
			projectile.setDeltaMovement(bounce[3], bounce[4], bounce[5]);
			projectile.hasImpulse = true;
			if (projectile.level() instanceof ServerLevel level) {
				level.playSound(null, bounce[0], bounce[1], bounce[2], DragonSounds.WARD, SoundSource.NEUTRAL, 1.0F,
						0.9F + level.getRandom().nextFloat() * 0.2F);
				level.sendParticles(ParticleTypes.ENCHANT, bounce[0], bounce[1], bounce[2], SPARKS, 0.3, 0.3, 0.3, 0.6);
				level.sendParticles(ParticleTypes.END_ROD, bounce[0], bounce[1], bounce[2], 4, 0.1, 0.1, 0.1, 0.05);
			}
			return;
		}
	}
}
