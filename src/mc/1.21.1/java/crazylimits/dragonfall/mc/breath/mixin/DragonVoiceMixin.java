package crazylimits.dragonfall.mc.breath.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import crazylimits.dragonfall.anim.AnimClock;
import crazylimits.dragonfall.anim.DragonAnim;
import crazylimits.dragonfall.anim.DragonAnimSelector;
import crazylimits.dragonfall.anim.DragonVoice;
import crazylimits.dragonfall.mc.DragonBrain;
import crazylimits.dragonfall.mc.DragonData;
import crazylimits.dragonfall.mc.DragonfallDragon;
import crazylimits.dragonfall.mc.breath.client.ClientGame;
import crazylimits.dragonfall.mc.client.DragonAudio;
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
	@Unique private DragonAnimSelector.Choice dragonfall$voiceChoice;
	@Unique private DragonAnim dragonfall$voiceAnim;
	@Unique private double dragonfall$voiceSeconds;
	/** The server's roar count last seen; null until the first tick (a dragon coming into view is quiet). */
	@Unique private Integer dragonfall$roars;

	private DragonVoiceMixin(EntityType<? extends Mob> type, Level level) {
		super(type, level);
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void dragonfall$voice(CallbackInfo ci) {
		// never voice a dragon while the game is paused (ClientGame is client-only: checked after the side)
		if (!level().isClientSide || ClientGame.paused() || isSilent()) return;
		EnderDragon self = (EnderDragon) (Object) this;
		DragonBrain brain = DragonfallDragon.brain(this);
		AnimClock clock = brain.clock;
		DragonAnim anim = clock.anim();
		double seconds = clock.seconds();
		boolean same = anim == dragonfall$voiceAnim && clock.choice() != null && clock.choice().equals(dragonfall$voiceChoice);
		DragonVoice.Cue cue = same ? DragonVoice.due(anim, dragonfall$voiceSeconds, seconds) : null;
		dragonfall$voiceChoice = clock.choice();
		dragonfall$voiceAnim = anim;
		dragonfall$voiceSeconds = seconds;
		if (cue != null) DragonAudio.play(self, cue);

		int roars = getEntityData().get(DragonData.VOICE);
		if (dragonfall$roars != null && roars != dragonfall$roars) DragonAudio.roar(self, true);
		dragonfall$roars = roars;

		if (DragonVoice.attacking(anim) || brain.attacking()) brain.roar.fade(tickCount);
	}

	/** Vanilla's flap, on its own flap timer: the swings follow the model's wingbeat instead. */
	@Inject(method = "onFlap", at = @At("HEAD"), cancellable = true)
	private void dragonfall$noFlap(CallbackInfo ci) {
		ci.cancel();
	}

	/** Vanilla's growl every 10-20 s (the only sound aiStep plays): the dragon roars with its jaw open instead. */
	@WrapWithCondition(method = "aiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
	private boolean dragonfall$noGrowl(Level level, double x, double y, double z, SoundEvent sound, SoundSource source, float volume,
			float pitch, boolean delay) {
		return false;
	}

	@Inject(method = "getAmbientSound", at = @At("HEAD"), cancellable = true)
	private void dragonfall$noAmbient(CallbackInfoReturnable<SoundEvent> cir) {
		cir.setReturnValue(null);
	}
}
