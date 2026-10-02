package crazylimits.dragonfall.mc.breath.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import crazylimits.dragonfall.mc.DragonfallDragon;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonStrafePlayerPhase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The arena's strafe heats up before its fireball like every other: the shot vanilla fires at once
 * is handed to the brain ({@code DragonBrain.chargeFireball}), which fires it at the strafe's target
 * once the fast heat glow reaches the jaw. The strafe still ends where vanilla's does.
 */
@Mixin(DragonStrafePlayerPhase.class)
public abstract class DragonStrafePlayerPhaseMixin extends AbstractDragonPhaseInstance {
	@Shadow @Nullable private LivingEntity attackTarget;

	private DragonStrafePlayerPhaseMixin(EnderDragon dragon) {
		super(dragon);
	}

	/** The shot's sound plays when the brain fires it. */
	@WrapWithCondition(method = "doServerTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;levelEvent(Lnet/minecraft/world/entity/player/Player;ILnet/minecraft/core/BlockPos;I)V"))
	private boolean dragonfall$noShotSound(Level level, @Nullable Player player, int event, BlockPos pos, int data) {
		return false;
	}

	@WrapOperation(method = "doServerTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
	private boolean dragonfall$chargeFireball(Level level, Entity fireball, Operation<Boolean> original) {
		DragonfallDragon.brain(dragon).chargeFireball(attackTarget);
		return true;
	}
}
