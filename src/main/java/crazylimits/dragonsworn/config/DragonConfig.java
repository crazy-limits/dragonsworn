package crazylimits.dragonsworn.config;

import crazylimits.dragonsworn.Text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * The server's say over the dragon's AI: which attacks it has, how it picks between them, when it flies,
 * lands, wanders and gives up. Read from {@code config/dragonsworn-server.toml} as the server starts and on
 * {@code /reload}; every value is clamped to its range, and a missing file or key is written out with its
 * default. Game-free: the loaders hand it the file ({@code DragonswornCommon.loadConfig}).
 *
 * <p>Each option is a constant here, read where it is used ({@code SEIZE_CHANCE.get()}): a reload takes
 * effect at once. Each default lives only here: the AI reads the option, never a constant of its own, so
 * tests run on the shipped behaviour.
 */
public final class DragonConfig {
	public static final String FILE = "dragonsworn-server.toml";

	/** The file's tables, in the order they are written, each with its heading. */
	private static final Map<String, String> SECTIONS = new LinkedHashMap<>();
	private static final List<Option> OPTIONS = new ArrayList<>();
	/** The file last loaded, where {@link #save} writes (null before the first load). */
	private static Path file;

	static {
		section("dragon", "The dragon itself.");
		section("targeting", "Who the dragon goes after.");
		section("wandering", "A wild dragon with nothing to hunt roams: long spells on foot, short flights between them.");
		section("stance", "A wild dragon fights on foot; hurt too much there it takes a break in the air, then comes back down.");
		section("crowd", """
				The more players fight it, the fiercer it gets: its attacks come sooner, and players bunched together
				draw its area attacks (breath, fireballs, the wing buffet).""");
		section("ground_combat", "Fighting on its feet: bite, tail strike, roar, the seize (a bite that holds on), narrow footholds.");
		section("climbing", """
				Walls: it flies in and grips a cliff, a spire or a pillar where its feet find a block to stand on, for a
				short while, to reach prey there or hiding in tunnels dug into it; then it flies off again.""");
		section("air_combat", "A wild dragon's attacks from the air, and when it lands to fight instead.");
		section("attacks", "Which air attacks exist at all. A disabled attack is never used, not even as a fallback.");
		section("attacks.open_ground", """
				How a target on open ground (room to land beside it) is attacked from the air: the odds of each attack
				being the first choice (relative weights). If the first choice cannot start (no line of sight, prey
				too big to carry...) the attacks listed after it are tried in this order. 0 = never the first choice.""");
		section("attacks.wall", "As above, for a target the dragon cannot land beside (a wall's top, a pillar, a ledge).");
		section("attacks.air", "As above, for a target in the air (elytra, creative flight, a drop under it).");
		section("snatch", "The snatch: a dive that grabs the prey in its talons, a climb, and a drop.");
		section("fireballs", "The fireball pass and the hovering barrage.");
		section("flyby_bite", "The fly-by bite: a bite in passing, harder the faster it flies.");
		section("hover_attacks", "Hovering in close to bite or breathe.");
		section("breath", "The void-flame breath (perched stream, breath pass, hovering breath) and the fire it leaves.");
		section("arena", "The End fight's dragon: vanilla's fight, plus landings beside players and air attacks.");
		section("end_island", "The End island's shape and its crystals' wards (worldgen; the respawn ritual rebuilds the spires the same way).");
	}

	// ---------------------------------------------------------------- dragon

	public static final Num MAX_HEALTH = num("dragon", "max_health", 500.0, 1.0, 1024.0,
			"Health points (2 = one heart), on every difficulty: 500 as the Warden (vanilla's dragon has 200).");

	// ---------------------------------------------------------------- targeting

	public static final Num HUNT_RANGE = num("targeting", "hunt_range", 48.0, 0.0, 256.0,
			"Blocks: a wild dragon hunts survival players this close (0 = it only fights back).");
	public static final Num FORGET_RANGE = num("targeting", "forget_range", 128.0, 8.0, 512.0,
			"Blocks: whoever hurt a wild dragon is chased until this far away.");
	public static final Num WAKE_RANGE = num("targeting", "wake_range", 24.0, 0.0, 128.0,
			"Blocks: a dragon resting on the ground wakes for a player this close.");

	// ---------------------------------------------------------------- wandering

	public static final Int FLIGHT_MIN = num("wandering", "flight_min", 200, 20, 72000,
			"Ticks (20 = 1 s) a roaming flight lasts before it looks for somewhere to land: at least ...");
	public static final Int FLIGHT_MAX = num("wandering", "flight_max", 500, 20, 72000, "... and at most.");
	public static final Int GROUND_MIN = num("wandering", "ground_min", 3000, 20, 720000,
			"Ticks it stays on foot (walking, resting) before it flies again: at least ...");
	public static final Int GROUND_MAX = num("wandering", "ground_max", 7000, 20, 720000, "... and at most.");
	public static final Int PAUSE_MIN = num("wandering", "pause_min", 40, 0, 6000,
			"Ticks it stands and looks around between walks: at least ...");
	public static final Int PAUSE_MAX = num("wandering", "pause_max", 200, 0, 6000, "... and at most.");
	public static final Num WALK_MIN = num("wandering", "walk_min", 8.0, 2.0, 64.0, "Blocks per walk: at least ...");
	public static final Num WALK_MAX = num("wandering", "walk_max", 22.0, 2.0, 64.0, "... and at most.");
	public static final Num FLY_LEG_MIN = num("wandering", "fly_leg_min", 24.0, 8.0, 256.0,
			"Blocks per leg of a roaming flight: at least ...");
	public static final Num FLY_LEG_MAX = num("wandering", "fly_leg_max", 45.0, 8.0, 256.0, "... and at most.");
	public static final Num CRUISE_MIN = num("wandering", "cruise_height_min", 10.0, 4.0, 128.0,
			"Blocks over the ground it cruises at: at least ...");
	public static final Num CRUISE_MAX = num("wandering", "cruise_height_max", 20.0, 4.0, 128.0, "... and at most.");
	public static final Num DRIFT = num("wandering", "drift_degrees", 55.0, 0.0, 180.0,
			"Degrees each new leg (walk or flight) may turn from the last. Low: it travels on; high: it stays round one spot.");

	// ---------------------------------------------------------------- stance

	public static final Num GROUND_HEALTH_LIMIT = num("stance", "ground_health_limit", 0.2, 0.0, 1.0,
			"Fraction of its max health lost on the ground that sends it up for a break in the air (1 = never).");
	public static final Num AIR_HEALTH_LIMIT = num("stance", "air_health_limit", 0.1, 0.0, 1.0,
			"Fraction of its max health lost during the break that brings it back down early.");
	public static final Int BREAK_MIN = num("stance", "break_min", 300, 0, 72000,
			"Ticks a break in the air lasts (attacking from there): at least ...");
	public static final Int BREAK_MAX = num("stance", "break_max", 600, 0, 72000, "... and at most.");
	public static final Int CALM_TICKS = num("stance", "calm_ticks", 600, 1, 72000,
			"Ticks without a target after which it calms down: the next fight starts on the ground again.");
	public static final Flag OVERWHELM_TAKEOFF = flag("stance", "overwhelm_takeoff", true,
			"Take off when hit too many times at once on the ground (from its blind spots). Any dragon, the End's too.");
	public static final Int OVERWHELM_HITS = num("stance", "overwhelm_hits", 4, 1, 32,
			"That many hits ...");
	public static final Int OVERWHELM_WINDOW = num("stance", "overwhelm_window", 50, 1, 1200, "... within this many ticks.");

	// ---------------------------------------------------------------- crowd

	public static final Num CROWD_RANGE = num("crowd", "range", 48.0, 8.0, 256.0,
			"Blocks from the dragon a survival player counts toward the crowd it fights.");
	public static final Num PACE_PER_PLAYER = num("crowd", "pace_per_player", 0.35, 0.0, 4.0,
			"Each player past the first speeds up its attacks by this much (0.35: against two, the pauses between attacks run 1.35 times as fast; 0 = off).");
	public static final Num MAX_PACE = num("crowd", "max_pace", 2.5, 1.0, 10.0, "Its attacks come at most this many times as fast as against one player.");
	public static final Num GUARD_PACE = num("crowd", "guard_pace", 2.0, 1.0, 10.0,
			"The End's dragon with a player at its crystals: its attacks come at least this many times as fast (1 = no faster).");
	public static final Num GROUP_RADIUS = num("crowd", "group_radius", 6.0, 1.0, 32.0,
			"Blocks: players this close to the one it attacks count as bunched together with it.");
	public static final Num AREA_BIAS = num("crowd", "area_bias", 1.0, 0.0, 10.0,
			"Each player bunched with its target makes an area attack (breath pass, hovering breath, fireballs) this much likelier to be its first choice (1: twice as likely with two players, three times with three; 0 = off).");
	public static final Int MOB_BUFFET = num("crowd", "mob_buffet", 2, 0, 16,
			"On the ground: that many players within its wing buffet's reach get the buffet before any bite or tail strike (0 = never).");

	// ---------------------------------------------------------------- ground combat

	public static final Flag BITE = flag("ground_combat", "bite", true, "Bite what is in front of it.");
	public static final Flag TAIL_STRIKE = flag("ground_combat", "tail_strike", true, "Lash its tail at what is beside or behind it.");
	public static final Flag ROAR = flag("ground_combat", "roar", true,
			"Roar at a target out of reach while nobody is close: slows everything in its range (whoever runs away or shoots from afar).");
	public static final Flag WING_BUFFET = flag("ground_combat", "wing_buffet", true,
			"Beat both wings at what is too close to bite or lash (under its chin, at its flanks, behind its hips): it throws everything round its body away.");
	public static final Flag SEIZE = flag("ground_combat", "seize", true, "Bites may hold on: the prey is shaken, chewed and flung.");
	public static final Flag NARROW_FOOTHOLDS = flag("ground_combat", "narrow_footholds", true,
			"Come down on a ledge or a pillar's top beside its prey (sat up, or clinging) where all four feet do not fit.");
	public static final Num SIDE_BITE_CHANCE = num("ground_combat", "side_bite_chance", 0.5, 0.0, 1.0,
			"A target to its side, nearer the head than the tail: the chance it bites rather than lashes its tail.");
	public static final Int BITE_RECOVERY = num("ground_combat", "bite_recovery", 12, 0, 1200, "Ticks after a bite before the next blow.");
	public static final Int TAIL_RECOVERY = num("ground_combat", "tail_recovery", 20, 0, 1200, "Ticks after a tail strike before the next blow.");
	public static final Int BUFFET_RECOVERY = num("ground_combat", "wing_buffet_recovery", 40, 0, 1200, "Ticks after a wing buffet before the next blow.");
	public static final Int REACTION_TICKS = num("ground_combat", "dodge_window", 7, 0, 20,
			"Ticks before a bite or tail strike lands that its aim is committed: the window to step out of the way.");
	public static final Int ROAR_COOLDOWN_MIN = num("ground_combat", "roar_cooldown_min", 360, 20, 72000, "Ticks between roars: at least ...");
	public static final Int ROAR_COOLDOWN_MAX = num("ground_combat", "roar_cooldown_max", 560, 20, 72000, "... and at most.");
	public static final Num ROAR_RANGE = num("ground_combat", "roar_range", 32.0, 0.0, 64.0,
			"Blocks: it roars only at a target closer than this, and the roar slows everything this close.");
	public static final Num ROAR_QUIET = num("ground_combat", "roar_quiet_range", 12.0, 0.0, 64.0,
			"Blocks: it roars only while no target is this close (one in reach gets a blow instead).");
	public static final Int ROAR_SLOW_TICKS = num("ground_combat", "roar_slowness", 140, 0, 1200, "Ticks the roar slows whoever it reaches.");
	public static final Int PROVOKED_TICKS = num("ground_combat", "provoked_ticks", 40, 0, 1200,
			"Ticks a hit counts: hurt from behind this recently, it answers with its tail instead of turning round.");
	public static final Num SEIZE_CHANCE = num("ground_combat", "seize_chance", 0.35, 0.0, 1.0, "The chance a bite is a seize.");
	public static final Int SEIZE_COOLDOWN = num("ground_combat", "seize_cooldown", 300, 0, 72000, "Ticks after a seize before the next can be.");
	public static final Int HOLD_MIN = num("ground_combat", "hold_min", 70, 1, 1200, "Ticks the jaws hold their prey: at least ...");
	public static final Int HOLD_MAX = num("ground_combat", "hold_max", 110, 1, 1200, "... and at most.");
	public static final Int CHEW_INTERVAL = num("ground_combat", "chew_interval", 20, 1, 1200, "Ticks between chews of the prey held.");
	public static final Num BITE_DAMAGE = num("ground_combat", "bite_damage", 12.0, 0.0, 1000.0, "Damage of a bite.");
	public static final Num BITE_RADIUS = num("ground_combat", "bite_radius", 2.0, 0.5, 8.0,
			"Blocks: how close the jaws must come to a body to hit it (standing and hovering bites).");
	public static final Num TAIL_DAMAGE = num("ground_combat", "tail_damage", 9.0, 0.0, 1000.0, "Damage of a tail strike.");
	public static final Num BUFFET_DAMAGE = num("ground_combat", "wing_buffet_damage", 5.0, 0.0, 1000.0, "Damage of a wing buffet.");
	public static final Num BUFFET_RANGE = num("ground_combat", "wing_buffet_range", 7.0, 2.0, 16.0,
			"Blocks from its middle the wing buffet reaches (and a target this close in a blind spot gets it).");
	public static final Num BUFFET_KNOCKBACK = num("ground_combat", "wing_buffet_knockback", 1.6, 0.0, 5.0,
			"How hard the wing buffet throws what it hits away (blocks a tick).");
	public static final Num SEIZE_DAMAGE = num("ground_combat", "seize_damage", 4.0, 0.0, 1000.0, "Damage of the bite that seizes.");
	public static final Num CHEW_DAMAGE = num("ground_combat", "chew_damage", 3.0, 0.0, 1000.0, "Damage of each chew.");
	public static final Num LOSE_DISTANCE = num("ground_combat", "lose_distance", 40.0, 8.0, 256.0,
			"Blocks: a target this far off (or 10 blocks above or below) ...");
	public static final Int LOSE_TICKS = num("ground_combat", "lose_ticks", 60, 1, 6000, "... for this many ticks is given up: it takes off.");
	public static final Int NARROW_PATIENCE = num("ground_combat", "narrow_patience", 100, 1, 6000,
			"Ticks its prey may stay out of its jaws' reach while it is on a narrow foothold before it takes off.");
	public static final Int UNREACHED_PATIENCE = num("ground_combat", "unreached_patience", 160, 1, 6000,
			"Ticks its prey may stay out of reach of its blows (up a pillar, across a ditch) while it cannot walk any closer before it takes off to fight from the air.");
	public static final Int CLING_MAX = num("ground_combat", "cling_max", 500, 1, 72000, "Ticks it clings to a pillar's top at most.");
	public static final Int ARENA_STAY_MIN = num("ground_combat", "arena_stay_min", 400, 20, 72000,
			"The End's dragon: ticks it stays on the ground once landed: at least ...");
	public static final Int ARENA_STAY_MAX = num("ground_combat", "arena_stay_max", 700, 20, 72000, "... and at most.");

	// ---------------------------------------------------------------- climbing

	public static final Flag CLIMBING = flag("climbing", "climbing", true,
			"Climb: grip walls it flies to (wall_landing), hop onto ledges and down off them, reach prey hiding in tunnels.");
	public static final Flag WALL_LANDING = flag("climbing", "wall_landing", true,
			"Fly in and grip a wall beside prey it cannot land by (on a cliff, a spire's flank, in a tunnel's mouth).");
	public static final Num HOP_RANGE = num("climbing", "hop_range", 12.0, 4.0, 24.0, "Blocks: the furthest it hops across the ground (onto a ledge, down off one).");
	public static final Int HOP_PATIENCE = num("climbing", "hop_patience", 30, 1, 6000,
			"Ticks its prey may stay out of reach while it cannot walk any closer before it looks for a hop toward it.");
	public static final Num TUNNEL_REACH = num("climbing", "tunnel_reach", 3.0, 0.0, 8.0,
			"Blocks: how far into a tunnel too narrow for its head its bite reaches past the mouth.");
	public static final Flag TUNNEL_BREATH = flag("climbing", "tunnel_breath", true, "Pour its breath down a tunnel its prey hides in beyond its bite.");
	public static final Int TUNNEL_BREATH_COOLDOWN = num("climbing", "tunnel_breath_cooldown", 200, 0, 72000,
			"Ticks after a breath down a tunnel before the next.");
	public static final Int WALL_REST = num("climbing", "wall_rest", 100, 1, 6000, "Ticks it stays on a wall with nobody to fight.");
	public static final Int WALL_TIME = num("climbing", "wall_time", 160, 20, 6000,
			"Ticks it stays on a wall at most, fighting or not, before it flies off again.");

	// ---------------------------------------------------------------- air combat

	public static final Flag LAND_TO_FIGHT = flag("air_combat", "land_to_fight", true,
			"A wild dragon lands beside its target to fight it on foot. Off: it fights only from the air.");
	public static final Int LANDING_RETRY = num("air_combat", "landing_retry", 40, 1, 6000,
			"Ticks between tries to land beside its target (attacks from the air in between).");
	public static final Int FIRST_ATTACK = num("air_combat", "first_attack_delay", 100, 0, 6000, "Ticks after it appears before its first air attack.");
	public static final Int EARNEST_COOLDOWN_MIN = num("air_combat", "earnest_cooldown_min", 80, 1, 72000,
			"Ticks between air attacks on a break in the air, or at a target it cannot land by: at least ...");
	public static final Int EARNEST_COOLDOWN_MAX = num("air_combat", "earnest_cooldown_max", 160, 1, 72000, "... and at most.");
	public static final Int CASUAL_COOLDOWN_MIN = num("air_combat", "casual_cooldown_min", 140, 1, 72000,
			"Ticks between air attacks while it would rather land and fight on foot: at least ...");
	public static final Int CASUAL_COOLDOWN_MAX = num("air_combat", "casual_cooldown_max", 260, 1, 72000, "... and at most.");
	public static final Num AIRBORNE_GAP = num("air_combat", "airborne_gap", 3.0, 1.0, 64.0,
			"Blocks of air under a target that make it a target in the air.");
	public static final Int WALLED_TICKS = num("air_combat", "walled_ticks", 200, 1, 72000,
			"Ticks a target it found no landing beside counts as out of reach on the ground.");

	// ---------------------------------------------------------------- attacks

	public static final Flag SNATCH = flag("attacks", "snatch", true, "Dive and carry off the prey in its talons, then drop it.");
	public static final Flag BREATH_PASS = flag("attacks", "breath_pass", true, "A gliding pass raking the ground with void flame.");
	public static final Flag FIREBALL_PASS = flag("attacks", "fireball_pass", true, "A low run at the target with one fireball.");
	public static final Flag CHARGE = flag("attacks", "charge", true, "Vanilla's charge: straight at the target.");
	public static final Flag BARRAGE = flag("attacks", "barrage", true, "Hovers off to one side and fires several fireballs.");
	public static final Flag FLYBY_BITE = flag("attacks", "flyby_bite", true, "A bite in passing.");
	public static final Flag HOVER_BITE = flag("attacks", "hover_bite", true, "Hovers in close and bites.");
	public static final Flag HOVER_BREATH = flag("attacks", "hover_breath", true, "Hovers and breathes void flame at the target.");

	public static final Num GROUND_SNATCH = weight("attacks.open_ground", "snatch", 0.2);
	public static final Num GROUND_BREATH_PASS = weight("attacks.open_ground", "breath_pass", 0.25);
	public static final Num GROUND_FIREBALL_PASS = weight("attacks.open_ground", "fireball_pass", 0.2);
	public static final Num GROUND_CHARGE = weight("attacks.open_ground", "charge", 0.2);
	public static final Num GROUND_BARRAGE = weight("attacks.open_ground", "barrage", 0.15);

	public static final Num WALL_SNATCH = weight("attacks.wall", "snatch", 0.1);
	public static final Num WALL_FLYBY_BITE = weight("attacks.wall", "flyby_bite", 0.25);
	public static final Num WALL_BREATH_PASS = weight("attacks.wall", "breath_pass", 0.15);
	public static final Num WALL_HOVER_BITE = weight("attacks.wall", "hover_bite", 0.2);
	public static final Num WALL_HOVER_BREATH = weight("attacks.wall", "hover_breath", 0.2);
	public static final Num WALL_BARRAGE = weight("attacks.wall", "barrage", 0.1);

	public static final Num AIR_FLYBY_BITE = weight("attacks.air", "flyby_bite", 0.35);
	public static final Num AIR_HOVER_BITE = weight("attacks.air", "hover_bite", 0.25);
	public static final Num AIR_HOVER_BREATH = weight("attacks.air", "hover_breath", 0.3);
	public static final Num AIR_BARRAGE = weight("attacks.air", "barrage", 0.1);

	// ---------------------------------------------------------------- attack details

	public static final Num DROP_MIN = num("snatch", "drop_height_min", 26.0, 0.0, 128.0, "Blocks over where it caught its prey that it drops it: at least ...");
	public static final Num DROP_MAX = num("snatch", "drop_height_max", 40.0, 0.0, 128.0, "... and at most.");

	public static final Int BARRAGE_SHOTS = num("fireballs", "barrage_shots", 3, 1, 32, "Fireballs in a barrage.");
	public static final Int BARRAGE_INTERVAL = num("fireballs", "barrage_interval", 30, 5, 1200, "Ticks between a barrage's fireballs.");

	public static final Num FLYBY_DAMAGE = num("flyby_bite", "base_damage", 6.0, 0.0, 1000.0, "Damage of a fly-by bite at a standstill ...");
	public static final Num FLYBY_SPEED_DAMAGE = num("flyby_bite", "speed_damage", 8.0, 0.0, 1000.0,
			"... plus this per block/tick of its speed (it flies at about 0.9..1.3) ...");
	public static final Num FLYBY_MAX_DAMAGE = num("flyby_bite", "max_damage", 20.0, 0.0, 1000.0, "... and at most this.");
	public static final Num FLYBY_RADIUS = num("flyby_bite", "radius", 2.5, 0.5, 8.0, "Blocks: how close the jaws must come to a body to hit it.");

	public static final Int HOVER_BITES = num("hover_attacks", "bites", 3, 1, 16, "Bites in one hovering attack, at most.");
	public static final Num HOVER_BITE_DAMAGE = num("hover_attacks", "bite_damage", 10.0, 0.0, 1000.0, "Damage of a hovering bite.");
	public static final Num HOVER_BREATH_DAMAGE = num("hover_attacks", "breath_damage", 5.0, 0.0, 1000.0,
			"Damage of the hovering breath (every 10 ticks a target stays in it).");

	public static final Num STREAM_CHANCE = num("breath", "perched_stream_chance", 0.5, 0.0, 1.0,
			"Perched, after its roar: the chance it pours the void-flame stream rather than vanilla's lingering breath cloud.");
	public static final Int MAX_STREAMS = num("breath", "streams_per_landing", 2, 0, 16,
			"Streams per perch; after the last it takes off.");
	public static final Num STREAM_DAMAGE = num("breath", "stream_damage", 5.0, 0.0, 1000.0,
			"Damage of the perched stream (every 10 ticks a target stays in it).");
	public static final Num PASS_DAMAGE = num("breath", "pass_damage", 5.0, 0.0, 1000.0,
			"Damage of the breath pass (every 4 ticks a target stays in it).");
	public static final Flag DRAGON_FIRE = flag("breath", "dragon_fire", true,
			"Fire attacks (breath, fireballs, the perched cloud) leave dragon fire where they land. It burns out by itself and never spreads.");

	// ---------------------------------------------------------------- arena

	public static final Flag ARENA_GROUND_ASSAULT = flag("arena", "ground_assault", true, "Now and then it lands beside a player on the island to fight on foot.");
	public static final Flag GUARD_CRYSTALS = flag("arena", "guard_crystals", true,
			"It guards its crystals: it goes after the player nearest a crystal (and nearest itself) first, one at a time, and comes sooner.");
	public static final Num GUARD_RADIUS = num("arena", "guard_radius", 24.0, 4.0, 128.0, "Blocks from a crystal a player counts as a threat to it.");
	public static final Num GUARD_DRAGON_WEIGHT = num("arena", "guard_dragon_weight", 0.5, 0.0, 4.0,
			"Picking whom to go after: how much nearness to the dragon counts, against nearness to a crystal (0 = the crystals alone).");
	public static final Num GUARD_SWITCH = num("arena", "guard_switch", 12.0, 0.0, 128.0,
			"Blocks nearer a crystal another player must be to draw it off the one it is after.");
	public static final Int GUARD_RETRY = num("arena", "guard_retry", 40, 1, 72000, "Ticks before it goes at a player threatening its crystals again.");
	public static final Flag PERCH_FIGHT_BACK = flag("arena", "perch_fight_back", true,
			"Perched and hit by someone close, it gets up and fights them on foot rather than sitting still.");
	public static final Num ARENA_SNATCH_CHANCE = num("arena", "snatch_chance", 0.3, 0.0, 1.0,
			"When it goes for a player on the ground: the chance of a snatch instead of a landing ...");
	public static final Num ARENA_BREATH_PASS_CHANCE = num("arena", "breath_pass_chance", 0.25, 0.0, 1.0, "... and of a breath pass.");
	public static final Int ARENA_FIRST_ASSAULT = num("arena", "first_assault_delay", 900, 0, 72000,
			"Ticks after it appears before it first goes for a player.");
	public static final Int ARENA_RETRY = num("arena", "retry", 100, 1, 72000, "Ticks before it looks again when nobody could be attacked.");
	public static final Int ARENA_AFTER_LANDING_MIN = num("arena", "after_landing_min", 900, 1, 72000,
			"Ticks after a landing to fight before the next, while crystals stand: at least ...");
	public static final Int ARENA_AFTER_LANDING_MAX = num("arena", "after_landing_max", 1500, 1, 72000, "... and at most.");
	public static final Int ARENA_NO_CRYSTALS_MIN = num("arena", "after_landing_no_crystals_min", 400, 1, 72000,
			"The same, once the crystals are gone: at least ...");
	public static final Int ARENA_NO_CRYSTALS_MAX = num("arena", "after_landing_no_crystals_max", 800, 1, 72000, "... and at most.");
	public static final Int ARENA_AFTER_PASS_MIN = num("arena", "after_pass_min", 500, 1, 72000, "Ticks after a snatch or breath pass: at least ...");
	public static final Int ARENA_AFTER_PASS_MAX = num("arena", "after_pass_max", 900, 1, 72000, "... and at most.");
	public static final Int ARENA_AFTER_AIR_MIN = num("arena", "after_air_attack_min", 200, 1, 72000,
			"Ticks after an air attack on a player it cannot land by (in the air, up a spire): at least ...");
	public static final Int ARENA_AFTER_AIR_MAX = num("arena", "after_air_attack_max", 400, 1, 72000, "... and at most.");
	public static final Num ISLAND = num("arena", "island_radius", 100.0, 16.0, 1000.0,
			"Blocks from the fight's center a player counts as on the island (perches and assaults go only there).");

	// ---------------------------------------------------------------- end island

	public static final Flag SPIRES = flag("end_island", "spires", true,
			"Spiral obsidian spires in place of vanilla's obsidian pillars (crystals stay where vanilla puts them).");
	public static final Flag ENTRANCE_PLATFORM = flag("end_island", "entrance_platform", true,
			"The spherical obsidian arrival platform in place of vanilla's flat one.");
	public static final Int WARDED_EASY = num("end_island", "warded_crystals_easy", 2, 0, 10,
			"Crystals warded by three turning rings of runes (projectiles bounce off them; break them up close) on Easy and Peaceful: those on the shortest spires, the ones vanilla cages (needs the spires; 0 = none) ...");
	public static final Int WARDED_NORMAL = num("end_island", "warded_crystals_normal", 3, 0, 10, "... on Normal ...");
	public static final Int WARDED_HARD = num("end_island", "warded_crystals_hard", 4, 0, 10, "... and on Hard.");

	private DragonConfig() {}

	// ---------------------------------------------------------------- reading values

	/** A random whole number between two options (inclusive; either order). */
	public static int between(Int min, Int max, RandomGenerator random) {
		int a = min.get(), b = max.get();
		return a == b ? a : random.nextInt(Math.min(a, b), Math.max(a, b) + 1);
	}

	/** A random number between two options (either order). */
	public static double between(Num min, Num max, RandomGenerator random) {
		double a = min.get(), b = max.get();
		return Math.min(a, b) + random.nextDouble() * Math.abs(b - a);
	}

	// ---------------------------------------------------------------- the file

	/** Every option back to its default (tests; a reload starts from the file). */
	public static synchronized void defaults() {
		for (Option o : OPTIONS) o.reset();
	}

	/**
	 * Reads {@code file}: options it sets take its values (clamped), the rest their defaults. A missing
	 * file, or one missing options, is written back out complete (its values kept, comments renewed).
	 * Returns what was wrong with it, for the log; nothing it reads can break the AI.
	 */
	public static synchronized List<String> load(Path file) {
		DragonConfig.file = file;
		List<String> problems = new ArrayList<>();
		Map<String, Object> values = new LinkedHashMap<>();
		boolean rewrite = !Files.exists(file);
		if (!rewrite) {
			try {
				values.putAll(Toml.parse(Files.readString(file, StandardCharsets.UTF_8), problems));
			} catch (IOException e) {
				problems.add("could not read " + file + ": " + e.getMessage() + " (defaults used)");
				defaults();
				return problems;
			}
		}
		for (Option o : OPTIONS) {
			Object raw = values.remove(o.path());
			if (raw == null) {
				o.reset();
				rewrite = true;
				continue;
			}
			String problem = o.set(raw);
			if (problem != null) problems.add(o.path() + ": " + problem);
		}
		for (String unknown : values.keySet()) problems.add("unknown option " + unknown + " (ignored)");
		if (rewrite) {
			try {
				Files.createDirectories(file.toAbsolutePath().getParent());
				Files.writeString(file, write(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				problems.add("could not write " + file + ": " + e.getMessage());
			}
		}
		return problems;
	}

	/** Writes every option's current value to the file last loaded (a config screen's save). */
	public static synchronized void save() throws IOException {
		if (file == null) return;
		Files.createDirectories(file.toAbsolutePath().getParent());
		Files.writeString(file, write(), StandardCharsets.UTF_8);
	}

	/** The tables in the file's order, each with its heading (config screens). */
	public static Map<String, String> sections() {
		return Collections.unmodifiableMap(SECTIONS);
	}

	/** Every option, in the file's order within its table (config screens). */
	public static List<Option> options() {
		return Collections.unmodifiableList(OPTIONS);
	}

	/** {@code "ground_combat"} as {@code "Ground combat"}; {@code "attacks.open_ground"} as {@code "Open ground"}. */
	public static String label(String name) {
		String last = name.substring(name.lastIndexOf('.') + 1).replace('_', ' ');
		return Character.toUpperCase(last.charAt(0)) + last.substring(1);
	}

	/** A table's name in the config screens, translatable. */
	public static Text sectionLabel(String name) {
		return new Text("dragonsworn.config.section." + name, label(name));
	}

	/** A table's heading (its comment in the file, as one paragraph), translatable. */
	public static Text sectionHeading(String name) {
		return new Text("dragonsworn.config.section." + name + ".tooltip", SECTIONS.get(name).replace('\n', ' '));
	}

	/** Every text the config screens show, key to English: what {@code en_us.json} must hold (tested). */
	public static Map<String, String> translations() {
		Map<String, String> out = new LinkedHashMap<>();
		for (String name : SECTIONS.keySet()) {
			for (Text t : List.of(sectionLabel(name), sectionHeading(name))) out.put(t.key(), t.english());
		}
		for (Option o : OPTIONS) {
			for (Text t : List.of(o.labelText(), o.commentText())) out.put(t.key(), t.english());
		}
		out.put(Flag.RANGE_KEY, Flag.RANGE);
		out.put(Int.RANGE_KEY, Int.RANGE);
		return out;
	}

	/** The whole file, every option at its current value. */
	public static synchronized String write() {
		StringBuilder out = new StringBuilder();
		out.append("# Dragonsworn: the dragon's AI (server side). Ticks: 20 = 1 second. Damage: 2 = one heart.\n");
		out.append("# Read when the server starts and on /reload. Values out of range are clamped.\n");
		out.append("# Options missing from this file are added back with their defaults (the file is rewritten then).\n");
		for (Map.Entry<String, String> section : SECTIONS.entrySet()) {
			out.append("\n");
			for (String line : section.getValue().split("\n")) out.append("# ").append(line.strip()).append('\n');
			out.append('[').append(section.getKey()).append("]\n");
			for (Option o : OPTIONS) {
				if (!o.section.equals(section.getKey())) continue;
				if (!o.comment.isEmpty()) out.append("# ").append(o.comment).append('\n');
				out.append("# ").append(o.range()).append('\n');
				out.append(o.key).append(" = ").append(Toml.format(o.value())).append('\n');
			}
		}
		return out.toString();
	}

	// ---------------------------------------------------------------- options

	private static void section(String name, String heading) {
		SECTIONS.put(name, heading);
	}

	private static Flag flag(String section, String key, boolean value, String comment) {
		return add(new Flag(section, key, comment, value));
	}

	private static Int num(String section, String key, int value, int min, int max, String comment) {
		return add(new Int(section, key, comment, value, min, max));
	}

	private static Num num(String section, String key, double value, double min, double max, String comment) {
		return add(new Num(section, key, comment, value, min, max));
	}

	private static Num weight(String section, String key, double value) {
		return num(section, key, value, 0.0, 100.0, "");
	}

	private static <T extends Option> T add(T option) {
		if (!SECTIONS.containsKey(option.section)) throw new IllegalStateException("no section " + option.section);
		OPTIONS.add(option);
		return option;
	}

	public abstract static sealed class Option permits Flag, Int, Num {
		final String section, key, comment;

		Option(String section, String key, String comment) {
			this.section = section;
			this.key = key;
			this.comment = comment;
		}

		public String path() {
			return section + "." + key;
		}

		public String section() {
			return section;
		}

		public String key() {
			return key;
		}

		/** What it does, for a tooltip; a weight's says so. */
		public String comment() {
			return comment.isEmpty() ? "Relative weight of this attack being the first choice (0 = never first)." : comment;
		}

		/** Its range and default, as the file's comment line. */
		public String describeRange() {
			return range();
		}

		/** Its key as a readable name. */
		public String label() {
			return DragonConfig.label(key);
		}

		/** {@link #label}, translatable. */
		public Text labelText() {
			return new Text("dragonsworn.config.option." + path(), label());
		}

		/** {@link #comment}, translatable (every weight shares one). */
		public Text commentText() {
			return comment.isEmpty() ? new Text("dragonsworn.config.weight.tooltip", comment())
					: new Text("dragonsworn.config.option." + path() + ".tooltip", comment);
		}

		/** {@link #describeRange}, translatable. */
		public abstract Text rangeText();

		abstract Object value();

		String range() {
			return rangeText().toEnglish();
		}

		abstract void reset();

		/** Takes a value read from the file; returns what was wrong with it (it is then kept in range, or left at its default), or null. */
		abstract String set(Object raw);
	}

	public static final class Flag extends Option {
		private final boolean fallback;
		private volatile boolean value;

		Flag(String section, String key, String comment, boolean value) {
			super(section, key, comment);
			this.fallback = this.value = value;
		}

		public boolean get() {
			return value;
		}

		public boolean defaultValue() {
			return fallback;
		}

		public void set(boolean value) {
			this.value = value;
		}

		@Override
		Object value() {
			return value;
		}

		static final String RANGE_KEY = "dragonsworn.config.range.flag", RANGE = "true or false, default: %s";

		@Override
		public Text rangeText() {
			return new Text(RANGE_KEY, RANGE, fallback);
		}

		@Override
		void reset() {
			value = fallback;
		}

		@Override
		String set(Object raw) {
			if (raw instanceof Boolean b) {
				value = b;
				return null;
			}
			value = fallback;
			return "expected true or false, got " + Toml.format(raw) + " (default used)";
		}
	}

	public static final class Int extends Option {
		private final int fallback, min, max;
		private volatile int value;

		Int(String section, String key, String comment, int value, int min, int max) {
			super(section, key, comment);
			this.fallback = this.value = value;
			this.min = min;
			this.max = max;
		}

		public int get() {
			return value;
		}

		public int defaultValue() {
			return fallback;
		}

		public int min() {
			return min;
		}

		public int max() {
			return max;
		}

		public void set(int value) {
			this.value = Math.max(min, Math.min(max, value));
		}

		@Override
		Object value() {
			return value;
		}

		static final String RANGE_KEY = "dragonsworn.config.range.number", RANGE = "range: %s .. %s, default: %s";

		@Override
		public Text rangeText() {
			return new Text(RANGE_KEY, RANGE, min, max, fallback);
		}

		@Override
		void reset() {
			value = fallback;
		}

		@Override
		String set(Object raw) {
			if (!(raw instanceof Number n) || raw instanceof Double d && d != Math.rint(d)) {
				value = fallback;
				return "expected a whole number, got " + Toml.format(raw) + " (default used)";
			}
			long v = n.longValue();
			set((int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v)));
			return v < min || v > max ? "out of range " + min + " .. " + max + ", set to " + value : null;
		}
	}

	public static final class Num extends Option {
		private final double fallback, min, max;
		private volatile double value;

		Num(String section, String key, String comment, double value, double min, double max) {
			super(section, key, comment);
			this.fallback = this.value = value;
			this.min = min;
			this.max = max;
		}

		public double get() {
			return value;
		}

		public double defaultValue() {
			return fallback;
		}

		public double min() {
			return min;
		}

		public double max() {
			return max;
		}

		/** As a float (damage). */
		public float f() {
			return (float) value;
		}

		public void set(double value) {
			this.value = Math.max(min, Math.min(max, value));
		}

		@Override
		Object value() {
			return value;
		}

		@Override
		public Text rangeText() {
			return new Text(Int.RANGE_KEY, Int.RANGE, Toml.format(min), Toml.format(max), Toml.format(fallback));
		}

		@Override
		void reset() {
			value = fallback;
		}

		@Override
		String set(Object raw) {
			if (!(raw instanceof Number n) || !Double.isFinite(n.doubleValue())) {
				value = fallback;
				return "expected a number, got " + Toml.format(raw) + " (default used)";
			}
			double v = n.doubleValue();
			set(v);
			return v < min || v > max ? "out of range " + Toml.format(min) + " .. " + Toml.format(max) + ", set to " + Toml.format(value) : null;
		}
	}
}
