package crazylimits.dragonsworn.anim;

import crazylimits.dragonsworn.flight.FlightModel;

/**
 * Which frame of which animation the dragon is on, so the server (hitboxes, attack timing) and the
 * client's hitboxes follow the pose the model shows. Restarts when the {@link DragonAnimSelector.Choice}
 * changes; the walk advances with distance walked, like its playback speed. A push ({@link DragonAnim#FLAP})
 * settles into the glide after its strokes.
 */
public final class AnimClock {
	private DragonAnimSelector.Choice current;
	private double seconds;
	private int flaps = 1;
	/** What showed when the choice last changed, and how far into it; ticks since. */
	private DragonAnim from;
	private double fromSeconds;
	private int ticks;
	/** How many times the choice has changed: each change starts a new blend. */
	private int changes;

	/** Advance one tick (1/20 s) with this choice. */
	public void tick(DragonAnimSelector.Choice choice, FlightModel.Plan flight, double horizontalSpeed) {
		if (!choice.equals(current)) {
			from = current == null ? null : anim();
			changes++;
			fromSeconds = seconds();
			current = choice;
			seconds = 0.0;
			ticks = 0;
			flaps = Math.max(1, flight.flaps());
			return;
		}
		ticks++;
		seconds += DragonAnimSelector.playbackSpeed(choice.anim(), horizontalSpeed) / 20.0;
	}

	/** The choice playing: a different one restarts the clock. */
	public DragonAnimSelector.Choice choice() {
		return current;
	}

	/** The animation showing now. */
	public DragonAnim anim() {
		if (current == null) return DragonAnim.GLIDE;
		if (current.anim() == DragonAnim.FLAP && seconds >= flaps * DragonAnim.PUSH_SECONDS) return DragonAnim.GLIDE;
		return current.anim();
	}

	/** Seconds into {@link #anim()}. */
	public double seconds() {
		if (current != null && current.anim() == DragonAnim.FLAP) {
			double pushes = flaps * DragonAnim.PUSH_SECONDS;
			return seconds >= pushes ? seconds - pushes : seconds % DragonAnim.PUSH_SECONDS;
		}
		return seconds;
	}

	/** Counts the choice's changes: a different value is a new blend (from whatever showed then). */
	public int changes() {
		return changes;
	}

	/** The animation that showed before the choice changed (the model blends from it), or null. */
	public DragonAnim from() {
		return from;
	}

	/** Seconds into {@link #from()} when the choice changed. */
	public double fromSeconds() {
		return fromSeconds;
	}

	/**
	 * Seconds into {@link #anim()} the model shows at {@code partialTick}: GeckoLib blends into a new
	 * animation over {@link DragonAnim#BLEND_TICKS} from its first frame, and only then plays it.
	 */
	public double shownSeconds(float partialTick) {
		return Math.max(0.0, seconds() + (partialTick - DragonAnim.BLEND_TICKS) / 20.0);
	}

	/** Seconds into {@link #from()} the model showed when the choice changed: the pose it blends from. */
	public double fromShownSeconds() {
		return Math.max(0.0, fromSeconds + (1.0 - DragonAnim.BLEND_TICKS) / 20.0);
	}

	/**
	 * How far the model has blended from {@link #from()} into {@link #anim()} at {@code partialTick}:
	 * 0..1 over {@link DragonAnim#BLEND_TICKS}.
	 */
	public double blend(float partialTick) {
		return from == null ? 1.0 : Math.min(1.0, (ticks + partialTick) / DragonAnim.BLEND_TICKS);
	}
}
