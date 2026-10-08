package crazylimits.dragonsworn.mc.client;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Shader packs (Iris, on Fabric and NeoForge), through Iris's public API, looked up at run time: Iris is never
 * needed. A shader pack draws the world a second time from the sun for its shadows; what is see-through and
 * glows (the crystal wards) must cast none. Iris draws only the render pipelines it knows with the pack's
 * programs: our own pipelines are named to it ({@link #assign}).
 */
public final class Shaders {
	private static final Logger LOG = LogUtils.getLogger();
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

	/**
	 * Tells Iris to draw {@code pipeline} with the pack's program {@code program} (an {@code IrisProgram} name, such as
	 * {@code PARTICLES_TRANSLUCENT}); without Iris nothing happens.
	 */
	public static void assign(Object pipeline, String program) {
		assign(pipeline, "assignPipeline", "IrisProgram", program);
	}

	/**
	 * As {@link #assign(Object, String)}, and in the shadow pass with {@code shadowProgram} (an {@code IrisShadowProgram}
	 * name, such as {@code SHADOW_ENTITIES}) where Iris has one (26.x).
	 */
	public static void assign(Object pipeline, String program, String shadowProgram) {
		assign(pipeline, program);
		assign(pipeline, "assignPipelineShadow", "IrisShadowProgram", shadowProgram);
	}

	/** Calls {@code IrisApi.<call>(pipeline, <programs>.valueOf(program))}, if this Iris has it. */
	private static void assign(Object pipeline, String call, String programs, String program) {
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Class<?> names = Class.forName("net.irisshaders.iris.api.v0." + programs);
			Object iris = api.getMethod("getInstance").invoke(null);
			for (java.lang.reflect.Method method : api.getMethods()) {
				if (method.getName().equals(call) && method.getParameterCount() == 2
						&& method.getParameterTypes()[0].isInstance(pipeline) && method.getParameterTypes()[1] == names) {
					method.invoke(iris, pipeline, names.getMethod("valueOf", String.class).invoke(null, program));
					LOG.info("Shader packs draw {} as {}", pipeline, program);
					return;
				}
			}
		} catch (ClassNotFoundException e) {
			// no Iris, or one without this kind of program
		} catch (ReflectiveOperationException | LinkageError e) {
			LOG.warn("Iris did not take {} as {}", pipeline, program, e);
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
