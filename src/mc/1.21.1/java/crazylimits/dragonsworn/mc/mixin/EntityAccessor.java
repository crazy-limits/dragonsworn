package crazylimits.dragonsworn.mc.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.atomic.AtomicInteger;

/** The entity id counter: the dragon reserves a run of ids for its (now more numerous) parts. */
@Mixin(Entity.class)
public interface EntityAccessor {
	@Accessor("ENTITY_COUNTER")
	static AtomicInteger dragonsworn$counter() {
		throw new AssertionError();
	}
}
