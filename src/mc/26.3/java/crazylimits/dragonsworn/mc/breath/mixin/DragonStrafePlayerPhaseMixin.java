package crazylimits.dragonsworn.mc.breath.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.phase.AttackTargeting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonStrafePlayerPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The arena's strafe heats up before its fireball like every other: the shot vanilla fires at once
 * is handed to the brain ({@code DragonBrain.chargeFireball}), which fires it at the strafe's target
 * once the fast heat glow reaches the jaw. The strafe flies on at its target while the fireball heats up
 * (vanilla's turns away to the holding pattern at once, and the head lost its aim) and ends once it is out.
 */
@Mixin(DragonStrafePlayerPhase.class)
public abstract class DragonStrafePlayerPhaseMixin extends AbstractDragonPhaseInstance implements AttackTargeting {
	@Shadow @Nullable private LivingEntity attackTarget;
	/** This strafe has handed its shot to the brain. */
	@Unique
	private boolean dragonsworn$charged;

	private DragonStrafePlayerPhaseMixin(EnderDragon dragon) {
		super(dragon);
	}

	@Nullable
	@Override
	public LivingEntity dragonsworn$attackTarget() {
		return attackTarget;
	}

	@Inject(method = "begin", at = @At("HEAD"))
	private void dragonsworn$begin(CallbackInfo ci) {
		dragonsworn$charged = false;
	}

	/** The shot is out (or dropped): the strafe ends as vanilla's does after its shot. */
	@Inject(method = "doServerTick", at = @At("HEAD"), cancellable = true)
	private void dragonsworn$afterShot(CallbackInfo ci) {
		if (!dragonsworn$charged || DragonswornDragon.brain(dragon).fireballs.charging()) return;
		dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
		ci.cancel();
	}

	/** No turning away while the fireball heats up: the strafe flies on at its target. */
	@WrapWithCondition(method = "doServerTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/boss/enderdragon/phases/EnderDragonPhaseManager;setPhase(Lnet/minecraft/world/entity/boss/enderdragon/phases/EnderDragonPhase;)V"))
	private boolean dragonsworn$holdCourse(EnderDragonPhaseManager manager, EnderDragonPhase<?> phase) {
		return !DragonswornDragon.brain(dragon).fireballs.charging();
	}

	/** The shot's sound plays when the brain fires it. */
	@WrapWithCondition(method = "doServerTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;levelEvent(Lnet/minecraft/world/entity/Entity;ILnet/minecraft/core/BlockPos;I)V"))
	private boolean dragonsworn$noShotSound(ServerLevel level, @Nullable Entity source, int event, BlockPos pos, int data) {
		return false;
	}

	@WrapOperation(method = "doServerTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
	private boolean dragonsworn$chargeFireball(ServerLevel level, Entity fireball, Operation<Boolean> original) {
		DragonswornDragon.brain(dragon).fireballs.charge(attackTarget);
		dragonsworn$charged = true;
		return true;
	}
}
