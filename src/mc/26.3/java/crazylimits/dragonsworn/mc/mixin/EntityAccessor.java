package crazylimits.dragonsworn.mc.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.atomic.AtomicInteger;

/** The server's entity id counter (26.x keeps it in the level): the dragon reserves a run of ids for its (now more numerous) parts. */
@Mixin(ServerLevel.class)
public interface EntityAccessor {
	@Accessor("ENTITY_COUNTER")
	static AtomicInteger dragonsworn$counter() {
		throw new AssertionError();
	}
}
