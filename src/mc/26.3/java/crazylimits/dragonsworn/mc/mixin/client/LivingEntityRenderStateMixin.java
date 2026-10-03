package crazylimits.dragonsworn.mc.mixin.client;

import crazylimits.dragonsworn.mc.client.LyingPrey;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries {@link LyingPrey}'s yaw from the state's extraction to the drawing. */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements LyingPrey {
	@Unique
	private double dragonsworn$lyingYaw = Double.NaN;

	@Override
	public double dragonsworn$lyingYaw() {
		return dragonsworn$lyingYaw;
	}

	@Override
	public void dragonsworn$setLyingYaw(double yaw) {
		dragonsworn$lyingYaw = yaw;
	}
}
