package crazylimits.dragonsworn.mc.arena.mixin;

import crazylimits.dragonsworn.mc.arena.Wards;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Each time the fight counts its crystals, those on spires Dragonsworn did not build get their wards ({@link Wards#wardFound}). */
@Mixin(EnderDragonFight.class)
public abstract class EnderDragonFightMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Inject(method = "updateCrystalCount", at = @At("TAIL"))
	private void dragonsworn$ward(CallbackInfo ci) {
		Wards.wardFound(level);
	}
}
