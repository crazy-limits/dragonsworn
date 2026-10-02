# Contributing to Dragonsworn

Thanks for helping. This page covers how the project is laid out, how to build and test it, and the house rules.
For the detailed design of each system (flight, hitboxes, tail, AI, breath…), read [CLAUDE.md](CLAUDE.md).

## Project layout

Builds use [Stonecutter](https://stonecutter.kikugie.dev/) 0.9: one source tree builds every
Minecraft version × loader pair. Each pair is a Gradle subproject such as `:1.21.1-fabric`.

| Path | What lives there |
|---|---|
| `src/main/java` | **The game-free core.** AI decisions, animation timing, flight, body/IK, navigation, config. It never imports Minecraft or a loader. |
| `src/test/java` | JUnit tests for the core (`./gradlew :1.21.1-fabric:test`). |
| `src/mc/<version>/java` | **The Minecraft bridge** for one version: `DragonBrain` (the AI director), phases, mixins, the GeckoLib renderer, and the in-game showcase. |
| `src/mc/shared/resources` | Textures, sounds, lang, and the block and particle definitions. |
| `src/gecko4`, `src/gecko5` | The model and animations, in the folder layout of GeckoLib 4 (1.21.1) or GeckoLib 5 (1.21.2 and later). **Generated.** |
| `src/fabric`, `src/neoforge` | Loader entrypoints only. |
| `tools/` | The Python asset pipeline that generates the model, animations, textures and `PoseData.java`. |

Put a feature's logic in the core and test it there. The bridge should only feed it from the world and apply its
answers. Core classes such as `ai/GroundTactics`, `body/Strike` and `nav/Runway` show the pattern.

## Building and testing

You need JDK 21.

```bash
./gradlew :1.21.1-fabric:build :1.21.1-neoforge:build    # both loaders + unit tests: run before every PR
```

Unit tests cover the core only. Anything that touches behaviour in the world (AI, movement, collision, rendering)
must also pass the **showcase**, the in-game test. It runs on your machine and opens a Minecraft window:

```bash
./gradlew :1.21.1-fabric:runClient -Pdragonsworn.showcase=<stage>
```

Stages: `air`, `breath`, `collision`, `config`, `death`, `grabs`, `hitboxes`, `landing`, `narrow`, `pass`,
`stance`, `walls`. Leave off `=<stage>` to run them all. Read `run/1.21.1-fabric/showcase-report.txt`: its last line must
say `RESULT PASS`. Look at the screenshots in `run/1.21.1-fabric/screenshots/` too. For changes to the End spires, run
`-Pdragonsworn.arena`.

In the PR, say which stages you ran.

## Assets

The model, animations, textures, sounds and `body/PoseData.java` are **generated**. Never edit them by hand.
To change them, edit the generators in `tools/` and rebuild:

```bash
pip install -r tools/requirements.txt
python3 tools/build_assets.py     # about 7 minutes, almost all of it the standing IK
python3 tools/sounds.py           # only for sound changes
```

Some tools (`dragon_fire.py`, `crystal_beam.py`, `sounds.py`) read Minecraft's own assets from Loom's cache, so run
a Gradle build once first. Commit the generated files together with the tool change.

The dragon's model and texture are **All Rights Reserved** (see [LICENSE-ASSETS.md](LICENSE-ASSETS.md)). Do not
redistribute them outside this repository.

## Code style

- **Formatting.** Tabs, LF line endings, UTF-8. `.editorconfig` sets these.
- **Naming.** Name constants for what they mean (`Runway.TOUCH_SPEED`, not `0.8`). Tuning that players may want to
  change goes in `config/DragonConfig`.
- **Comments.** Explain *why* and the physical meaning: units (blocks, ticks, degrees), frames (model or world),
  and where a number comes from. Don't narrate the code.
- **Core vs. bridge.** No Minecraft or loader imports in `src/main/java`. Use `//? if fabric {` or
  `//? if neoforge {` (Stonecutter) only where the loaders really differ.
- **Tests.** Every core change comes with a test. Name tests for the behaviour they check
  (`biteMissesWhenTheTargetDodges`).
- **Docs.** When you change how a system works, update its section in CLAUDE.md in the same PR.

## Common changes

**A new air attack** (see the fly-by bite for a complete example):

1. Core: put its geometry and timing in `attack/` (e.g. `attack/FlybyBite`) with tests. Add the `AirTactics.Attack`
   entry and its config switch, and give it a line in `AirTactics.REPERTOIRES` for each reach where it applies.
   Put its knobs (flag, weights, damage…) in `config/DragonConfig`.
2. Animation: add a pose in `tools/anims.py` and its `DragonAnim` entry, then rebuild the assets. If the IK aims the
   attack, add it to `body/Strike`.
3. Bridge: write a phase that extends `mc/phase/AirAttackPhase` (`JawBlow` and `FlyingBreath` cover bites and
   breath). Register it at the **end** of `DragonPhases.register()`, and add its case to the switch in
   `Tactics.start`. The compiler flags that switch when it is missing.
4. Test it in game: add a showcase stage, or a case in `AirStage`.

**A new showcase stage:** write a class in `mc/client/showcase` whose `static void build…(int x, int y, int z)`
queues steps through `Script` (`command`, `server`, `shoot`, `check`…). Add it to `Stages.SOLO`, and to
`Stages.full` on a plot that no other stage uses. List its name under "Building and testing" above.

**A new config option:** declare one constant in `config/DragonConfig` (`num(...)` or `flag(...)`) and read it
where it is used (`MY_OPTION.get()`). The file, the reload and the three config screens all pick it up.

## Adding a Minecraft version

1. Uncomment or add its `match(...)` line in `settings.gradle.kts`.
2. Add its `[fabric."x"]` and `[neoforge."x"]` dependency blocks to `stonecutter.properties.toml`.
3. Write its bridge under `src/mc/<version>/`, starting from the closest existing version.

## Licence of contributions

By contributing code, you agree that it is licensed under the LGPL-3.0-only, the licence of the project's code.
Contributed assets (textures, sounds, models) need to be your own work, and the PR must say under what terms they
are offered.
