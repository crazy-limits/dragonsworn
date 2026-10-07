package crazylimits.dragonsworn.mc.client;

import crazylimits.dragonsworn.limb.Affine;
import crazylimits.dragonsworn.limb.BodyFrame;
import crazylimits.dragonsworn.mc.LevelGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;

/**
 * How far a limb is inside the ground: over every corner of every cube under a bone, the most the
 * ground under that corner is above it (blocks; negative: the limb is clear by that much). The world
 * columns are looked up once each.
 */
public final class GroundClearance {
	private final Footing footing;
	private final BodyFrame frame;
	private final Map<Long, Double> columns = new HashMap<>();
	private final double[] p = new double[3];

	/** {@code frame}: the model in the frame of the surface it stands on, as {@code footing} measures it. */
	GroundClearance(Footing footing, BodyFrame frame) {
		this.footing = footing;
		this.frame = frame;
	}

	/** The most any part of {@code bone}'s subtree is inside the ground; {@code parent}: its parent's model matrix. */
	double of(GeoBones.Bone bone, double[] parent) {
		return of(bone, parent, Double.NEGATIVE_INFINITY);
	}

	private double of(GeoBones.Bone bone, double[] parent, double worst) {
		if (bone.isHidden()) return worst;
		double[] m = Affine.mul(parent, GeoBones.joint(bone).local());
		for (GeoBones.Cube cube : bone.getCubes()) {
			double[] cm = Affine.mul(m, GeoBones.cubeMatrix(cube));
			for (Vector3f vertex : cube.vertices()) {
				p[0] = vertex.x * 16.0;
				p[1] = vertex.y * 16.0;
				p[2] = vertex.z * 16.0;
				frame.toWorld(Affine.apply(cm, p, p), p);
				double g = ground(p[0], p[1], p[2]);
				if (!Double.isNaN(g)) worst = Math.max(worst, g - p[1]);
			}
		}
		for (GeoBones.Bone child : bone.getChildBones()) worst = of(child, m, worst);
		return worst;
	}

	/** The ground top in the column at x, z, searched down from a little above y. */
	private double ground(double x, double y, double z) {
		int bx = Mth.floor(x), bz = Mth.floor(z), by = Mth.floor(y);
		long key = ((long) bx << 38) ^ ((long) (bz & 0x3FFFFFF) << 12) ^ (by & 0xFFF);
		Double known = columns.get(key);
		if (known == null) {
			known = footing.groundTop(x, y + 2.0, z);
			columns.put(key, known);
		}
		return known;
	}

	/**
	 * The top of the ground at or below (x, y, z): what a foot stands on. Plants and leaves the dragon
	 * tramples ({@link LevelGrid#breakable}) are not ground; NaN when nothing is near.
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
