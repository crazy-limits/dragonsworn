package crazylimits.dragonsworn.mc;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Small questions every phase asks about who it fights and which way it faces. */
public final class Targets {
	private Targets() {
	}

	/** A player in creative or spectator mode: never a target, never hit. */
	public static boolean untouchable(Entity entity) {
		return entity instanceof Player p && (p.isCreative() || p.isSpectator());
	}

	/** The horizontal unit vector the dragon faces at {@code yRot} (degrees; 0 faces north, -z, and 90 east, +x). */
	public static Vec3 facing(float yRot) {
		float yaw = yRot * Mth.DEG_TO_RAD;
		return new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw));
	}
}
