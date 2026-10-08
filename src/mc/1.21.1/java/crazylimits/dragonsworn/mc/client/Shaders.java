package crazylimits.dragonsworn.mc.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Shader packs (Iris, on Fabric and NeoForge), through Iris's public API, looked up at run time: Iris is never
 * needed. A shader pack draws the world a second time from the sun for its shadows; what is see-through and
 * glows (the crystal wards) must cast none.
 */
public final class Shaders {
	/** {@code IrisApi.getInstance()} bound, then {@code isRenderingShadowPass()}; null without Iris. */
	private static final MethodHandle SHADOW_PASS = shadowPassHandle();

	private Shaders() {}

	/** Whether this draw is a shader pack's shadow pass. */
	public static boolean shadowPass() {
		if (SHADOW_PASS == null) return false;
		try {
			return (boolean) SHADOW_PASS.invoke();
		} catch (Throwable e) {
			return false;
		}
	}

	private static MethodHandle shadowPassHandle() {
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			Object iris = lookup.findStatic(api, "getInstance", MethodType.methodType(api)).invoke();
			return lookup.findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class)).bindTo(iris);
		} catch (Throwable e) {
			return null;
		}
	}
}
