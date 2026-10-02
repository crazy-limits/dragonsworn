package crazylimits.dragonsworn.ai;

/**
 * How the dragon stands where it came down ({@code nav/LandingSite} says which fits), from the most room to
 * the least:
 * <ul>
 *   <li>{@link #STAND}: on all four limbs, the hind feet and the folded wings. It walks, bites, lashes its
 *       tail, roars.</li>
 *   <li>{@link #UPRIGHT}: no room for the wings on the ground (a ledge, a narrow ridge, a platform of a few
 *       blocks): it sits up on its hind feet alone, the tail laid behind it, the wings held out half
 *       spread to keep its balance.</li>
 *   <li>{@link #CLING}: hardly any ground at all (a pillar's top, one to four blocks): the feet grip it
 *       while the wings keep beating and carry most of the weight.</li>
 * </ul>
 * Up there it fights with its head only: it bites (and seizes) what is in reach, turns to face it, but
 * never walks, lashes its tail or roars. It takes off once its prey is out of reach.
 */
public enum Foothold {
	STAND, UPRIGHT, CLING;

	/** On its hind feet alone: no walking, no tail. */
	public boolean narrow() {
		return this != STAND;
	}

	/** From its {@link #ordinal}; {@link #STAND} for anything else. */
	public static Foothold of(int ordinal) {
		return ordinal > 0 && ordinal < values().length ? values()[ordinal] : STAND;
	}
}
