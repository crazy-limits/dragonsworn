package crazylimits.dragonsworn.mc;

import crazylimits.dragonsworn.nav.BlockGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The world for the dragon's navigation ({@link BlockGrid}). Blocks in {@link #BREAKABLE}
 * ({@code #dragonsworn:dragon_breakable}: leaves, plants, vines, snow...) are empty to it: it smashes
 * through them like a ravager. Everything else with a collision shape stops it. Unloaded chunks are
 * open air with no ground. Make one per tick (or per decision): it caches nothing between ticks.
 */
public final class LevelGrid implements BlockGrid {
	public static final TagKey<Block> BREAKABLE = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("dragonsworn", "dragon_breakable"));

	private final Level level;
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

	public LevelGrid(Level level) {
		this.level = level;
	}

	public static boolean breakable(BlockState state) {
		return state.is(BREAKABLE) && !(state.getBlock() instanceof LiquidBlock);
	}

	@Override
	public boolean blocked(int x, int y, int z) {
		if (y < level.getMinY()) return true;
		if (y > level.getMaxY()) return false;
		if (!level.hasChunk(x >> 4, z >> 4)) return false;
		pos.set(x, y, z);
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || breakable(state)) return false;
		return !state.getCollisionShape(level, pos).isEmpty();
	}

	@Override
	public int ground(int x, int z) {
		if (!level.hasChunk(x >> 4, z >> 4)) return NO_GROUND;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		int min = level.getMinY();
		// step down through what the dragon would trample (bamboo, snow...)
		while (y > min) {
			pos.set(x, y - 1, z);
			BlockState below = level.getBlockState(pos);
			if (!below.getFluidState().isEmpty()) return NO_GROUND;
			if (!below.isAir() && !breakable(below) && !below.getCollisionShape(level, pos).isEmpty()) return y;
			y--;
		}
		return NO_GROUND;
	}
}
