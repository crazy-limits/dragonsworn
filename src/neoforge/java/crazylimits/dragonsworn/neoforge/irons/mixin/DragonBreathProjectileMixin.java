package crazylimits.dragonsworn.neoforge.irons.mixin;

import crazylimits.dragonsworn.mc.breath.BreathParticles;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iron's Spells 'n Spellbooks' Dragon's Breath spell breathes the dragon's own void flames instead of vanilla's
 * Dragon's Breath particles: the same stream as {@code BreathRender}'s, scaled to the spell's cone (about 6 blocks).
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.entity.spells.dragon_breath.DragonBreathProjectile")
public abstract class DragonBreathProjectileMixin {
	/** Out of the caster's mouth: Iron's own spot, this far ahead of the eyes along the look. */
	private static final double MOUTH_AHEAD = 1.6, MOUTH_DROP = 0.1;
	/** The jet's speed (blocks per tick): the flames burn out (smoke) about the cone's reach away. */
	private static final double JET = 0.65, JITTER = 0.05;
	private static final int FLAMES_PER_TICK = 5;

	@Inject(method = "spawnParticles", at = @At("HEAD"), cancellable = true)
	private void dragonsworn$voidBreath(CallbackInfo ci) {
		ci.cancel();
		Entity owner = ((Projectile) (Object) this).getOwner();
		Level level = owner == null ? null : owner.level();
		if (level == null || !level.isClientSide()) return;
		Vec3 dir = owner.getLookAngle().normalize();
		Vec3 mouth = owner.getEyePosition().add(0.0, -owner.getEyeHeight() * MOUTH_DROP, 0.0).add(dir.scale(MOUTH_AHEAD));
		// the flames carry the caster's own speed, so it never runs into its breath
		Vec3 carried = owner.getDeltaMovement();
		RandomSource random = level.getRandom();
		for (int i = 0; i < FLAMES_PER_TICK; i++) {
			// spread along this tick's stretch of the stream so it reads as one continuous jet
			double lead = random.nextDouble() * JET;
			double speed = JET * (0.85 + random.nextDouble() * 0.3);
			level.addParticle(BreathParticles.VOID_BREATH,
					mouth.x + dir.x * lead, mouth.y + dir.y * lead, mouth.z + dir.z * lead,
					carried.x + dir.x * speed + random.nextGaussian() * JITTER,
					carried.y + dir.y * speed + random.nextGaussian() * JITTER,
					carried.z + dir.z * speed + random.nextGaussian() * JITTER);
		}
	}
}
