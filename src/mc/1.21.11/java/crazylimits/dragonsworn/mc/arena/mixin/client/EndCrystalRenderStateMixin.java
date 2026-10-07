package crazylimits.dragonsworn.mc.arena.mixin.client;

import crazylimits.dragonsworn.mc.arena.client.WardState;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries {@link WardState}'s flag from the state's extraction to the drawing. */
@Mixin(EndCrystalRenderState.class)
public abstract class EndCrystalRenderStateMixin implements WardState {
	@Unique
	private boolean dragonsworn$warded;

	@Override
	public boolean dragonsworn$warded() {
		return dragonsworn$warded;
	}

	@Override
	public void dragonsworn$setWarded(boolean warded) {
		dragonsworn$warded = warded;
	}
}
