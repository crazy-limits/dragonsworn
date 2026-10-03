![Dragonsworn banner](https://i.ibb.co/j9s7mb2z/dragonswarn-banner.png)

Dragonsworn gives the Ender Dragon a new model, new animations and a new AI. It flies on its wingbeats, lands to hunt
you on foot, bites, sweeps with its tail, grabs you and breathes void fire. Every blow is aimed at you, so if you dodge
in time it misses.

The dragon is still vanilla's `minecraft:ender_dragon`. The End fight, its advancements and other mods that work with
the dragon keep working.

## Model and movement

- The model has wings that fold, fan out and double as front legs, a long neck that bends, and a tail that swings,
  balances and follows every turn.
- Its wingbeats push it through the air. It banks into turns, glides and hovers, takes off from a crouch and lands at
  a run, claws first.
- On the ground its feet stay where they land and step properly, on uneven ground too and while it turns on the spot.
- The hitboxes follow the drawn model, so the head is where you see the head. Stand inside the dragon and it pushes you
  aside like any mob.
- It has its own sounds: a roar timed to its open jaws, wingbeats and heavy footsteps.

![The dragon walking on its wings and hind legs](https://i.ibb.co/ksJdQZK5/dragonsworn-walk.gif)

![The dragon landing at a run, claws first](https://i.ibb.co/Z60ZdpNb/08-landing.png)

## Fighting on the ground

The dragon comes down to fight you on foot and picks one blow at a time:

- Bite: it keeps its jaws on you until the last moment. Step away in time and it snaps at air.
- Tail strike: stand at its side and the tail comes round.
- Grab: a bite can catch you, shake you and fling you away. It lets go if someone hits its head.
- Void-flame breath: as it inhales its chest glows, the glow climbs its throat, and then it pours a stream of purple
  fire after you.

A blow lands only where the dragon can reach, so it has blind spots. It turns to face you, though, and if you hurt it
too much at once it takes to the air.

![The dragon catching a player in its jaws](https://i.ibb.co/DHFWdcPp/dragonsworn-jaw-grab.gif)

![The dragon's chest and throat glowing as it inhales before the breath](https://i.ibb.co/2Y1Vx88x/04-breath-inhale.png)

![The dragon pouring a stream of void fire](https://i.ibb.co/BVcGZMZC/dragonsworn-breath.gif)

## Fighting from the air

When it can't land beside you, because you are on a pillar or a narrow ledge or flying with an elytra, it attacks from
the air:

- fly-by bites at full speed, and hover bites face to face
- breath passes that rake the ground with void fire as it flies over
- snatches: it dives, catches you in its talons, climbs high and drops you
- fireballs, each one given away by its glowing throat

Where there is just enough room it perches on a narrow foothold, sitting up on its hind legs or clinging to a pillar's
top, and bites at you from there.

![The dragon raking the ground with void fire as it flies over](https://i.ibb.co/Q3pKVd2w/dragonsworn-breath-pass.gif)

![The dragon snatching a player in its talons](https://i.ibb.co/mFYhrPvt/dragonsworn-claw-grab.gif)

![The dragon hovering and breathing void fire onto the ground](https://i.ibb.co/rG2SdTzC/01-hover-breath.png)

## Dragon fire

Every fire attack leaves dragon fire where it lands. It is violet soul fire that hurts more than normal fire, stands on
any solid block and burns out after a few seconds. The dragon is immune to it.

## The End

- Spiral obsidian spires replace the vanilla pillars. Each twists its own way, and the End crystals sit where they
  always do. Respawning the dragon rebuilds them exactly as they were.
- The crystal beams are redrawn with Standard Galactic runes that flow from the crystals to the dragon.
- The portal drops you on a rounded entrance platform.
- The dragon perches beside you instead of on the exit portal.
- When it dies, it flies to the altar, rises, wraps its wings round itself like a cocoon and dies there.
- The dragon egg is covered in the dragon's scales.

![The dragon dying in a burst of light, wrapped in its wings](https://i.ibb.co/tM8STqKt/02-death.png)

## Wild dragons

Outside the End fight a dragon lives on foot. It walks and rests, and flies only to get somewhere else. When it finds a
target it lands beside it and fights. Hurt it enough and it takes to the air for a while, then comes back down.

## Roadmap

Most of what this page describes works but is still being tuned and extended.

| Feature | Status |
|---|---|
| New model and animations | In progress |
| Flight, takeoff and running landing | In progress |
| Ground combat: bite, tail strike, grab, breath | In progress |
| Air attacks: fly-by and hover bites, breath pass, snatch, fireballs | In progress |
| Perching on narrow footholds | In progress |
| Dragon fire | In progress |
| Death in a cocoon of wings | In progress |
| Wild dragons outside the End | In progress |
| Spiral obsidian spires | In progress |
| Rune crystal beams | Done |
| Rounded entrance platform | Done |
| Server config and config screen | Done |
| New dragon egg (looks, crack stages) | In progress |
| Boss phases with shield crystals | Planned |
| Scaling with the number of players | Planned |
| New attacks | Planned |
| Wing damage that grounds the dragon, a tail that can be severed | Planned |
| Compatibility with other mods, such as the dragon casting spells from Iron's Spells 'n Spellbooks | Planned |
| More Minecraft versions | Planned |

## Configuration

`config/dragonsworn-server.toml` covers almost all of the AI: which attacks it uses and how often, damage, cooldowns,
targeting, and whether the spires and the entrance platform are replaced. `/reload` applies changes at once.

The in-game config screen uses YACL or Cloth Config if one is installed, and plain vanilla widgets otherwise. Open it
from Mod Menu (Fabric) or the Mods list (NeoForge).

## Requirements

| Minecraft | Loaders |
|---|---|
| 1.21.1 | Fabric, NeoForge |

- [GeckoLib](https://modrinth.com/mod/geckolib) (required)
- [Fabric API](https://modrinth.com/mod/fabric-api) (required on Fabric)
- [Mod Menu](https://modrinth.com/mod/modmenu), [YACL](https://modrinth.com/mod/yacl), [Cloth Config](https://modrinth.com/mod/cloth-config) (optional, for the config screen)

Install it on both the server and the client.

## Compatibility

If another mod changes the End's pillars or entrance platform (YUNG's Better End Island, for example), Dragonsworn
leaves those to it. You can also turn them off in the config.

## Credits and license

- The dragon's model and textures are based on the "Ender Dragon Reborn" resource pack by Parrie43 (All Rights
  Reserved).
- The dragon's sounds and the dragon fire textures are made from Minecraft's own.
- The mod's code is open source under the LGPL-3.0.

Dragonsworn is in active development. Bug reports and ideas are welcome on the issue tracker.
