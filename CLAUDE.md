# CLAUDE.md

Dragonsworn (formerly Dragonfall): Ender Dragon overhaul mod. Design notes: `~/Documents/Notes/obsidian-notes/Minecraft/Mods/Ideas/Dragonfall*.md`.
The entity stays vanilla `minecraft:ender_dragon`: a GeckoLib replaced-entity renderer draws it; mixins
(`src/mc/<version>/java/.../mc/mixin`, `dragonsworn.mixins.json`) add phases, flight, hitboxes and synced state.

## Hard rules
- Never hand-edit generated files: model/animation JSON, textures, `body/PoseData.java`. Change `tools/*.py`, run `python3 tools/build_assets.py` (Pillow + numpy, ~7 min).
- Three bridge trees, `src/mc/1.21.1`, `src/mc/1.21.11`, `src/mc/26.3`: a bridge change goes into all three.
- Core `src/main/java` imports no Minecraft/loader class and has no package cycle (`ArchitectureTest`).
- Every AI default lives only in `config/DragonConfig`; read options where used (`X.get()`), never cache them.
- Every player-facing text is a lang key. `en_us.json` must equal `DragonConfig.translations()` word for word; other languages use only en_us keys with the same `%s` count (`LangFilesTest`).
- Every dragon sound has a subtitle saying what the dragon does; attacks play through own events in `DragonSounds` (aliasing vanilla's via `sounds.json` `"type": "event"`, written by `sounds.py` `ALIASES`), never a vanilla event directly.
- Mixin targets differ per version: check each against `genSources`; set `require` when an injection must match several methods (otherwise a partial match fails silently).
- Planted feet/hands never slide; toes rest flat on the ground (the dragon stands on its feet, not its claws).
- Arena spires: obsidian only, vanilla's flat top, no cages, no pointed tops, no crystal-like shapes, no crying obsidian (user decisions).

## Build and test (Stonecutter 0.9.8)
```bash
./gradlew :1.21.1-fabric:build :1.21.1-neoforge:build   # jars + unit tests; all targets: ./gradlew buildAll
./gradlew :1.21.1-fabric:runClient -Pdragonsworn.showcase   # in-game test, writes run/<target>/screenshots/df-*.png + showcase-report.txt, then quits
./gradlew :1.21.1-fabric:runClient -Pdragonsworn.arena      # End spire tour: df-arena-*.png, arena-report.txt
./gradlew :1.21.1-fabric:runClient -Pdragonsworn.film       # film clips, one frame per tick: film-<clip>-*.png
python3 tools/film_gifs.py                                  # film frames -> gallery/dragonsworn-<clip>.gif
```
Targets: `1.21.1` (GeckoLib 4), `1.21.11` (GeckoLib 5.4), `26.3` (GeckoLib 5.5, package `com.geckolib`, Java 25, unobfuscated); each `-fabric` and `-neoforge`.
- Showcase: flat world, NoAI dragons play every animation, then live AI tests. `-Pdragonsworn.showcase=<stage>` runs one stage:
  `landing` (ground assault, takeoff, running landing), `stance` (ground/air/ground cycle), `pass` (breath pass), `death`, `config`,
  `narrow` (3x3 platform, lone pillar), `air` (fly-by bite, hover bite, hover breath, wild choice; ~6 min), `breath`, `walls`,
  `hitboxes`, `collision`, `grabs`. Stages: `mc/client/showcase/Stages`.
- Film (`Film`, ~6 min): player is the prey (survival, resistance 255); third-person shots use `FilmCamera` (placed by `CameraMixin`).
  `-Pdragonsworn.film=walk,jaws` films only the named scenes (`flight walk stalk breath pass claws jaws`) and keeps other frames.
  `-Pdragonsworn.film=gallery` shoots store stills `still-<shot>-<n>.png`; `python3 tools/gallery.py` grades `PICKS` into `gallery/*.jpg` (1920x1080).
- Config screen: `-Pdragonsworn.configLibs` adds YACL + Cloth to the Fabric dev client; `-Pdragonsworn.configScreen=plain|cloth|yacl` picks one.

## Release
Push tag `release`, `release-beta` or `release-alpha` at the tip of `main` (`git tag -f release-alpha && git push -f origin release-alpha`).
`.github/workflows/release.yml`: version from Conventional Commits (`.github/scripts/release.py`; first release = `mod.version`), writes it and
`CHANGELOG.md`, runs `collectAll testAll`, tags, creates the GitHub release, runs `publishAll` (`buildSrc/DragonswornPublishing.kt`; ids
`mod.modrinth`/`mod.curseforge`; secrets `MODRINTH_TOKEN`/`CURSEFORGE_TOKEN`). `publish.yml` re-uploads a released tag.
Dry run: `./gradlew publishAll -PpublishDryRun -PcurseforgeToken=x`.

## Layout
- `src/main/java/crazylimits/dragonsworn` — game-free core (Stonecutter-processed), tests in `src/test`. Packages:
  `anim` (what plays, `DragonVoice`), `flight` (wingbeat flight model), `body` (bank/neck/tail, hitbox placement, grips, strikes),
  `nav` (air/ground A*, landing sites, runways), `ai` (tactics, roaming, stance), `limb` (foot IK, terrain tilt, head look, toes),
  `attack` (breath, pass, fly-by, hover), `config`, `math`, `arena`, `debug`.
- `src/mc/<version>/java/.../mc` — Minecraft bridge:
  - `DragonBrain` (one per dragon: animation, body, hitboxes) and parts `Flight`+`AirRoute`, `HullCollision`, `PartCollision`, `Fireballs`,
    `CombatMemory`, `Tactics`, `WildDirector`, `ArenaDirector`, `PreyHold`; `DragonData` (synced fields); `Targets`.
  - `DragonPhases`: phase registry, append only (ids are saved).
  - `phase/`: air attacks extend `AirAttackPhase`, share `JawBlow` (bite hits) and `FlyingBreath`; `GroundWalker` walks a landed dragon.
  - `breath/` (own mixin config `dragonsworn.breath.mixins.json`), `arena/` (`dragonsworn.arena.mixins.json`).
  - `client/`: renderer, `DragonModel`, `LimbAnimator` (+ `TalonPose`, `ToePose`, `GroundClearance`); `GeoBones` is the only GeckoLib bone access.
    `client/showcase/`: `TestRun` runner, `Script` helpers, one class per stage, `ArenaTour`. `client/config/ConfigScreens`.
- `src/fabric`, `src/neoforge` — entrypoints only; per-version moved APIs in `src/fabric/mc<version>`, `src/neoforge/mc<version>`.
- Assets: model + animations `src/gecko4/resources` (1.21.1), `src/gecko5/resources` (1.21.2+); textures, lang, sounds `src/mc/shared/resources`.

## GeckoLib 4 vs 5
GeckoLib 5 poses bones per render pass via `BoneSnapshot`s in the renderer's `adjustModelBonesForRender`; `GeoBones.Model`/`Bone` wrap them
in GeckoLib 4's API so shared code reads the same. The animation handler sees only the render state (`ReplacedEnderDragon.DRAGON` carries the
dragon). `DragonRenderer.State` implements `GeoRenderState` itself (NeoForge cannot see GeckoLib's injected interface at compile time).
Code that read GeckoLib 4 world matrices after drawing (`BreathRender`, `LimbContact`) runs in the posing step on the limbs' forward kinematics.

## Asset pipeline (`tools/`, entry `build_assets.py`)
- `build_wings.py`: geometry from `tools/source/` (Ender Dragon Reborn CEM, converted); 4-segment neck, 9-segment tail (`chain.py`; poses
  may name `neck_rot*`/`tail_rot*`, `chain.expand` spreads them). Every cube has a `name`.
  - Wing bones: `*_wing` (shoulder: `_arm` + `_membrane`), `*_wing_tip` (elbow, `_sail`), `tip1` forearm, fingers `tip2/3/5/6` at
    0/30/60/90° round the wrist apex, `tip7` (no cube) the sail crease at 120°; `web0-5` fill finger gaps, `web6/7` the sail gap.
    `*_wing_root_web`: inner membrane folded down at x = 19 into a 24 px strip tilted 30° under the body, own (mirrored) texture;
    the renderer keeps it within 40° of its rest direction (`body/WingRoot`).
  - Only the left wing is built; each `right_wing*` bone mirrors it (pivot -x, rotation (x, -y, -z)): right poses = left poses with Y and Z negated.
  - Arm, forearm, fingers each raised 0.1 px over the previous (no z-fight). Hind toes: bones `foot_*_toe1..3` + `foot_*_back_toe`, never keyed (`limb/Toes`, `ToePose`).
- `pack_uv.py` (last step): box UV, one net per cube (right wing `mirror: true`, identical nets shared), packed by body part into 512x512 with
  skin, glowmask, heat frames. Asserts every face samples the same texels as before, except `tail_1..5` (GeckoLib floors box-UV sizes; resampled nearest).
- `anims.py`: every animation as a pose function; ground poses solved by IK (`walk.py`, `stand.py`, `ik.py` on FK `rig.py`; foot residual < 0.02 px).
  Every animation keys every neck/tail segment, `head_group` and both shoulders; the tail is keyed zero everywhere (motion is procedural).
- `parts.py`: hitbox anchors, chain pivots, tail rest geometry, flight tails (`anims.tail_track`) -> `body/PoseData.java` (long strings split by `String.join`, javac 64 KB limit).
- Preview poses offline by rendering `rig.matrices` with Pillow before a full rebuild (the standing IK takes most of the 7 min).
- Wing rule (no gaps, no crossing): shoulders rotate freely; elbows only about Z; the hand only through `fan.py` (4 gaps; sail gap pleats at
  `fan.SAIL` x its neighbour; `tip7` never moves) plus `fan.wrist_twist` keyed into `tip2` (turns the hand about the `tip7` crease).
- Standing (`stand.py`): chest up, elbow above shoulder, hand half spread (`stand.FAN`) laid on the ground along `tip2`
  (`walk.solve_limbs(hand_laid=True)`); the hand plane holds the crease, 20° off the forearm, so it cannot lie flat.
- Editor convention: +X pitches a bone's front up; files store X and Y negated.
- Other generators: `flight.py` (wingbeat), `particles.py` (void flame sprites: `void_breath_*` 48x48x16, smoke from frame 9; `void_flame_*`
  24x24x12, smoke from frame 7; must match `VoidFlameParticle.smokeFrom`), `heat.py` (breath heat frames, baked on `build_wings.py`'s layout
  before `pack_uv.py`), `dragon_fire.py` (vanilla soul fire, red/green swapped, dimmed), `crystal_beam.py` (32x512 beam with SGA rune helix;
  flows crystal -> dragon via `mc/arena/mixin/client/EnderDragonRendererMixin`), `egg.py` (dragon egg, 3 `hatch` stages via
  `DragonEggBlockMixin`; nothing hatches it yet), `icon.py` (128x128 mod icon), `sounds.py` (numpy + soundfile; cuts `roar`, `wing`, `step`
  from vanilla, writes `sounds.json`). `egg.py <png>`, `icon.py <png>` preview.

## Systems (core class -> bridge class; showcase stage)
- **Hitboxes** follow the drawn pose: `body/PartSolver` solves the pose shown (`AnimClock.shownSeconds`, GeckoLib plays `BLEND_TICKS` late,
  blending from the last solved frame) plus the renderer's bends, on both sides (`DragonBrain.placeParts`). Neck: exact FK `body/NeckChain`
  (`bend`, `aim`); head look `limb/HeadLook` ticks in `DragonBrain.look`. Stage `hitboxes`.
- **Tail**: fully procedural. `body/TailMotion` (ground motions, blended via `AnimClock.from/blend`), `body/TailTrack` (flight tails from
  `anims.tail_track`), `body/Tail` (laid on real ground, turn/look/strike bends, segments kept out of `BlockGrid.blocked`), `body/TailChain`
  (FK) used by hitboxes, renderer (solved last in `DragonModel`) and strike IK.
- **Flight**: `tools/flight.py` wingbeat (one phase angle drives all joints as harmonics; body heave/pitch/surge answer
  `flight/Wingbeat.downstroke`, the same curve as server thrust/lift). Phase 0 = wings level rising; top `DOWNSTROKE_START` 0.225, bottom
  `DOWNSTROKE_END` 0.775. `flap` = one beat out of the glide and back. Takeoff: all four limbs planted until the jump, ends on a hover beat
  boundary; server starts hover at `DragonAnim.TAKEOFF_PHASE` (`FlightModel.startAtPhase`). Turning: `body/DragonBody` (bank, `HEAD_LEAD`,
  `RUDDER`, renderer-only `wingTurn`).
- **Running landing** (`LAND`): `nav/Runway` path + skid; `GroundApproachPhase` uses it at `Runway.MIN_SPEED`+, else hovers down.
  `DragonBrain.footing()` switches body/limb IK to ground (takeoff before jump, landing after touch). Stage `landing`.
- **Breath** (stream): `attack/BreathAttack`, `attack/FlamePuff` -> `mc/breath/` (`BreathFlames`, `BreathRender`). Damage and dragon fire only
  where server puffs reach. Heat glow `client/HeatGlowLayer` over the 2 s inhale. Mouth constants in `BreathAttack` derive from the `breath`
  pose in `anims.py`: re-derive when it changes. Neck tracks alone inside `NECK_ARC`, body turns outside (`bodyTurns`); no neck sway keyed. Stage `breath`.
- **Breath pass**: `attack/BreathPass` -> `phase/BreathPassPhase` (`GLIDE_BREATH`); aim synced in `DragonData.STRIKE`; client timing
  `BreathPassPhase.breathTicks` (shared with hover breath). Stage `pass`.
- **Fireballs**: all go through `mc/Fireballs` (head aims first, glow `FIREBALL_SPEEDUP` x faster once within `FIREBALL_CONE`, dropped after
  `FIREBALL_AIM_TICKS`; never fired backwards; synced `DragonData.FIREBALL`). Arena strafe via `DragonStrafePlayerPhaseMixin`.
  Projectiles never hit their owner's parts (`ProjectileMixin`).
- **Dragon fire**: `mc/breath/DragonFire`, block `dragonsworn:dragon_fire`, placed by `DragonFire.spread` from every fire attack. `DAMAGE` 3;
  no spread, no block burning, burns out after 5-10 s except on bedrock; the dragon is immune. Crystals on bedrock burn it (`EndCrystalMixin`).
  Cutout: `BlockRenderLayerMap` (Fabric), model `render_type` (NeoForge).
- **Ground combat**: `ai/GroundTactics` (one blow at a time: front bite, side tail, behind turn) + `body/Strike` (IK puts jaws/tail tip on the
  aim at the blow frame; same bends in hitboxes and renderer; aim synced `DragonData.STRIKE`, frozen `REACTION_TICKS` before the blow; hit
  radius `Strike.BITE_HIT_RADIUS` = config `bite_radius`). Unreachable = real blind spot. `ai/HitTally` triggers takeoff.
- **Wild vs arena**: `DragonBrain.Context.WILD` lives on foot (`ai/Roaming`), fights grounded via `WildDirector.tryGroundAssault`;
  `ai/CombatStance` cycles ground fight / air break (`GROUND_LIMIT`, `AIR_LIMIT`, `BREAK_MIN..MAX`, `CALM_TICKS`). Arena keeps vanilla's
  fight but never lands on the exit portal: perches by the nearest player (`ArenaDirector.perchByPlayer`). Stage `stance`.
- **Narrow footholds**: `ai/Foothold` `UPRIGHT` / `CLING` (synced `DragonData.FOOTHOLD`), bites `UPRIGHT_BITE`/`CLING_BITE`, only bite/turn;
  takeoff after `NARROW_PATIENCE`, cling max `CLING_MAX`. Poses `anims.upright_pose`, `cling_pose`. Stage `narrow`.
- **Air combat**: `ai/AirTactics` by `Reach` (`GROUND`, `WALL`, `AIR`); `choices` tried in order, barrage last (always starts).
  `attack/FlybyBite` -> `FlybyBitePhase` (`GLIDE_BITE`); `attack/HoverAttack` -> `HoverAttackPhase` (`HOVER_BITE`, `HOVER_BREATH`, attacks start
  on beat boundaries via `DragonBrain.beatPhase`). Stage `air`.
- **Grabs**: `body/Grip` (holds, `fits`, jaw shake) -> `mc/PreyHold`: prey is carried, not mounted (level skips its tick, dragon ticks it;
  `ServerLevelMixin`, `ClientLevelMixin`, `ServerGamePacketListenerImplMixin`, `Carried`/`EntityMixin`); drawn lying (`LivingEntityRendererMixin`,
  camera `CameraMixin`). Snatch `phase/SnatchPhase` (`TalonPose`), seize in `GroundFightPhase`. Toe bones are reset from rest every frame
  (GeckoLib keeps unkeyed bones: adding spins them). Stage `grabs`.
- **Death**: `ai/DeathFlight` via `DragonDeathPhaseMixin` (fly to altar, rise; wild: rise only), then `DragonBrain.deathTick` and `DEATH`
  (cocoon). `anims.assert_wings_apart` fails the build if wings cross the body's middle. Dead `aiStep` returns early: `tickEnd` hooks every return. Stage `death`.
- **Collision**: `HullCollision` (hull never moves deeper into solid, unbreakable blocks; `pushOut`; pass-through only after `ESCAPE_TICKS`
  wedged), `AirRoute` + `nav/AirPlanner`, `nav/GroundPlanner`. Soft parts: `PartCollision` + `body/BodyPush` push what overlaps (each side its
  own entities); nothing is solid. Stages `walls`, `collision`.
- **Turning on the spot**: `limb/TurnSteps` steps planted feet in diagonal pairs; `LimbIK.solveLegReach`/`solveArmReach`; front limb held by
  `*_wing_tip3`, only while the hand is down.
- **Sound**: `anim/DragonVoice` times wing/step/roar on the animation clock; flight roars synced `DragonData.VOICE`; `RoarSound` fades;
  vanilla flap/growl/ambient silenced (`DragonVoiceMixin`).
- **Arena**: `arena/Monolith` spiral towers (`CROWN`, `WINDOW`), pure function of the spike, within `REACH`, blast-proof; crystal stays at
  vanilla's spot. `arena/EntrancePlatform` (sphere radius 5). Mixins `SpikeFeatureMixin`, `EndPlatformFeatureMixin` step aside when
  `end_island.spires` / `end_island.entrance_platform` is off or another mod's mixin targets the class (`mc/arena/OtherMods`).
- **Config**: `config/DragonConfig` + `config/Toml` -> `config/dragonsworn-server.toml` (loaded by `DragonswornCommon.loadConfig` at init,
  server start, `/reload`; clamped, missing keys written back; file stays English). Screens: YACL, else Cloth, else `PlainConfigScreen`;
  libraries compile-only. Text via `Text` (core) -> `mc/Lang.of`. Stage `config`.

## Licensing
Model and texture derive from "Ender Dragon Reborn" by Parrie43 (All Rights Reserved): personal use until permission is granted.
Dragon sounds are cut from Minecraft's (Mojang); dragon fire textures are recolored vanilla soul fire.
