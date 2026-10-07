package crazylimits.dragonsworn.nav;


/**
 * The world as a wall's face sees it ({@link Surface.Face}): in the face's own frame the wall is the
 * ground, so the planners, the landing sites and the tail work on it unchanged. A column is searched
 * from {@code top} (out in the air in front of the wall) down into it, at most {@link #DEPTH} blocks: what
 * the dragon would grip. The floor's view is the world itself ({@link #of}).
 */
public final class SurfaceGrid implements BlockGrid {
	/** How far into the face a column is searched for the wall (blocks). */
	public static final int DEPTH = 40;
	/** How far out from a height the search starts ({@link #around}). */
	public static final int OUT = 10;

	private final BlockGrid world;
	private final Surface.Face face;
	private final int top;

	private SurfaceGrid(BlockGrid world, Surface.Face face, int top) {
		this.world = world;
		this.face = face;
		this.top = top;
	}

	/**
	 * {@code world} seen from {@code face}, its columns searched from local height {@code top} down; the
	 * world itself for the floor.
	 */
	public static BlockGrid of(BlockGrid world, Surface.Face face, int top) {
		return face == Surface.Face.FLOOR ? world : new SurfaceGrid(world, face, top);
	}

	/** {@link #of}, the columns searched from {@link #OUT} blocks over local height {@code y}. */
	public static BlockGrid around(BlockGrid world, Surface.Face face, double y) {
		return of(world, face, (int) Math.floor(y) + OUT);
	}

	/** The world under a face's view ({@code grid} itself when it is the world: the floor's). */
	public static BlockGrid world(BlockGrid grid) {
		return grid instanceof SurfaceGrid view ? view.world : grid;
	}

	@Override
	public boolean blocked(int x, int y, int z) {
		int[] w = face.blockToWorld(x, y, z);
		return world.blocked(w[0], w[1], w[2]);
	}

	@Override
	public int ground(int x, int z) {
		if (blocked(x, top, z)) return NO_GROUND;
		for (int y = top - 1; y >= top - DEPTH; y--) {
			if (blocked(x, y, z)) return y + 1;
		}
		return NO_GROUND;
	}
}
