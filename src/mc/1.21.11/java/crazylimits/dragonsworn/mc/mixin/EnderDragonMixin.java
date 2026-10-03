package crazylimits.dragonsworn.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import crazylimits.dragonsworn.body.PoseTrack;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonData;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.PreyHold;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
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
 *   <li>the flight plan and the current action are synced to clients;</li>
 *   <li>what it holds is never hurt by its contact damage ({@link PreyHold});</li>
 *   <li>no wing buffet: vanilla's shove (and its hit) on anything near the wings is gone;</li>
 *   <li>brought down anywhere, even on its feet, it takes its last flight (vanilla's dying phase) before it dies.</li>
 * </ul>
 */
@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin extends Mob implements DragonswornDragon {
	@Shadow
	@Final
	@Mutable
	private EnderDragonPart[] subEntities;

	@Shadow
	@Final
	private EnderDragonPart body;

	@Unique
	private DragonBrain dragonsworn$brain;
	@Unique
	private float dragonsworn$healthBefore;

	protected EnderDragonMixin(EntityType<? extends Mob> type, Level level) {
		super(type, level);
	}

	@Override
	public DragonBrain dragonsworn$brain() {
		if (dragonsworn$brain == null) dragonsworn$brain = new DragonBrain((EnderDragon) (Object) this);
		return dragonsworn$brain;
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void dragonsworn$moreParts(EntityType<? extends EnderDragon> type, Level level, CallbackInfo ci) {
		EnderDragon self = (EnderDragon) (Object) this;
		EnderDragonPart[] parts = Arrays.copyOf(subEntities, PoseTrack.PARTS);
		for (int i = subEntities.length; i < parts.length; i++) {
			parts[i] = new EnderDragonPart(self, PoseTrack.partName(i), PoseTrack.partWidth(i), PoseTrack.partHeight(i));
		}
		for (int i = 0; i < subEntities.length; i++) {
			((EnderDragonPartAccessor) parts[i]).dragonsworn$setSize(EntityDimensions.scalable(PoseTrack.partWidth(i), PoseTrack.partHeight(i)));
			parts[i].refreshDimensions();
		}
		subEntities = parts;
		// Parts are addressed by id = dragon id + index + 1 (what clients assume): reserve a fresh run long
		// enough for all of them.
		int base = EntityAccessor.dragonsworn$counter().getAndAdd(parts.length + 1) + 1;
		setId(base);
		for (int i = 0; i < parts.length; i++) parts[i].setId(base + i + 1);
	}

	@Inject(method = "recreateFromPacket", at = @At("RETURN"))
	private void dragonsworn$partIds(ClientboundAddEntityPacket packet, CallbackInfo ci) {
		for (int i = 0; i < subEntities.length; i++) subEntities[i].setId(getId() + i + 1);
	}

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void dragonsworn$defineData(SynchedEntityData.Builder builder, CallbackInfo ci) {
		builder.define(DragonData.FLIGHT, 0);
		builder.define(DragonData.ACTION, 0);
		builder.define(DragonData.LOOK, -1);
		builder.define(DragonData.VOICE, 0);
		builder.define(DragonData.STRIKE, DragonData.NO_STRIKE);
		builder.define(DragonData.GRIP, 0);
		builder.define(DragonData.FIREBALL, 0);
		builder.define(DragonData.FOOTHOLD, 0);
	}

	/** Vanilla flies straight at the target through anything; the brain flies there its own way. */
	@WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonPhaseInstance;getFlyTargetLocation()Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 dragonsworn$fly(DragonPhaseInstance phase, Operation<Vec3> original) {
		Vec3 target = original.call(phase);
		if (target == null) return null;
		dragonsworn$brain().flight.fly(phase, target);
		return null;
	}

	/** All parts are placed on the model at once, at vanilla's first part update. */
	@WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;tickPart(Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;DDD)V"))
	private void dragonsworn$placeParts(EnderDragon self, EnderDragonPart part, double x, double y, double z, Operation<Void> original) {
		if (part == body) dragonsworn$brain().placeParts();
	}

	/** Contact damage from the head and neck: kept in flight, replaced by real bites on the ground; never on what it holds. */
	@WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;hurt(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;)V"))
	private void dragonsworn$contactDamage(EnderDragon self, ServerLevel level, List<Entity> entities, Operation<Void> original) {
		if (!dragonsworn$brain().onGround()) original.call(self, level, dragonsworn$notHeld(entities));
	}

	/** No wing buffet: whoever stands close to the dragon is not thrown back (nor hit) by its wings. */
	@WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;knockBack(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;)V"))
	private void dragonsworn$noWingBuffet(EnderDragon self, ServerLevel level, List<Entity> entities, Operation<Void> original) {
	}

	/**
	 * Vanilla lets a dragon killed while sitting (on the portal) die on the spot; this one always takes its
	 * last flight first ({@code DragonDeathPhaseMixin}), wherever it stands.
	 */
	@WrapOperation(method = "hurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonPhaseInstance;isSitting()Z", ordinal = 0))
	private boolean dragonsworn$diesInFlight(DragonPhaseInstance phase, Operation<Boolean> original) {
		return false;
	}

	@Unique
	private List<Entity> dragonsworn$notHeld(List<Entity> entities) {
		PreyHold prey = dragonsworn$brain().prey;
		if (!prey.holding()) return entities;
		return entities.stream().filter(e -> !prey.holds(e)).toList();
	}

	/** Every return: a dead dragon's aiStep leaves early (its body, clock and cocoon still need their tick). */
	@Inject(method = "aiStep", at = @At("RETURN"))
	private void dragonsworn$tick(CallbackInfo ci) {
		dragonsworn$brain().tickEnd();
	}

	/** Breaks only soft blocks; anything else just counts as being in a wall. */
	@Inject(method = "checkWalls", at = @At("HEAD"), cancellable = true)
	private void dragonsworn$checkWalls(ServerLevel level, AABB box, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(dragonsworn$brain().hull.inWall(box));
	}

	@Inject(method = "hurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"))
	private void dragonsworn$hurtBy(ServerLevel level, EnderDragonPart part, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		dragonsworn$healthBefore = getHealth();
		dragonsworn$brain().combat.hurtBy(source, Arrays.asList(subEntities).indexOf(part));
	}

	/** A hit that took health off (not one the dragon shrugged off, or met while still flashing red). */
	@Inject(method = "hurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("RETURN"))
	private void dragonsworn$hit(ServerLevel level, EnderDragonPart part, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (getHealth() < dragonsworn$healthBefore) dragonsworn$brain().combat.hit(source, dragonsworn$healthBefore - getHealth());
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void dragonsworn$save(ValueOutput output, CallbackInfo ci) {
		dragonsworn$brain().save(output);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void dragonsworn$load(ValueInput input, CallbackInfo ci) {
		dragonsworn$brain().load(input);
	}
}
