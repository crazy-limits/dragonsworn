package crazylimits.dragonsworn.mc;

import crazylimits.dragonsworn.body.Parts;
import crazylimits.dragonsworn.body.Tail;
import crazylimits.dragonsworn.nav.BlockGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Vanilla's dragon has no physics; this gives it some (server). Its hull (head, necks, chest, hips, tail
 * root) never moves deeper into solid blocks it cannot break, flying ({@link #move}) or on its feet
 * ({@link #walk}, sliding along walls), and is pushed back out a little per tick when a turn or the pose
 * swung it into one ({@link #pushOut}). Leaves and plants ({@code #dragonsworn:dragon_breakable}) are
 * smashed instead, with their breaking sound, like a ravager ({@link #breakSoftBlocks}).
 */
public final class HullCollision {
	/** Parts that make up the solid hull: head, both necks, chest, hips, tail root. */
	private static final int[] HULL = {Parts.HEAD, Parts.NECK_UPPER, Parts.NECK_LOWER, Parts.CHEST, Parts.HIPS, Parts.TAIL_ROOT};
	/** Walking: the hull without the tail root (the tail lays itself round blocks, {@link Tail}). */
	private static final int[] WALK_HULL = {Parts.HEAD, Parts.NECK_UPPER, Parts.NECK_LOWER, Parts.CHEST, Parts.HIPS};
	/** Standing: what is pushed out of a wall it turned into (the head and neck bend away by themselves). */
	private static final int[] GROUND_CORE = {Parts.NECK_LOWER, Parts.CHEST, Parts.HIPS};
	/** How much smaller than its part each hull box is (blocks): grazing a block is not being in it. */
	private static final double HULL_INSET = 0.15;
	/** How far a hull caught in a block is pushed out per tick, at most (blocks; tried smallest first). */
	private static final double[] PUSH_OUT = {0.15, 0.4, 0.8};
	/** Ticks wedged in terrain (nothing frees it) before it may move through it to get out. */
	private static final int ESCAPE_TICKS = 60;
	/** How much a wedged hull escaping its block is lifted per tick (blocks). */
	private static final double ESCAPE_LIFT = 0.08;
	private static final int MAX_BREAKS_PER_TICK = 64;
	private static final Vec3[] SIDEWAYS = {new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 0, -1),
			new Vec3(0.7071, 0, 0.7071), new Vec3(-0.7071, 0, 0.7071), new Vec3(0.7071, 0, -0.7071), new Vec3(-0.7071, 0, -0.7071)};
	/** Up first: out of a ceiling or off the ground is the likelier way out in flight. */
	private static final Vec3[] AROUND = {new Vec3(0, 1, 0), SIDEWAYS[0], SIDEWAYS[1], SIDEWAYS[2], SIDEWAYS[3],
			SIDEWAYS[4], SIDEWAYS[5], SIDEWAYS[6], SIDEWAYS[7], new Vec3(0, -1, 0)};

	private final DragonBrain brain;
	private final AirRoute route;
	private int stuckTicks, wedgedTicks;

	HullCollision(DragonBrain brain, AirRoute route) {
		this.brain = brain;
		this.route = route;
	}

	/**
	 * Moves by {@code delta}, the hull stopped by solid blocks: blocked axes are dropped (slides along
	 * walls), the vanilla collision flags are set (vanilla phases pick a new target on them) and the
	 * route is replanned. A hull already caught in a block (the pose swung it there) may move in any way
	 * that does not take it deeper, so it never passes through; only one wedged for
	 * {@link #ESCAPE_TICKS} (nothing pushes it out) moves freely to get out.
	 */
	public void move(Vec3 delta, boolean collide) {
		EnderDragon dragon = brain.dragon();
		if (!collide) {
			dragon.move(MoverType.SELF, delta);
			return;
		}
		BlockGrid grid = brain.grid();
		AABB[] hull = hull(HULL);
		int inside = overlap(grid, hull, Vec3.ZERO);
		if (inside > 0 && wedgedTicks > ESCAPE_TICKS) {
			dragon.move(MoverType.SELF, delta.add(0.0, ESCAPE_LIFT, 0.0));
			stuck();
			return;
		}
		if (overlap(grid, hull, delta) <= inside) {
			dragon.move(MoverType.SELF, delta);
			dragon.horizontalCollision = dragon.verticalCollision = false;
			if (stuckTicks > 0) stuckTicks--;
			return;
		}
		double my = overlap(grid, hull, new Vec3(0.0, delta.y, 0.0)) > inside ? 0.0 : delta.y;
		double mx = overlap(grid, hull, new Vec3(delta.x, my, 0.0)) > inside ? 0.0 : delta.x;
		double mz = overlap(grid, hull, new Vec3(mx, my, delta.z)) > inside ? 0.0 : delta.z;
		dragon.move(MoverType.SELF, new Vec3(mx, my, mz));
		dragon.horizontalCollision = mx != delta.x || mz != delta.z;
		dragon.verticalCollision = my != delta.y;
		Vec3 v = dragon.getDeltaMovement();
		dragon.setDeltaMovement(mx != delta.x ? 0.0 : v.x, my != delta.y ? 0.0 : v.y, mz != delta.z ? 0.0 : v.z);
		stuck();
	}

	/**
	 * A step on its feet by dx, dz (server): the body (head to hips) is stopped by solid blocks the
	 * same way, sliding along a wall. Returns false when it was stopped (wholly or along one axis).
	 */
	public boolean walk(double dx, double dz) {
		EnderDragon dragon = brain.dragon();
		BlockGrid grid = brain.grid();
		AABB[] hull = hull(WALK_HULL);
		int inside = overlap(grid, hull, Vec3.ZERO);
		Vec3 delta = new Vec3(dx, 0.0, dz);
		double mx = dx, mz = dz;
		if (overlap(grid, hull, delta) > inside) {
			mx = overlap(grid, hull, new Vec3(dx, 0.0, 0.0)) > inside ? 0.0 : dx;
			mz = overlap(grid, hull, new Vec3(mx, 0.0, dz)) > inside ? 0.0 : dz;
		}
		dragon.setPos(dragon.getX() + mx, dragon.getY(), dragon.getZ() + mz);
		boolean free = mx == dx && mz == dz;
		dragon.horizontalCollision = !free;
		return free;
	}

	/** Ticks lately spent bumping into terrain (counts down while it moves freely). */
	public int stuckTicks() {
		return stuckTicks;
	}

	private void stuck() {
		stuckTicks++;
		route.replanNow();
	}

	/**
	 * The pose just placed may have turned or swung the hull into a wall (a turn swings the head and
	 * hips, a wingbeat heaves the body): the dragon is pushed back out, a little per tick, the way that
	 * frees the most. On its feet ({@code ground}) only sideways (its height is the ground's); in a phase
	 * that does not {@code collide}, never.
	 */
	void pushOut(EnderDragonPart[] parts, boolean ground, boolean collide) {
		if (!ground && !collide) {
			wedgedTicks = 0;
			return;
		}
		BlockGrid grid = brain.grid();
		AABB[] hull = hull(ground ? GROUND_CORE : HULL);
		int inside = overlap(grid, hull, Vec3.ZERO);
		if (inside == 0) {
			wedgedTicks = 0;
			return;
		}
		Vec3 best = null;
		int least = inside;
		for (double d : PUSH_OUT) {
			for (Vec3 dir : ground ? SIDEWAYS : AROUND) {
				Vec3 shift = dir.scale(d);
				int n = overlap(grid, hull, shift);
				if (n < least) {
					least = n;
					best = shift;
				}
			}
			if (least == 0) break;
		}
		if (best == null) {
			wedgedTicks++;
			return;
		}
		wedgedTicks = 0;
		EnderDragon dragon = brain.dragon();
		dragon.setPos(dragon.getX() + best.x, dragon.getY() + best.y, dragon.getZ() + best.z);
		for (EnderDragonPart part : parts) part.setPos(part.getX() + best.x, part.getY() + best.y, part.getZ() + best.z);
	}

	/** Smashes the soft blocks (leaves, plants) the parts touch, as a ravager does, when mob griefing is on. */
	void breakSoftBlocks(EnderDragonPart[] parts) {
		EnderDragon dragon = brain.dragon();
		Level level = dragon.level();
		if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
		int broken = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (EnderDragonPart part : parts) {
			AABB box = part.getBoundingBox().inflate(0.25);
			for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
				for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
					for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
						pos.set(x, y, z);
						BlockState state = level.getBlockState(pos);
						if (state.isAir() || !LevelGrid.breakable(state)) continue;
						// drops, the block's breaking sound and particles: what a ravager does to leaves
						if (level.destroyBlock(pos.immutable(), true, dragon) && ++broken >= MAX_BREAKS_PER_TICK) return;
					}
				}
			}
		}
	}

	/** Vanilla's wall check, without the demolition: solid, unbreakable blocks in {@code box}. */
	public boolean inWall(AABB box) {
		return overlap(brain.grid(), box, false) > 0;
	}

	private AABB[] hull(int[] ids) {
		EnderDragonPart[] parts = brain.dragon().getSubEntities();
		AABB[] out = new AABB[ids.length];
		for (int i = 0; i < ids.length; i++) out[i] = parts[ids[i]].getBoundingBox().deflate(HULL_INSET);
		return out;
	}

	/** Solid blocks the hull, moved by {@code delta}, is in (counted per box). */
	private static int overlap(BlockGrid grid, AABB[] hull, Vec3 delta) {
		int n = 0;
		for (AABB box : hull) n += overlap(grid, box.move(delta), true);
		return n;
	}

	/** How many solid, unbreakable blocks {@code box} is in (stops at the first when {@code all} is false). */
	private static int overlap(BlockGrid grid, AABB box, boolean all) {
		int n = 0;
		for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX - 1e-7); x++) {
			for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY - 1e-7); y++) {
				for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ - 1e-7); z++) {
					if (!grid.blocked(x, y, z)) continue;
					if (!all) return 1;
					n++;
				}
			}
		}
		return n;
	}
}
