package crazylimits.dragonsworn.mc.arena.mixin;

import com.mojang.serialization.Codec;
import crazylimits.dragonsworn.mc.arena.WardedCrystal;
import crazylimits.dragonsworn.mc.breath.DragonFire;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An End crystal standing on bedrock (the spires' crystals, the ones set on the exit portal to respawn the
 * dragon) keeps dragon fire burning under it instead of common fire; anywhere else the fire is vanilla's.
 *
 * <p>And its rune ward ({@code CrystalWard}): a flag set where the spires are built ({@code Monoliths}) or the fight
 * finds the crystal ({@code Wards.wardFound}), synced (the client draws the rings, its projectiles bounce too) and
 * saved once set, warded or not (as {@code DragonswornWarded}, which {@code /summon} takes too). A warded crystal takes
 * no projectile damage (one shot from inside the ward, or pushed in, does not break it either).
 */
@Mixin(EndCrystal.class)
public abstract class EndCrystalMixin extends Entity implements WardedCrystal {
	@Unique
	private static final String WARDED = "DragonswornWarded";
	@Unique
	private static final EntityDataAccessor<Boolean> DRAGONSWORN$WARDED = SynchedEntityData.defineId(EndCrystal.class, EntityDataSerializers.BOOLEAN);
	@Unique
	private boolean dragonsworn$wardSet;

	private EndCrystalMixin(EntityType<?> type, Level level) {
		super(type, level);
	}

	@Redirect(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/BaseFireBlock;getState(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
	private BlockState dragonsworn$crystalFire(BlockGetter level, BlockPos pos) {
		return DragonFire.crystalFire(level, pos);
	}

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void dragonsworn$defineWard(SynchedEntityData.Builder builder, CallbackInfo ci) {
		builder.define(DRAGONSWORN$WARDED, false);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void dragonsworn$saveWard(ValueOutput output, CallbackInfo ci) {
		if (dragonsworn$wardSet) output.putBoolean(WARDED, dragonsworn$warded());
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void dragonsworn$readWard(ValueInput input, CallbackInfo ci) {
		input.read(WARDED, Codec.BOOL).ifPresent(this::dragonsworn$setWarded);
	}

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void dragonsworn$wardOff(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (dragonsworn$warded() && source.is(DamageTypeTags.IS_PROJECTILE)) cir.setReturnValue(false);
	}

	@Override
	public boolean dragonsworn$warded() {
		return getEntityData().get(DRAGONSWORN$WARDED);
	}

	@Override
	public void dragonsworn$setWarded(boolean warded) {
		getEntityData().set(DRAGONSWORN$WARDED, warded);
		dragonsworn$wardSet = true;
	}

	@Override
	public boolean dragonsworn$wardSet() {
		return dragonsworn$wardSet;
	}
}
