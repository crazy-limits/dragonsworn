package crazylimits.dragonfall.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import crazylimits.dragonfall.body.PoseTrack;
import crazylimits.dragonfall.mc.DragonBrain;
import crazylimits.dragonfall.mc.DragonData;
import crazylimits.dragonfall.mc.DragonfallDragon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.List;

/**
 * Plugs {@link DragonBrain} into the vanilla dragon:
 * <ul>
 *   <li>more hitbox parts, sized to the model, and placed on its bones every tick (both sides);</li>
 *   <li>flight steered by the brain (wingbeat thrust, banking, flying around terrain) instead of
 *       vanilla's straight-through flight;</li>
 *   <li>only soft blocks (leaves, plants) are broken, instead of everything not dragon-immune;</li>
 *   <li>the flight plan and the current action are synced to clients.</li>
 * </ul>
 */
@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin extends Mob implements DragonfallDragon {
	@Shadow
	@Final
	@Mutable
	private EnderDragonPart[] subEntities;

	@Shadow
	@Final
	private EnderDragonPart body;

	@Unique
	private DragonBrain dragonfall$brain;
	@Unique
	private float dragonfall$healthBefore;

	protected EnderDragonMixin(EntityType<? extends Mob> type, Level level) {
		super(type, level);
	}

	@Override
	public DragonBrain dragonfall$brain() {
		if (dragonfall$brain == null) dragonfall$brain = new DragonBrain((EnderDragon) (Object) this);
		return dragonfall$brain;
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void dragonfall$moreParts(EntityType<? extends EnderDragon> type, Level level, CallbackInfo ci) {
		EnderDragon self = (EnderDragon) (Object) this;
		EnderDragonPart[] parts = Arrays.copyOf(subEntities, PoseTrack.PARTS);
		for (int i = subEntities.length; i < parts.length; i++) {
			parts[i] = new EnderDragonPart(self, PoseTrack.partName(i), PoseTrack.partWidth(i), PoseTrack.partHeight(i));
		}
		for (int i = 0; i < subEntities.length; i++) {
			((EnderDragonPartAccessor) parts[i]).dragonfall$setSize(EntityDimensions.scalable(PoseTrack.partWidth(i), PoseTrack.partHeight(i)));
			parts[i].refreshDimensions();
		}
		subEntities = parts;
		// Parts are addressed by id = dragon id + index + 1 (what clients assume): reserve a fresh run long
		// enough for all of them.
		int base = EntityAccessor.dragonfall$counter().getAndAdd(parts.length + 1) + 1;
		setId(base);
		for (int i = 0; i < parts.length; i++) parts[i].setId(base + i + 1);
	}

	@Inject(method = "recreateFromPacket", at = @At("RETURN"))
	private void dragonfall$partIds(ClientboundAddEntityPacket packet, CallbackInfo ci) {
		for (int i = 0; i < subEntities.length; i++) subEntities[i].setId(getId() + i + 1);
	}

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void dragonfall$defineData(SynchedEntityData.Builder builder, CallbackInfo ci) {
		builder.define(DragonData.FLIGHT, 0);
		builder.define(DragonData.ACTION, 0);
		builder.define(DragonData.LOOK, -1);
		builder.define(DragonData.VOICE, 0);
		builder.define(DragonData.STRIKE, DragonData.NO_STRIKE);
	}

	/** Vanilla flies straight at the target through anything; the brain flies there its own way. */
	@WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonPhaseInstance;getFlyTargetLocation()Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 dragonfall$fly(DragonPhaseInstance phase, Operation<Vec3> original) {
		Vec3 target = original.call(phase);
		if (target == null) return null;
		dragonfall$brain().fly(phase, target);
		return null;
	}

	/** All parts are placed on the model at once, at vanilla's first part update. */
	@WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;tickPart(Lnet/minecraft/world/entity/boss/EnderDragonPart;DDD)V"))
	private void dragonfall$placeParts(EnderDragon self, EnderDragonPart part, double x, double y, double z, Operation<Void> original) {
		if (part == body) dragonfall$brain().placeParts();
	}

	/** Contact damage from the head and neck: kept in flight, replaced by real bites on the ground. */
	@WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;hurt(Ljava/util/List;)V"))
	private void dragonfall$contactDamage(EnderDragon self, List<Entity> entities, Operation<Void> original) {
		if (!dragonfall$brain().onGround()) original.call(self, entities);
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void dragonfall$tick(CallbackInfo ci) {
		dragonfall$brain().tickEnd();
	}

	/** Breaks only soft blocks; anything else just counts as being in a wall. */
	@Inject(method = "checkWalls", at = @At("HEAD"), cancellable = true)
	private void dragonfall$checkWalls(AABB box, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(dragonfall$brain().inWall(box));
	}

	@Inject(method = "hurt(Lnet/minecraft/world/entity/boss/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"))
	private void dragonfall$hurtBy(EnderDragonPart part, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		dragonfall$healthBefore = getHealth();
		dragonfall$brain().hurtBy(source);
	}

	/** A hit that took health off (not one the dragon shrugged off, or met while still flashing red). */
	@Inject(method = "hurt(Lnet/minecraft/world/entity/boss/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("RETURN"))
	private void dragonfall$hit(EnderDragonPart part, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (getHealth() < dragonfall$healthBefore) dragonfall$brain().hit(source);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void dragonfall$save(CompoundTag tag, CallbackInfo ci) {
		dragonfall$brain().save(tag);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void dragonfall$load(CompoundTag tag, CallbackInfo ci) {
		dragonfall$brain().load(tag);
	}
}
