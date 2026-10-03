package crazylimits.dragonsworn.mc.client;

import crazylimits.dragonsworn.limb.Affine;
import crazylimits.dragonsworn.limb.Joint;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.model.BakedGeoModel;
import software.bernie.geckolib.cache.model.GeoBone;
import software.bernie.geckolib.cache.model.GeoQuad;
import software.bernie.geckolib.cache.model.GeoVertex;
import software.bernie.geckolib.cache.model.cuboid.CuboidGeoBone;
import software.bernie.geckolib.cache.model.cuboid.GeoCube;
import software.bernie.geckolib.renderer.base.BoneSnapshots;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * GeckoLib's bones and cubes, read and set in editor space (pixels, degrees) as {@code tools/rig.py} and
 * the core's {@link Joint}/{@link Affine} use it. The one place that knows GeckoLib's bone API: a GeckoLib
 * version with another shape needs only this changed.
 *
 * <p>GeckoLib 5 keeps its bones immutable and poses them for one render pass through {@link BoneSnapshot}s
 * (added to the bone's own rest turn), handed to the renderer's bone updaters. {@link Model} is that
 * pass's model, its {@link Bone}s read and turn the snapshots as GeckoLib 4's bones were read and turned:
 * whole turns, the rest turn included.
 */
public final class GeoBones {
	private GeoBones() {
	}

	/** The model posed for one render pass: GeckoLib's baked bones and this pass's snapshots of them. */
	public static final class Model {
		private final BakedGeoModel baked;
		private final BoneSnapshots snapshots;
		private final Map<String, Bone> bones = new HashMap<>();

		Model(BakedGeoModel baked, BoneSnapshots snapshots) {
			this.baked = baked;
			this.snapshots = snapshots;
		}

		/** The bone {@code name}, or null. */
		public Bone bone(String name) {
			Bone bone = bones.get(name);
			if (bone != null) return bone;
			Optional<GeoBone> geo = baked.getBone(name);
			if (geo.isEmpty()) return null;
			bone = new Bone(this, geo.get(), snapshots.get(geo.get()));
			bones.put(name, bone);
			return bone;
		}

		Optional<Bone> getBone(String name) {
			return Optional.ofNullable(bone(name));
		}
	}

	/** One bone as posed this pass; its turns are whole (GeckoLib's rest turn and the snapshot's together). */
	public static final class Bone {
		private final Model model;
		private final GeoBone bone;
		private final BoneSnapshot snapshot;
		private List<Cube> cubes;

		private Bone(Model model, GeoBone bone, BoneSnapshot snapshot) {
			this.model = model;
			this.bone = bone;
			this.snapshot = snapshot;
		}

		/** Its rest turn (radians), as GeckoLib 4's initial snapshot gave it. */
		record Rest(float getRotX, float getRotY, float getRotZ) {
		}

		String name() {
			return bone.name();
		}

		Bone getParent() {
			return bone.parent() == null ? null : model.bone(bone.parent().name());
		}

		List<Bone> getChildBones() {
			List<Bone> children = new ArrayList<>(bone.children().length);
			for (GeoBone child : bone.children()) children.add(model.bone(child.name()));
			return children;
		}

		List<Cube> getCubes() {
			if (cubes == null) {
				cubes = new ArrayList<>();
				if (bone instanceof CuboidGeoBone cuboid) {
					for (GeoCube cube : cuboid.cubes) cubes.add(Cube.of(cube));
				}
			}
			return cubes;
		}

		boolean isHidden() {
			return snapshot.isHidden();
		}

		float getPivotX() {
			return bone.pivotX();
		}

		float getPivotY() {
			return bone.pivotY();
		}

		float getPivotZ() {
			return bone.pivotZ();
		}

		float getPosX() {
			return snapshot.getTranslateX();
		}

		float getPosY() {
			return snapshot.getTranslateY();
		}

		float getPosZ() {
			return snapshot.getTranslateZ();
		}

		float getRotX() {
			return bone.baseRotX() + snapshot.getRotX();
		}

		float getRotY() {
			return bone.baseRotY() + snapshot.getRotY();
		}

		float getRotZ() {
			return bone.baseRotZ() + snapshot.getRotZ();
		}

		void setRotX(float radians) {
			snapshot.setRotX(radians - bone.baseRotX());
		}

		void setRotY(float radians) {
			snapshot.setRotY(radians - bone.baseRotY());
		}

		void setRotZ(float radians) {
			snapshot.setRotZ(radians - bone.baseRotZ());
		}

		Rest getInitialSnapshot() {
			return new Rest(bone.baseRotX(), bone.baseRotY(), bone.baseRotZ());
		}
	}

	/**
	 * A cube: its pivot and turn inside its bone (GeckoLib's: blocks, mirrored like the vertices, and
	 * radians) and its corners (blocks, every face's, so a corner shared by three faces comes three times).
	 */
	record Cube(Vec3 pivot, Vec3 rotation, Vector3f[] vertices) {
		static Cube of(GeoCube cube) {
			List<Vector3f> vertices = new ArrayList<>();
			if (cube.quads() != null) {
				for (GeoQuad quad : cube.quads()) {
					if (quad == null) continue;
					for (GeoVertex v : quad.vertices()) vertices.add(new Vector3f(v.posX(), v.posY(), v.posZ()));
				}
			}
			return new Cube(cube.pivot(), cube.rotation(), vertices.toArray(new Vector3f[0]));
		}
	}

	/** The model's bone {@code name}, or null. */
	static Bone bone(Model model, String name) {
		return model.bone(name);
	}

	/** The bone as a {@link Joint}: its pivot, position and rotation now, in editor space (pixels, degrees). */
	static Joint joint(Bone bone) {
		return new Joint().set(new double[]{bone.getPivotX(), bone.getPivotY(), bone.getPivotZ()},
				new double[]{-bone.getPosX(), bone.getPosY(), bone.getPosZ()},
				new double[]{Math.toDegrees(bone.getRotX()), Math.toDegrees(bone.getRotY()), Math.toDegrees(bone.getRotZ())});
	}

	/** The bone's matrix in the model (pixels), through all its parents. */
	public static double[] matrix(Bone bone) {
		double[] local = joint(bone).local();
		return bone.getParent() == null ? local : Affine.mul(matrix(bone.getParent()), local);
	}

	/**
	 * A cube's own turn inside its bone (pixels): GeckoLib keeps it out of the vertices and turns the
	 * cube about its pivot when drawing, Z then Y then X like a bone.
	 */
	static double[] cubeMatrix(Cube cube) {
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
	static void setRotation(Bone bone, Joint joint) {
		bone.setRotX((float) Math.toRadians(joint.rot[0]));
		bone.setRotY((float) Math.toRadians(joint.rot[1]));
		bone.setRotZ((float) Math.toRadians(joint.rot[2]));
	}

	/** Adds {@code pitch} (X) and {@code yaw} (Y), degrees, to {@code bone}'s rotation this frame. */
	static void add(Bone bone, double pitch, double yaw) {
		if (bone == null) return;
		bone.setRotX(bone.getRotX() + (float) Math.toRadians(pitch));
		bone.setRotY(bone.getRotY() + (float) Math.toRadians(yaw));
	}
}
