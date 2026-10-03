package crazylimits.dragonsworn.mc.breath.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import crazylimits.dragonsworn.anim.AnimClock;
import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.anim.DragonAnimSelector;
import crazylimits.dragonsworn.anim.DragonVoice;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonData;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.breath.client.ClientGame;
import crazylimits.dragonsworn.mc.client.DragonAudio;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The dragon's voice on the client, timed by {@link DragonVoice} on the animation clock: a swing on each
 * downstroke, a step as each foot plants, the roar as the jaw opens (the roar animation's, or in flight
 * the jaw opened with it when the server says so, {@link DragonData#VOICE}); an attack fades a roar out.
 * Heard whether or not the dragon is on screen; never while the game is paused.
 *
 * <p>Vanilla's own dragon sounds are gone: the flap on its own timer (not the model's wingbeat), the
 * growl every 10-20 s and the ambient growl.
 */
@Mixin(EnderDragon.class)
public abstract class DragonVoiceMixin extends Mob {
	@Unique private DragonAnimSelector.Choice dragonsworn$voiceChoice;
	@Unique private DragonAnim dragonsworn$voiceAnim;
	@Unique private double dragonsworn$voiceSeconds;
	/** The server's roar count last seen; null until the first tick (a dragon coming into view is quiet). */
	@Unique private Integer dragonsworn$roars;

	private DragonVoiceMixin(EntityType<? extends Mob> type, Level level) {
		super(type, level);
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void dragonsworn$voice(CallbackInfo ci) {
		// never voice a dragon while the game is paused (ClientGame is client-only: checked after the side)
		if (!level().isClientSide() || ClientGame.paused() || isSilent()) return;
		EnderDragon self = (EnderDragon) (Object) this;
		DragonBrain brain = DragonswornDragon.brain(this);
		AnimClock clock = brain.clock;
		DragonAnim anim = clock.anim();
		double seconds = clock.seconds();
		boolean same = anim == dragonsworn$voiceAnim && clock.choice() != null && clock.choice().equals(dragonsworn$voiceChoice);
		DragonVoice.Cue cue = same ? DragonVoice.due(anim, dragonsworn$voiceSeconds, seconds) : null;
		dragonsworn$voiceChoice = clock.choice();
		dragonsworn$voiceAnim = anim;
		dragonsworn$voiceSeconds = seconds;
		if (cue != null) DragonAudio.play(self, cue);

		int roars = getEntityData().get(DragonData.VOICE);
		if (dragonsworn$roars != null && roars != dragonsworn$roars) DragonAudio.roar(self, true);
		dragonsworn$roars = roars;

		if (DragonVoice.attacking(anim) || brain.attacking()) brain.roar.fade(tickCount);
	}

	/** Vanilla's flap, on its own flap timer: the swings follow the model's wingbeat instead. */
	@Inject(method = "onFlap", at = @At("HEAD"), cancellable = true)
	private void dragonsworn$noFlap(CallbackInfo ci) {
		ci.cancel();
	}

	/** Vanilla's growl every 10-20 s (the only sound aiStep plays): the dragon roars with its jaw open instead. */
	@WrapWithCondition(method = "aiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
	private boolean dragonsworn$noGrowl(Level level, double x, double y, double z, SoundEvent sound, SoundSource source, float volume,
			float pitch, boolean delay) {
		return false;
	}

	@Inject(method = "getAmbientSound", at = @At("HEAD"), cancellable = true)
	private void dragonsworn$noAmbient(CallbackInfoReturnable<SoundEvent> cir) {
		cir.setReturnValue(null);
	}
}
