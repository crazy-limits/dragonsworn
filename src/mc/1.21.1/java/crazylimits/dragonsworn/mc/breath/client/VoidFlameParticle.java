package crazylimits.dragonsworn.mc.breath.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
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
 * slows down, drifts up and fades out while its billows shrink in the sprites. The sprites are opaque (every
 * texel drawn or empty); only the smoke's fading makes the particle see-through.
 * The fire is drawn opaque; the smoke in {@link #SMOKE}, translucent without writing depth, so particles behind it
 * still show (vanilla's translucent particles write depth and hide whatever is drawn after them). A particle's
 * render type is fixed when it is added: a puff turning to smoke hands over to a copy in {@link #SMOKE}.
 */
public class VoidFlameParticle extends TextureSheetParticle {
	/** Translucent particles that write no depth, drawn after vanilla's translucent ones ({@code mixin.client.ParticleEngineMixin}). */
	public static final ParticleRenderType SMOKE = new ParticleRenderType() {
		@Override
		public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
			RenderSystem.depthMask(false);
			RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
		}

		@Override
		public String toString() {
			return "DRAGONSWORN_VOID_SMOKE";
		}
	};

	private final SpriteSet sprites;
	private final float rise;
	/** From this share of its life it is smoke (0 for none: a flame throughout). */
	private float smokeFrom;
	/** Drawn in {@link #SMOKE}: the copy a puff hands over to once it is smoke. */
	private boolean smokeLayer;

	protected VoidFlameParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites,
			int lifetime, float size, float friction, float rise) {
		super(level, x, y, z);
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
			alpha = 1.0F - (k - smokeFrom) / (1.0F - smokeFrom);
			xd *= 0.9;
			zd *= 0.9;
		}
		// flames that hit the ground spread out along it instead of stopping dead
		if (onGround) {
			xd *= 1.08;
			zd *= 1.08;
		}
		if (smoke && !smokeLayer) toSmokeLayer();
	}

	private void toSmokeLayer() {
		VoidFlameParticle copy = new VoidFlameParticle(level, x, y, z, xd, yd, zd, sprites, lifetime, quadSize, friction, rise);
		copy.smokeFrom = smokeFrom;
		copy.smokeLayer = true;
		copy.xo = xo;
		copy.yo = yo;
		copy.zo = zo;
		copy.age = age;
		copy.roll = roll;
		copy.oRoll = oRoll;
		copy.alpha = alpha;
		copy.onGround = onGround;
		copy.setSpriteFromAge(sprites);
		Minecraft.getInstance().particleEngine.add(copy);
		remove();
	}

	@Override
	public ParticleRenderType getRenderType() {
		return smokeLayer ? SMOKE : ParticleRenderType.PARTICLE_SHEET_OPAQUE;
	}

	@Override
	protected int getLightColor(float partialTick) {
		if (smokeFrom > 0.0F && (age + partialTick) / lifetime >= smokeFrom) return super.getLightColor(partialTick);
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
			// fire for three quarters of its life (sprites 0-12 of 18), then smoke that shrinks and fades (13-17)
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
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
			RandomSource r = level.random;
			// the cloud hands out a little sideways drift; the flame mostly licks upward, then its smoke
			// (sprites 9-11 of 12) rises, shrinks and fades
			VoidFlameParticle flame = new VoidFlameParticle(level, x, y, z, vx * 0.4, vy + 0.03, vz * 0.4, sprites, 12 + r.nextInt(8),
					0.5F + r.nextFloat() * 0.25F, 0.9F, 0.004F);
			flame.smokeFrom = 0.8F;
			return flame;
		}
	}
}
