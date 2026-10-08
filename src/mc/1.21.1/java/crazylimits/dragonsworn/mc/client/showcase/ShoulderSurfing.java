package crazylimits.dragonsworn.mc.client.showcase;

import net.minecraft.world.phys.Vec3;

/**
 * Shoulder Surfing Reloaded's API, when the dev client has it ({@code -Pdragonsworn.shoulderSurfing}), reached by
 * reflection: Dragonsworn does not depend on it. Its over-the-shoulder view is vanilla's third person behind, its
 * camera moved off the eyes by its own offset ({@code getRenderOffset}: camera axes, x to the left, y up, z back).
 */
final class ShoulderSurfing {
	private static final String API = "com.github.exopandora.shouldersurfing.api.client.";

	private ShoulderSurfing() {
	}

	static boolean loaded() {
		try {
			Class.forName(API + "IShoulderSurfing");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	/** Its perspective by name: {@code FIRST_PERSON}, {@code THIRD_PERSON_BACK}, {@code SHOULDER_SURFING}. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	static void perspective(String name) {
		try {
			Class<?> perspective = Class.forName(API + "Perspective");
			api().getMethod("changePerspective", perspective).invoke(instance(), Enum.valueOf((Class) perspective, name));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Shoulder Surfing's perspective", e);
		}
	}

	/** Whether its over-the-shoulder view is on. */
	static boolean active() {
		try {
			return (Boolean) api().getMethod("isShoulderSurfing").invoke(instance());
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Shoulder Surfing's state", e);
		}
	}

	/** Its camera's offset from the eyes as last placed. */
	static Vec3 offset() {
		try {
			Object camera = api().getMethod("getCamera").invoke(instance());
			return (Vec3) Class.forName(API + "IShoulderSurfingCamera").getMethod("getRenderOffset").invoke(camera);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Shoulder Surfing's camera", e);
		}
	}

	private static Class<?> api() throws ClassNotFoundException {
		return Class.forName(API + "IShoulderSurfing");
	}

	private static Object instance() throws ReflectiveOperationException {
		return api().getMethod("getInstance").invoke(null);
	}
}
