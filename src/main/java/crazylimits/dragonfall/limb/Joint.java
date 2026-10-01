package crazylimits.dragonfall.limb;

/**
 * One bone of a limb as the renderer draws it, in Blockbench editor space (pixels, degrees): what
 * GeckoLib shows is exactly the editor's view (see {@code tools/rig.py}). The bone turns about its
 * pivot, Euler Z, Y, X ({@code Rz . Ry . Rx}), after an animated position offset.
 */
public final class Joint {
	public final double[] pivot = new double[3], pos = new double[3], rot = new double[3];

	public Joint set(double[] pivot, double[] pos, double[] rot) {
		System.arraycopy(pivot, 0, this.pivot, 0, 3);
		System.arraycopy(pos, 0, this.pos, 0, 3);
		System.arraycopy(rot, 0, this.rot, 0, 3);
		return this;
	}

	/** The bone's matrix relative to its parent: {@code T(pos + pivot) . R(rot) . T(-pivot)}. */
	public double[] local() {
		return local(rot[0], rot[1], rot[2]);
	}

	/** {@link #local()} with other rotations. */
	public double[] local(double rx, double ry, double rz) {
		double[] m = Affine.rotationZYX(rx, ry, rz);
		double px = pivot[0], py = pivot[1], pz = pivot[2];
		// R . T(-pivot), then translate by pos + pivot
		m[3] = -(m[0] * px + m[1] * py + m[2] * pz) + px + pos[0];
		m[7] = -(m[4] * px + m[5] * py + m[6] * pz) + py + pos[1];
		m[11] = -(m[8] * px + m[9] * py + m[10] * pz) + pz + pos[2];
		return m;
	}
}
