package crazylimits.dragonfall.mc.arena;

import crazylimits.dragonfall.arena.Monolith;
import crazylimits.dragonfall.mc.breath.DragonFire;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration;

/** Places a {@link Monolith} for one of the End's spikes, and its crystal where vanilla puts it, over dragon fire. */
public final class Monoliths {
	/** The island's surface is looked for from here down (the spikes start well above it). */
	private static final int SEARCH_TOP = 80, SEARCH_DEPTH = 40;

	private Monoliths() {}

	public static Monolith build(ServerLevelAccessor level, SpikeFeature.EndSpike spike) {
		return Monolith.build(spike.getCenterX(), spike.getCenterZ(), spike.getRadius(), spike.getHeight(),
			level.getMinBuildHeight(), (x, z) -> surface(level, x, z));
	}

	public static void place(ServerLevelAccessor level, RandomSource random, SpikeConfiguration config, SpikeFeature.EndSpike spike) {
		Monolith monolith = build(level, spike);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		monolith.forEach((x, y, z, block) -> level.setBlock(pos.set(x, y, z), state(block), Block.UPDATE_ALL));

		// the crystal, as vanilla places it
		EndCrystal crystal = EntityType.END_CRYSTAL.create(level.getLevel());
		if (crystal != null) {
			crystal.setBeamTarget(config.getCrystalBeamTarget());
			crystal.setInvulnerable(config.isCrystalInvulnerable());
			crystal.moveTo(spike.getCenterX() + 0.5, monolith.crystalY(), spike.getCenterZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			level.addFreshEntity(crystal);
			BlockPos at = crystal.blockPosition();
			level.setBlock(at.below(), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(at, DragonFire.crystalFire(level, at), Block.UPDATE_ALL);
		}
	}

	/** The y of the top End stone block in a column (what is on it, an old spire included, is skipped). */
	private static int surface(ServerLevelAccessor level, int x, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, SEARCH_TOP, z);
		for (int i = 0; i < SEARCH_DEPTH; i++, pos.move(0, -1, 0))
			if (level.getBlockState(pos).is(Blocks.END_STONE)) return pos.getY();
		return Monolith.Ground.NONE;
	}

	private static BlockState state(Monolith.Block block) {
		return switch (block) {
			case AIR -> Blocks.AIR.defaultBlockState();
			case OBSIDIAN -> Blocks.OBSIDIAN.defaultBlockState();
			case BEDROCK -> Blocks.BEDROCK.defaultBlockState();
		};
	}
}
