package crazylimits.dragonsworn.mc.breath.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A void flame: its sprite frame follows its age. Its quad keeps one size: the
 * puff grows inside its sprites (a small ball in the middle at first, the whole sprite at the end), so its
 * texels stay the same size all its life. Both
 * kinds burn out into smoke (sprites from {@code tools/particles.py}): a ball of purple fire that a ring of
 * smoke closes in on, then only smoke clouds. {@link Breath} is a puff of the stream out of the mouth;
 * {@link Cloud} a smaller, quicker one out of a breath cloud (the fireball's, the perched breath's) or an
 * ember in the mouth. The fire glows; the smoke, from {@link #smokeFrom} of its life, is lit by the world,
 * slows down and drifts up. Nothing is see-through: the sprites are opaque (every texel drawn or empty) and
 * drawn opaque; the smoke never fades, it breaks up in its sprites into smaller and smaller puffs that vanish.
 */
public class VoidFlameParticle extends SingleQuadParticle {
	private final SpriteSet sprites;
	private final float rise;
	/** From this share of its life it is smoke (0 for none: a flame throughout). */
	private float smokeFrom;

	protected VoidFlameParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites,
			int lifetime, float size, float friction, float rise) {
		super(level, x, y, z, sprites.first());
		this.sprites = sprites;
		this.xd = vx;
		this.yd = vy;
		this.zd = vz;
		this.lifetime = lifetime;
		this.friction = friction;
		this.rise = rise;
		this.gravity = 0.0F;
		this.hasPhysics = true;
		this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
		this.quadSize = size;
		setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		if (removed) return;
		setSpriteFromAge(sprites);
		float k = (float) age / lifetime;
		boolean smoke = smokeFrom > 0.0F && k >= smokeFrom;
		yd += smoke ? rise * 3.0F : rise;
		if (smoke) {
			xd *= 0.9;
			zd *= 0.9;
		}
		// flames that hit the ground spread out along it instead of stopping dead
		if (onGround) {
			xd *= 1.08;
			zd *= 1.08;
		}
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.OPAQUE;
	}

	@Override
	protected int getLightCoords(float partialTick) {
		if (smokeFrom > 0.0F && (age + partialTick) / lifetime >= smokeFrom) return super.getLightCoords(partialTick);
		return 0xF000F0;
	}

	public static final class Breath implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Breath(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
				RandomSource r) {
			// fire for three quarters of its life (sprites 0-12 of 18), then smoke that quickly breaks up (13-17)
			VoidFlameParticle puff = new VoidFlameParticle(level, x, y, z, vx, vy, vz, sprites, 24 + r.nextInt(11),
					1.8F + r.nextFloat() * 0.8F, 0.95F, 0.003F);
			puff.smokeFrom = 0.69F;
			return puff;
		}
	}

	public static final class Cloud implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Cloud(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
				RandomSource r) {
			// the cloud hands out a little sideways drift; the flame mostly licks upward, then its smoke
			// (sprites 8-11 of 12) rises and breaks up
			VoidFlameParticle flame = new VoidFlameParticle(level, x, y, z, vx * 0.4, vy + 0.03, vz * 0.4, sprites, 12 + r.nextInt(8),
					0.5F + r.nextFloat() * 0.25F, 0.9F, 0.004F);
			flame.smokeFrom = 0.65F;
			return flame;
		}
	}
}
