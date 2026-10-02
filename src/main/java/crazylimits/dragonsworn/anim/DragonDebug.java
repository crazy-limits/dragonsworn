package crazylimits.dragonsworn.anim;

/** Client-side debug switches, set by {@code /dragonsworn anim} and by the in-game test. */
public final class DragonDebug {
	/** When set, every dragon loops this animation instead of following its phase. */
	public static volatile DragonAnim forcedAnimation;

	private DragonDebug() {}
}
