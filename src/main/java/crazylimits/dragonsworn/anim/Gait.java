package crazylimits.dragonsworn.anim;

/**
 * Whether a dragon on the ground is walking, with a little memory: a walk goes on through a stall of up to
 * {@link #STOP_TICKS} (the walker turning toward its next leg, reaching a path node, planning its path
 * again) instead of dropping to idle for a tick and starting the walk over from its first frame. Through
 * the stall the walk stands mid-stride ({@link DragonAnimSelector#playbackSpeed} is 0 when it does not move).
 * One per dragon per side, ticked once a tick with the speed it moved.
 */
public final class Gait {
	/** Ticks standing still before a walk ends. */
	public static final int STOP_TICKS = 8;

	private int still = STOP_TICKS;

	public void tick(double horizontalSpeed) {
		still = horizontalSpeed > DragonAnimSelector.WALK_THRESHOLD ? 0 : Math.min(still + 1, STOP_TICKS);
	}

	/** It moved within the last {@link #STOP_TICKS} ticks. */
	public boolean walking() {
		return still < STOP_TICKS;
	}
}
