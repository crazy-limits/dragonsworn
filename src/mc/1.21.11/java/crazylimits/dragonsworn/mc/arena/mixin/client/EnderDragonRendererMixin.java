package crazylimits.dragonsworn.mc.arena.mixin.client;

import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The crystal beam (the dragon's healing beam, the crystals' own beams), vanilla's tube exactly, except that
 * its texture (the runes, {@code tools/crystal_beam.py}) flows from the crystal toward the beam's other end
 * (the dragon): vanilla scrolls it the other way. The tube starts (thin) at the receiving end and reaches the
 * crystal at its length; v grows toward the crystal, and vanilla scrolls by minus the time: run with the
 * time negated, it adds where vanilla subtracts.
 */
@Mixin(EnderDragonRenderer.class)
public abstract class EnderDragonRendererMixin {
	@ModifyVariable(method = "submitCrystalBeams", at = @At("HEAD"), argsOnly = true, ordinal = 3)
	private static float dragonsworn$beamTowardTheDragon(float time) {
		return -time;
	}
}
