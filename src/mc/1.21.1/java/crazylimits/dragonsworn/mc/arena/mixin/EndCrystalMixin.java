package crazylimits.dragonsworn.mc.arena.mixin;

import crazylimits.dragonsworn.mc.breath.DragonFire;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * An End crystal standing on bedrock (the spires' crystals, the ones set on the exit portal to respawn the
 * dragon) keeps dragon fire burning under it instead of common fire; anywhere else the fire is vanilla's.
 */
@Mixin(EndCrystal.class)
public abstract class EndCrystalMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/BaseFireBlock;getState(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
	private BlockState dragonsworn$crystalFire(BlockGetter level, BlockPos pos) {
		return DragonFire.crystalFire(level, pos);
	}
}
