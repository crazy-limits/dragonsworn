package crazylimits.dragonfall.mc.arena.mixin;

import crazylimits.dragonfall.arena.EntrancePlatform;
import crazylimits.dragonfall.arena.Monolith;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Builds the End's entrance platform as Dragonfall's sphere ({@link EntrancePlatform}) instead of vanilla's
 * 5x5 slab, both when the End generates and each time the portal drops someone there.
 */
@Mixin(EndPlatformFeature.class)
public abstract class EndPlatformFeatureMixin {
	@Inject(method = "createEndPlatform", at = @At("HEAD"), cancellable = true)
	private static void dragonfall$sphere(ServerLevelAccessor level, BlockPos origin, boolean dropBlocks, CallbackInfo ci) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		EntrancePlatform.forEach(origin.getX(), origin.getY(), origin.getZ(), (x, y, z, block) -> {
			BlockState state = (block == Monolith.Block.OBSIDIAN ? Blocks.OBSIDIAN : Blocks.AIR).defaultBlockState();
			if (level.getBlockState(pos.set(x, y, z)).is(state.getBlock())) return;
			if (dropBlocks) level.destroyBlock(pos, true, null);
			level.setBlock(pos, state, Block.UPDATE_ALL);
		});
		ci.cancel();
	}
}
