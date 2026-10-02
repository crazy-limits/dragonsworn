package crazylimits.dragonsworn.mc.client;

import crazylimits.dragonsworn.limb.Affine;
import crazylimits.dragonsworn.limb.Joint;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib's bones and cubes, read and set in editor space (pixels, degrees) as {@code tools/rig.py} and
 * the core's {@link Joint}/{@link Affine} use it. The one place that knows GeckoLib's bone API: a GeckoLib
 * version with another shape needs only this changed.
 */
final class GeoBones {
	private GeoBones() {
	}

	/** The model's bone {@code name}, or null. */
	static GeoBone bone(GeoModel<?> model, String name) {
		return model.getAnimationProcessor().getBone(name);
	}

	/** The bone as a {@link Joint}: its pivot, position and rotation now, in editor space (pixels, degrees). */
	static Joint joint(GeoBone bone) {
		return new Joint().set(new double[]{bone.getPivotX(), bone.getPivotY(), bone.getPivotZ()},
				new double[]{-bone.getPosX(), bone.getPosY(), bone.getPosZ()},
				new double[]{Math.toDegrees(bone.getRotX()), Math.toDegrees(bone.getRotY()), Math.toDegrees(bone.getRotZ())});
	}

	/** The bone's matrix in the model (pixels), through all its parents. */
	static double[] matrix(GeoBone bone) {
		double[] local = joint(bone).local();
		return bone.getParent() == null ? local : Affine.mul(matrix(bone.getParent()), local);
	}

	/**
	 * A cube's own turn inside its bone (pixels): GeckoLib keeps it out of the vertices and turns the
	 * cube about its pivot when drawing, Z then Y then X like a bone.
	 */
	static double[] cubeMatrix(GeoCube cube) {
		Joint j = new Joint();
		// GeckoLib keeps the cube's pivot in blocks (mirrored like the vertices) and its turn in radians
		j.pivot[0] = cube.pivot().x * 16.0;
		j.pivot[1] = cube.pivot().y * 16.0;
		j.pivot[2] = cube.pivot().z * 16.0;
		j.rot[0] = Math.toDegrees(cube.rotation().x);
		j.rot[1] = Math.toDegrees(cube.rotation().y);
		j.rot[2] = Math.toDegrees(cube.rotation().z);
		return j.local();
	}

	/** Sets {@code bone}'s rotation to {@code joint}'s (degrees). */
	static void setRotation(GeoBone bone, Joint joint) {
		bone.setRotX((float) Math.toRadians(joint.rot[0]));
		bone.setRotY((float) Math.toRadians(joint.rot[1]));
		bone.setRotZ((float) Math.toRadians(joint.rot[2]));
	}

	/** Adds {@code pitch} (X) and {@code yaw} (Y), degrees, to {@code bone}'s rotation this frame. */
	static void add(GeoBone bone, double pitch, double yaw) {
		if (bone == null) return;
		bone.setRotX(bone.getRotX() + (float) Math.toRadians(pitch));
		bone.setRotY(bone.getRotY() + (float) Math.toRadians(yaw));
	}
}
