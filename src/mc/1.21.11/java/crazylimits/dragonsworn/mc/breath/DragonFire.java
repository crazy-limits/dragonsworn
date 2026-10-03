package crazylimits.dragonsworn.mc.breath;

import com.mojang.serialization.MapCodec;
import crazylimits.dragonsworn.Dragonsworn;
import crazylimits.dragonsworn.config.DragonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiConsumer;

/**
 * Dragon fire ({@code dragonsworn:dragon_fire}): the purple fire every fire attack leaves where it lands
 * (the stream breath, the breath pass, the perched cloud breath, the fireball). Soul fire tinted violet
 * ({@code tools/dragon_fire.py}) that burns {@link #DAMAGE} a touch, three times what fire does. Like soul
 * fire it does not spread or burn blocks away; it stands on any solid top and burns out by itself after
 * {@link #MIN_TICKS}..{@link #MAX_TICKS}, except on bedrock, where it burns for good (as fire does in the
 * End): that is the fire under the End crystals ({@link #crystalFire}). The dragon walks through its own
 * fire unhurt.
 */
public final class DragonFire extends BaseFireBlock {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(Dragonsworn.MOD_ID, "dragon_fire");
	public static final MapCodec<DragonFire> CODEC = simpleCodec(DragonFire::new);
	/** Vanilla fire hurts 1 a touch, soul fire 2. */
	public static final float DAMAGE = 3.0F;
	/** How long a patch burns (ticks). */
	static final int MIN_TICKS = 100, MAX_TICKS = 200;

	/** The block, created by {@link #register} (blocks are made while the loader registers them). */
	public static Block BLOCK;

	public DragonFire(BlockBehaviour.Properties properties) {
		super(properties, DAMAGE);
	}

	/** Creates the block and hands it to the loader's registry. */
	public static void register(BiConsumer<Identifier, Block> registry) {
		BLOCK = new DragonFire(BlockBehaviour.Properties.of()
				.setId(ResourceKey.create(Registries.BLOCK, ID))
				.mapColor(MapColor.COLOR_PURPLE)
				.replaceable()
				.noCollision()
				.instabreak()
				.lightLevel(state -> 12)
				.sound(SoundType.WOOL)
				.pushReaction(PushReaction.DESTROY)
				.noLootTable());
		registry.accept(ID, BLOCK);
	}

	@Override
	protected MapCodec<DragonFire> codec() {
		return CODEC;
	}

	@Override
	protected boolean canBurn(BlockState state) {
		return true;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return canStandOn(level, pos);
	}

	private static boolean canStandOn(LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
		return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
	}

	/** Not vanilla fire's: no nether portal is lit by it. It burns out on its own. */
	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
		if (old.is(state.getBlock())) return;
		if (!state.canSurvive(level, pos)) {
			level.removeBlock(pos, false);
			return;
		}
		if (onBedrock(level, pos)) return;
		level.scheduleTick(pos, this, Mth.nextInt(level.getRandom(), MIN_TICKS, MAX_TICKS));
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!onBedrock(level, pos)) level.removeBlock(pos, false);
	}

	private static boolean onBedrock(BlockGetter level, BlockPos pos) {
		return level.getBlockState(pos.below()).is(Blocks.BEDROCK);
	}

	/** The fire an End crystal keeps under itself: dragon fire on bedrock, common fire anywhere else. */
	public static BlockState crystalFire(BlockGetter level, BlockPos pos) {
		return BLOCK != null && onBedrock(level, pos) ? BLOCK.defaultBlockState() : BaseFireBlock.getState(level, pos);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects,
			boolean intersects) {
		if (entity instanceof EnderDragon || entity instanceof EnderDragonPart) return;
		super.entityInside(state, level, pos, entity, effects, intersects);
	}

	/**
	 * Server: sets dragon fire on the ground round {@code center}, out to {@code radius} blocks: in each
	 * column (with chance {@code chance}) on the first open block over a solid top, from a little above
	 * the center down to a few blocks below it. Only air is set alight.
	 */
	public static void spread(Level level, Vec3 center, double radius, float chance) {
		if (level.isClientSide() || BLOCK == null || !DragonConfig.DRAGON_FIRE.get()) return;
		RandomSource random = level.getRandom();
		int r = Mth.ceil(radius), cy = Mth.floor(center.y);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = Mth.floor(center.x) - r; x <= Mth.floor(center.x) + r; x++) {
			for (int z = Mth.floor(center.z) - r; z <= Mth.floor(center.z) + r; z++) {
				double dx = x + 0.5 - center.x, dz = z + 0.5 - center.z;
				if (dx * dx + dz * dz > radius * radius || random.nextFloat() >= chance) continue;
				for (int y = cy + 2; y >= cy - 3; y--) {
					pos.set(x, y, z);
					if (!level.getBlockState(pos).isAir()) continue;
					if (!canStandOn(level, pos)) continue;
					level.setBlock(pos, BLOCK.defaultBlockState(), Block.UPDATE_ALL);
					break;
				}
			}
		}
	}
}
