# CLAUDE.md

Dragonfall: an Ender Dragon overhaul (design notes: `~/Documents/Notes/obsidian-notes/Minecraft/Mods/Ideas/Dragonfall*.md`).
Stage 1 so far: the boss **model, animations and AI**. The entity stays vanilla `minecraft:ender_dragon`:
its renderer is replaced (GeckoLib replaced-entity renderer) and mixins add phases, flight, hitboxes and
synced state (`src/mc/<version>/java/.../mc/mixin`, `dragonfall.mixins.json`).

## Build (Stonecutter 0.9.8, same layout as bookworm)

```bash
./gradlew :1.21.1-fabric:build :1.21.1-neoforge:build      # jars + unit tests
./gradlew :1.21.1-fabric:runClient -Pdragonfall.showcase    # in-game test (also :1.21.1-neoforge)
```
The showcase creates a flat world, summons NoAI dragons, plays every animation, then tests the AI live
(leaf cage + stone pillar, ground assault on a husk, takeoff, hitbox shots) and the breath; it writes
screenshots to `run/<target>/screenshots/df-*.png`, a report to `run/<target>/showcase-report.txt`, and quits.
Targets 1.21.11 and 26.2 are declared (GeckoLib 5) but their `src/mc/<version>` bridge is not written yet.

## Layout
- `src/main/java` -- game-free core (Stonecutter-processed), tested in `src/test`: `anim` (what plays),
  `flight` (wingbeat-driven flight model), `body` (procedural bank/neck/tail, wings turned against the body pitch, + hitbox placement from the
  generated pose track), `nav` (air/ground A*, landing sites), `ai` (ground tactics, free roaming),
  `limb` (foot IK on uneven ground, body tilt over terrain, head look-at; applied by `mc/client/LimbAnimator`).
- `src/mc/<version>/java` -- Minecraft bridge per MC version: `mc` (DragonBrain: AI director, movement,
  collision, parts; phases in `mc/phase`; mixins), `mc/client` (GeckoLib renderer, model, showcase).
- `src/fabric`, `src/neoforge` -- entrypoints only (`src/neoforge/mc<version>` for moved APIs).
- `src/gecko4/resources` (1.21.1) / `src/gecko5/resources` (1.21.2+) -- model + animations; textures in `src/mc/shared/resources`.
- `tools/` -- the asset pipeline. **Never hand-edit the generated model/animation JSON.**

## Asset pipeline (`python3 tools/build_assets.py`, needs Pillow)
- `build_wings.py` -- geometry from `tools/source/` (the Ender Dragon Reborn CEM model, converted): fan-wing
  wedge slices, 4-segment neck and 9-segment tail (`chain.py`, per-face UV; the neck split was verified
  pixel-exact in Blockbench). Poses may still name `neck_rot*`/`tail_rot*`; `chain.expand` spreads them.
- `anims.py` -- every animation as a pose function. Ground poses are solved by IK (`walk.py`, `stand.py`,
  `ik.py` on the FK in `rig.py`), so planted feet do not slide (residuals < 0.02 px).
- `parts.py` -- hitbox anchors + chain pivots for every keyframe, + the tail's rest geometry -> generated `body/PoseData.java`.
- Every animation keys every neck/tail segment, `head_group` and both shoulders: the renderer adds procedural turns on top.
  The tail is keyed straight (zero) everywhere: its whole motion is procedural (see Tail below).
- Wing rule (no gaps, no crossing): shoulders rotate freely, elbows only about Z, the hand only through `fan.py`.
- Editor convention: +X pitches a bone's front up; files store X and Y negated.
- `particles.py` -- void flame sprites: Ice and Fire's fire-breath particle recolored to Dragon's Breath, 4 frames
  cooling white -> violet with age.
- `egg.py` -- the dragon egg's block texture (overrides `minecraft:block/dragon_egg`): overlapping scales in the
  dragon texture's palette, cracked by a glowing rune-magenta vein. 16x16; vanilla's egg model maps it upside down.

## Tail
Fully procedural. `body/TailMotion` (core, tested) is what the keyframes used to do per animation (ported from
`anims.py`/`walk.py`/`stand.py`: idle sway, walk swing, the wingbeat's wave, hover droop, glide sway, roar shake,
tail-strike lift + rattle, death slump; one-shots continue into idle/hover; blends from the last animation via
`AnimClock.from/blend`). `body/Tail` lays a standing tail on the real ground (bisection on the root bend; flat
model y = 0 without a world), adds the turn-trailing / head-look / strike bends, then keeps every segment's
capsule out of `BlockGrid.blocked` blocks (ring search for the smallest clear extra bend, released gradually).
`body/TailChain` is the exact FK, hung from the body bone (`PoseTrack` frame or the drawn GeckoLib bone).
Hitboxes (`PartSolver.tail`), the renderer (`LimbAnimator.State.tail`, solved last in `DragonModel`) and the
strike's IK all use it. Showcase stage `tailCage` checks the drawn tail never overlaps a block.

## Breath attack
`anim/BreathAttack` (core, tested) + `mc/breath/` (phase, particles, mixins in `dragonfall.breath.mixins.json`).
After the perched roar the dragon sometimes pours a void-flame stream instead of vanilla's cloud; the server
burns along `BreathAttack`'s cone, the client spawns flames from the model's mouth (`BreathRender`). The
dragon-fireball and perched-breath clouds use the `dragonfall:void_flame` particle. Mouth constants in
`BreathAttack` come from the `breath` pose in `anims.py`: re-derive them if that pose changes.

## Ground combat
`ai/GroundTactics` (core, tested) picks one blow at a time (bite and tail share one recovery): in front the bite,
to the side the tail (nearer the head: a dice roll between the two), behind it turns round, unless the
target just hurt it from there (then the tail). `body/Strike` (core, tested) aims the blow by IK on top of the
keyframes: the neck + head (or the 9 tail segments) are bent so that on the blow's frame the jaws (or the tail's
tip) are exactly on the aim; the same bends go into the hitboxes (`PartSolver`) and the renderer (`DragonModel`),
the aim is synced in `DragonData.STRIKE`. The aim follows the target until `REACTION_TICKS` before the blow, then
only what is at the jaws/tip when it lands is hit (a dodge is a miss). Whether a blow reaches at all is the IK's
answer, so the dragon has real blind spots; `ai/HitTally` makes it take off when hit too often at once. The
stream breath uses the same aim: the neck is straightened toward a point that chases the target at
`BreathAttack.AIM_SPEED`, and the flames go from the model's mouth to it.

## Sound
`python3 tools/sounds.py` (needs numpy + soundfile) cuts vanilla's dragon sounds (read from Loom's asset cache)
into single events: `roar` (the growl's roar, cut to the roar animation's open jaw), `wing` (one swing,
slightly deeper) and `step` (hard-ground step: stone + ravager step + a low thump), plus `sounds.json`
(each with its `attenuation_distance`: they play at volume <= 1 so a roar can fade). `anim/DragonVoice`
(core, tested) times them on the animation clock: a swing per downstroke, a step per foot plant, the roar
as the jaw opens; in flight the server picks occasional roars (`DragonData.VOICE`) and the jaw opens with
them (`DragonModel`); an attack fades a roar out (`RoarSound`). Vanilla's flap timer, growl and ambient
sound are silenced (`DragonVoiceMixin`).

## Licensing
Model and texture derive from the "Ender Dragon Reborn" pack by Parrie43 (All Rights Reserved). Personal use
until permission is granted.
The void flame particle sprites (`textures/particle/void_flame_*.png`, built by `tools/particles.py`) are
recolors of Ice and Fire's `dragon_flame.png` (AlexModGuy/Ice_and_Fire, LGPL-3.0); credit it if published.
The dragon sounds (`sounds/entity/ender_dragon/*.ogg`) are cut from Minecraft's own (Mojang).
