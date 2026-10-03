package crazylimits.dragonsworn.mc.client;

/**
 * A living entity's render state that knows whether its entity is prey the dragon carries, and how it
 * lies in the grip: the yaw it is drawn lying along (degrees), NaN when it is not held. Filled when the
 * state is extracted ({@code LivingEntityRendererMixin}), the one time the renderer sees the entity.
 */
public interface LyingPrey {
	double dragonsworn$lyingYaw();

	void dragonsworn$setLyingYaw(double yaw);
}
