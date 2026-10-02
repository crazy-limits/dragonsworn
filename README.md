# Dragonsworn

An Ender Dragon overhaul for Minecraft. The dragon is still vanilla's `minecraft:ender_dragon`, with a new GeckoLib
model and animations and a new AI:

- **A new body.** Fan-folding wings that it walks on, a procedural neck and tail, IK for the feet, and wingbeat-driven
  flight. Its hitboxes follow the model as drawn.
- **Fighting on the ground.** Bites and tail strikes aimed by IK, grabs, a void-flame breath, and blind spots you can
  use.
- **Fighting from the air.** Fly-by bites, hover attacks, breath passes, snatches, and fireballs that charge with a
  glow.
- **Wild dragons.** Outside the End, a dragon lives on foot and lands to fight you.
- **The End arena.** Twisting obsidian spires replace vanilla's pillars, and the dragon fire and End crystal beams
  are redrawn.
- **A server config.** Every AI setting can be changed, through a config screen built with YACL, Cloth Config, or
  plain vanilla widgets.

| Minecraft | Loaders | Status |
|---|---|---|
| 1.21.1 | Fabric, NeoForge | in development |
| 1.21.11, 26.2 | Fabric, NeoForge | planned (paused in `settings.gradle.kts`) |

Requires [GeckoLib](https://modrinth.com/mod/geckolib); Fabric also requires the Fabric API.

## Building

You need JDK 21. Gradle downloads everything else.

```bash
./gradlew :1.21.1-fabric:build :1.21.1-neoforge:build   # jars in versions/<target>/build/libs, plus the unit tests
./gradlew :1.21.1-fabric:runClient                      # a dev client (also :1.21.1-neoforge)
```

The in-game test, which also works for NeoForge:

```bash
./gradlew :1.21.1-fabric:runClient -Pdragonsworn.showcase            # every stage
./gradlew :1.21.1-fabric:runClient -Pdragonsworn.showcase=<stage>    # one stage, e.g. air, walls, landing, breath
./gradlew :1.21.1-fabric:runClient -Pdragonsworn.arena               # a tour of the End spires
```

The showcase builds a test world, runs the dragon through its animations and AI, and quits. It writes screenshots to
`run/<target>/screenshots/` and a pass/fail report to `run/<target>/showcase-report.txt`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). [CLAUDE.md](CLAUDE.md) is the detailed architecture reference.

## License

The code is under the [LGPL-3.0](LICENSE). The dragon's model and textures derive from Parrie43's
"Ender Dragon Reborn" pack and are **All Rights Reserved**. The sounds and fire textures are derived from
Minecraft's. See [LICENSE-ASSETS.md](LICENSE-ASSETS.md).
