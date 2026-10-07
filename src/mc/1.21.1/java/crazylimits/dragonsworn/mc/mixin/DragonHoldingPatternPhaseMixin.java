package crazylimits.dragonsworn.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonHoldingPatternPhase;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The End's dragon guards its crystals: when vanilla's circuit picks a player to strafe (the one nearest
 * the altar), it picks the one threatening its crystals instead ({@code ArenaDirector#guardTarget}), if any.
 */
@Mixin(DragonHoldingPatternPhase.class)
public abstract class DragonHoldingPatternPhaseMixin extends AbstractDragonPhaseInstance {
	protected DragonHoldingPatternPhaseMixin(EnderDragon dragon) {
		super(dragon);
	}

	@WrapOperation(method = "findNewTarget", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;getNearestPlayer(Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;Lnet/minecraft/world/entity/LivingEntity;DDD)Lnet/minecraft/world/entity/player/Player;"))
	private Player dragonsworn$guardCrystals(Level level, TargetingConditions conditions, LivingEntity source, double x, double y, double z,
			Operation<Player> original) {
		Player guarded = DragonswornDragon.brain(dragon).guardTarget();
		return guarded != null ? guarded : original.call(level, conditions, source, x, y, z);
	}
}
