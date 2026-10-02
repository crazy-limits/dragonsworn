package crazylimits.dragonsworn.limb;

import crazylimits.dragonsworn.body.PartSolver;

/**
 * The model's placement in the world, exactly as the renderer draws it: model pixels (editor space)
 * are rolled and pitched about the middle of the torso ({@link PartSolver#CENTER_Y}, {@code CENTER_Z}),
 * turned by the body's yaw and put at the dragon's position. Maps points both ways.
 */
public final class BodyFrame {
	private double x, y, z;
	private double cy, sy, cp, sp, cr, sr;

	/** Position (blocks, already raised or lowered by the body's lift) and yaw, pitch, roll in degrees. */
	public BodyFrame set(double x, double y, double z, double yaw, double pitch, double roll) {
		this.x = x;
		this.y = y;
		this.z = z;
		double a = Math.toRadians(-yaw), b = Math.toRadians(pitch), c = Math.toRadians(-roll);
		cy = Math.cos(a);
		sy = Math.sin(a);
		cp = Math.cos(b);
		sp = Math.sin(b);
		cr = Math.cos(c);
		sr = Math.sin(c);
		return this;
	}

	/** Model point (pixels) to the world (blocks). {@code out} may be {@code p}. */
	public double[] toWorld(double[] p, double[] out) {
		double qx = p[0] / 16.0, qy = p[1] / 16.0 - PartSolver.CENTER_Y, qz = p[2] / 16.0 - PartSolver.CENTER_Z;
		double x1 = qx * cr - qy * sr, y1 = qx * sr + qy * cr;
		double y2 = y1 * cp - qz * sp + PartSolver.CENTER_Y, z2 = y1 * sp + qz * cp + PartSolver.CENTER_Z;
		out[0] = x + x1 * cy + z2 * sy;
		out[1] = y + y2;
		out[2] = z - x1 * sy + z2 * cy;
		return out;
	}

	/** World point (blocks) to the model (pixels). {@code out} may be {@code w}. */
	public double[] toModel(double[] w, double[] out) {
		double wx = w[0] - x, wy = w[1] - y, wz = w[2] - z;
		double x1 = wx * cy - wz * sy, z2 = wx * sy + wz * cy - PartSolver.CENTER_Z, y2 = wy - PartSolver.CENTER_Y;
		double y1 = y2 * cp + z2 * sp, qz = -y2 * sp + z2 * cp;
		double qx = x1 * cr + y1 * sr, qy = -x1 * sr + y1 * cr;
		out[0] = qx * 16.0;
		out[1] = (qy + PartSolver.CENTER_Y) * 16.0;
		out[2] = (qz + PartSolver.CENTER_Z) * 16.0;
		return out;
	}
}
