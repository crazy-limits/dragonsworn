package crazylimits.dragonsworn.mc.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The dragon egg gets the sniffer egg's hatch stages (`hatch` 0..2, vanilla's {@link BlockStateProperties#HATCH}):
 * the blockstate shows each stage cracked further (`tools/egg.py`). Nothing raises it yet; every egg is placed,
 * dropped and loaded at 0 (a saved egg has no `hatch` and takes the default).
 */
@Mixin(DragonEggBlock.class)
public abstract class DragonEggBlockMixin extends FallingBlock {
	private DragonEggBlockMixin(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.HATCH);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void dragonsworn$unhatched(BlockBehaviour.Properties properties, CallbackInfo ci) {
		registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HATCH, 0));
	}
}
