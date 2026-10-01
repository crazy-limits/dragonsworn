package crazylimits.dragonfall.mc.breath.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A void flame, after Ice and Fire's dragon fire: full bright, cooling from white to violet over its
 * life (the sprite frame follows its age), growing as it spreads and fading out at the end.
 * {@link Breath} is the stream out of the mouth; {@link Cloud} is a flame licking up out of a breath cloud.
 */
public class VoidFlameParticle extends TextureSheetParticle {
	private final SpriteSet sprites;
	private final float startSize, endSize;
	private final float rise;

	protected VoidFlameParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites,
			int lifetime, float startSize, float endSize, float friction, float rise) {
		super(level, x, y, z);
		this.sprites = sprites;
		this.xd = vx;
		this.yd = vy;
		this.zd = vz;
		this.lifetime = lifetime;
		this.startSize = startSize;
		this.endSize = endSize;
		this.friction = friction;
		this.rise = rise;
		this.gravity = 0.0F;
		this.hasPhysics = true;
		this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
		this.quadSize = startSize;
		setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		if (removed) return;
		setSpriteFromAge(sprites);
		float k = (float) age / lifetime;
		quadSize = Mth.lerp(Mth.sqrt(k), startSize, endSize);
		alpha = k < 0.7F ? 1.0F : 1.0F - (k - 0.7F) / 0.3F;
		yd += rise;
		// flames that hit the ground spread out along it instead of stopping dead
		if (onGround) {
			xd *= 1.08;
			zd *= 1.08;
		}
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	protected int getLightColor(float partialTick) {
		return 0xF000F0;
	}

	public static final class Breath implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Breath(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
			RandomSource r = level.random;
			return new VoidFlameParticle(level, x, y, z, vx, vy, vz, sprites, 18 + r.nextInt(10),
					0.25F + r.nextFloat() * 0.15F, 1.1F + r.nextFloat() * 0.6F, 0.95F, 0.004F);
		}
	}

	public static final class Cloud implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Cloud(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
			RandomSource r = level.random;
			// the cloud hands out a little sideways drift; the flame mostly licks upward
			return new VoidFlameParticle(level, x, y, z, vx * 0.4, vy + 0.03, vz * 0.4, sprites, 8 + r.nextInt(8),
					0.12F + r.nextFloat() * 0.1F, 0.28F + r.nextFloat() * 0.14F, 0.9F, 0.006F);
		}
	}
}
