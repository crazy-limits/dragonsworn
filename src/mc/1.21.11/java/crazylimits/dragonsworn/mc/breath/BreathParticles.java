package crazylimits.dragonsworn.mc.breath;

import crazylimits.dragonsworn.Dragonsworn;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Void flame: Ice and Fire's dragon fire recolored to Dragon's Breath (see {@code tools/particles.py}).
 * Both types share the sprites and differ in how they move ({@code VoidFlameParticle}). The loaders
 * register {@link #ALL}; the server needs them too, because a breath cloud syncs its particle by id.
 */
public final class BreathParticles {
	/** The stream out of the dragon's mouth: flies along its velocity, grows and cools to violet. */
	public static final SimpleParticleType VOID_BREATH = new SimpleParticleType(false) {};
	/** A short flame licking up out of a breath cloud (the fireball's and the perched breath's). */
	public static final SimpleParticleType VOID_FLAME = new SimpleParticleType(false) {};

	public static final Map<Identifier, SimpleParticleType> ALL = Map.of(
			id("void_breath"), VOID_BREATH,
			id("void_flame"), VOID_FLAME);

	private BreathParticles() {}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(Dragonsworn.MOD_ID, path);
	}
}
