package crazylimits.dragonsworn.mc.client;

import crazylimits.dragonsworn.mc.LevelGrid;
import crazylimits.dragonsworn.nav.Surface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What the limbs stand on, in the frame of the surface the dragon stands on ({@code nav/Surface}): on the
 * ground the tops of the blocks under a point, on a wall the faces of the blocks behind it (the wall seen
 * as ground). The limb layer works in that frame throughout ({@link LimbAnimator#surfaceFrame}), so the feet
 * grip a wall exactly as they stand on the ground. Plants and leaves the dragon tramples
 * ({@link LevelGrid#breakable}) are not ground.
 */
final class Footing {
	private final Level level;
	private final Surface.Face face;
	/** The body's turn onto the face (world = r local), as drawn. */
	private final double[] r;

	Footing(Level level, Surface.Face face, double[] r) {
		this.level = level;
		this.face = face;
		this.r = r.clone();
	}

	/** How far into a wall a point's face is searched (blocks), in steps of this, then refined. */
	private static final double MARCH = 6.0, STEP = 0.125;

	/**
	 * The top of the ground at or below the local point (x, y, z), searched {@code 6} blocks down (into a
	 * wall, straight in along its normal: any wall, sheer, stepped back or diagonal); NaN when nothing is
	 * near. Local heights, as the point's.
	 */
	double groundTop(double x, double y, double z) {
		if (!face.wall()) return groundTop(level, x, y, z);
		double[] w = Surface.apply(r, new double[]{x, y, z}, new double[3]);
		// the frame's up, as drawn (the turn onto the face may be under way)
		double nx = r[1], ny = r[4], nz = r[7];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (double d = 0.0; d <= MARCH; d += STEP) {
			if (!solid(pos, w[0] - nx * d, w[1] - ny * d, w[2] - nz * d)) continue;
			// the face is between the last step and this one
			double lo = Math.max(0.0, d - STEP), hi = d;
			for (int i = 0; i < 6; i++) {
				double mid = (lo + hi) / 2.0;
				if (solid(pos, w[0] - nx * mid, w[1] - ny * mid, w[2] - nz * mid)) hi = mid;
				else lo = mid;
			}
			return y - hi;
		}
		return Double.NaN;
	}

	/** Whether the world point is inside a block's collision shape (its bounds). */
	private boolean solid(BlockPos.MutableBlockPos pos, double x, double y, double z) {
		pos.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || LevelGrid.breakable(state)) return false;
		VoxelShape shape = state.getCollisionShape(level, pos);
		if (shape.isEmpty()) return false;
		double fx = x - pos.getX(), fy = y - pos.getY(), fz = z - pos.getZ();
		return fx >= shape.min(Direction.Axis.X) && fx <= shape.max(Direction.Axis.X) && fy >= shape.min(Direction.Axis.Y)
				&& fy <= shape.max(Direction.Axis.Y) && fz >= shape.min(Direction.Axis.Z) && fz <= shape.max(Direction.Axis.Z);
	}

	/**
	 * The top of the ground at or below (x, y, z) (world): what a foot stands on. NaN when nothing is near.
	 */
	static double groundTop(Level level, double x, double y, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
		for (int i = 0; i < 6; i++, pos.move(Direction.DOWN)) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || LevelGrid.breakable(state)) continue;
			VoxelShape shape = state.getCollisionShape(level, pos);
			if (!shape.isEmpty()) return pos.getY() + shape.max(Direction.Axis.Y);
		}
		return Double.NaN;
	}
}
