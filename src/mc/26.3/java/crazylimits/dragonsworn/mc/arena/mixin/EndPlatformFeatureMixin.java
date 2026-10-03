package crazylimits.dragonsworn.mc.arena.mixin;

import crazylimits.dragonsworn.arena.EntrancePlatform;
import crazylimits.dragonsworn.arena.Monolith;
import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.mc.arena.OtherMods;
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
 * Builds the End's entrance platform as Dragonsworn's sphere ({@link EntrancePlatform}) instead of vanilla's
 * 5x5 slab, both when the End generates and each time the portal drops someone there. Off in the config
 * ({@code end_island.entrance_platform}) or when another mod also changes {@link EndPlatformFeature}: then theirs (or vanilla's).
 */
@Mixin(EndPlatformFeature.class)
public abstract class EndPlatformFeatureMixin {
	@Inject(method = "createEndPlatform", at = @At("HEAD"), cancellable = true)
	private static void dragonsworn$sphere(ServerLevelAccessor level, BlockPos origin, boolean dropBlocks, CallbackInfo ci) {
		if (!DragonConfig.ENTRANCE_PLATFORM.get() || OtherMods.rebuild(EndPlatformFeature.class)) return;
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
