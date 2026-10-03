package crazylimits.dragonsworn.mc.client;

import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.anim.DragonAnimSelector;
import crazylimits.dragonsworn.debug.DragonDebug;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import com.geckolib.animatable.GeoReplacedEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.util.GeckoLibUtil;

import java.util.EnumMap;
import java.util.Map;

/**
 * GeckoLib's stand-in for the vanilla {@link EnderDragon}: the entity stays vanilla (advancements,
 * datapacks and other mods keep working) and only its looks are replaced. One instance serves every
 * dragon; GeckoLib keeps animation state per entity id. What plays is decided by
 * {@link DragonAnimSelector} from what the server synced (phase, flight plan, current action), the same
 * choice the server uses to place hitboxes.
 */
public final class ReplacedEnderDragon implements GeoReplacedEntity {
	/** The dragon a render state is drawn from: GeckoLib 5 hands the animation handler only the render state. */
	static final DataTicket<EnderDragon> DRAGON = DataTicket.create("dragonsworn_dragon", EnderDragon.class);

	/**
	 * Every animation in two equivalent variants: a controller only restarts when handed a different
	 * chain, so a new beat pattern or a repeated action alternates between them -- a hard reset would also
	 * drop the blend from the previous pose. The second variant ends with a stage that is never reached
	 * (every chain ends in a loop or a hold). It must not differ at the start: a leading wait has no bones,
	 * so for the whole blend GeckoLib snaps every bone to the rest pose (neck and tail straight).
	 */
	private static final Map<DragonAnim, RawAnimation[]> PLAY = new EnumMap<>(DragonAnim.class);
	/** Push sequences, [flaps - 1][variant]: the pushes, then the glide. */
	private static final RawAnimation[][] PUSHES = new RawAnimation[2][2];
	private static final Map<DragonAnim, RawAnimation> FORCED = new EnumMap<>(DragonAnim.class);

	static {
		for (int flaps = 1; flaps <= 2; flaps++) {
			for (int variant = 0; variant < 2; variant++) {
				RawAnimation raw = RawAnimation.begin();
				for (int i = 0; i < flaps; i++) raw.thenPlay(DragonAnim.FLAP.id());
				PUSHES[flaps - 1][variant] = end(raw.thenLoop(DragonAnim.GLIDE.id()), variant);
			}
		}
		for (DragonAnim anim : DragonAnim.values()) {
			RawAnimation[] variants = new RawAnimation[2];
			for (int variant = 0; variant < 2; variant++) {
				RawAnimation raw = RawAnimation.begin();
				if (anim.loops()) raw.thenLoop(anim.id());
				else if (anim == DragonAnim.DEATH) raw.thenPlayAndHold(anim.id());
				// the takeoff ends standing in the air: carry on hovering until the next plan arrives; roar,
				// bite, sweep and breath play once and settle back into the stance they were played from
				else raw.thenPlay(anim.id()).thenLoop(anim.then().id());
				variants[variant] = end(raw, variant);
			}
			PLAY.put(anim, variants);
			FORCED.put(anim, RawAnimation.begin().thenLoop(anim.id()));
		}
	}

	private static RawAnimation end(RawAnimation raw, int variant) {
		return variant == 1 ? raw.thenWait(1) : raw;
	}

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>("main", DragonAnim.BLEND_TICKS, this::animate));
	}

	private PlayState animate(AnimationTest<ReplacedEnderDragon> state) {
		EnderDragon dragon = state.getData(DRAGON);
		if (dragon == null) return PlayState.STOP;
		AnimationController<ReplacedEnderDragon> controller = state.controller();
		DragonAnim forced = DragonDebug.forcedAnimation;
		if (forced != null) {
			controller.setAnimationSpeed(1.0);
			return state.setAndContinue(FORCED.get(forced));
		}
		DragonBrain brain = DragonswornDragon.brain(dragon);
		DragonAnimSelector.Choice choice = brain.choice();
		controller.setAnimationSpeed(DragonAnimSelector.playbackSpeed(choice.anim(), brain.horizontalSpeed()));
		int variant = choice.key() & 1;
		if (choice.anim() == DragonAnim.FLAP) {
			int flaps = Math.max(1, Math.min(2, brain.flightPlan().flaps()));
			return state.setAndContinue(PUSHES[flaps - 1][variant]);
		}
		return state.setAndContinue(PLAY.get(choice.anim())[variant]);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
