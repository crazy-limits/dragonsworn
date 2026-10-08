package crazylimits.dragonsworn.mc.breath.mixin.client;

import crazylimits.dragonsworn.mc.breath.client.VoidFlameParticle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the void smoke ({@link VoidFlameParticle#SMOKE}) after vanilla's translucent particles: vanilla draws only
 * the render types it lists (NeoForge draws every type, ordered by this list).
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Shadow
	@Final
	@Mutable
	private static List<ParticleRenderType> RENDER_ORDER;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void dragonsworn$smokeLayer(CallbackInfo ci) {
		List<ParticleRenderType> order = new ArrayList<>(RENDER_ORDER);
		order.add(order.indexOf(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT) + 1, VoidFlameParticle.SMOKE);
		RENDER_ORDER = List.copyOf(order);
	}
}
