# CLAUDE.md

Dragonfall: an Ender Dragon overhaul (design notes: `~/Documents/Notes/obsidian-notes/Minecraft/Mods/Ideas/Dragonfall*.md`).
Stage 1 so far: the boss **model, animations and AI**. The entity stays vanilla `minecraft:ender_dragon`:
its renderer is replaced (GeckoLib replaced-entity renderer) and mixins add phases, flight, hitboxes and
synced state (`src/mc/<version>/java/.../mc/mixin`, `dragonfall.mixins.json`).

## Build (Stonecutter 0.9.8, same layout as bookworm)

```bash
./gradlew :1.21.1-fabric:build :1.21.1-neoforge:build      # jars + unit tests
./gradlew :1.21.1-fabric:runClient -Pdragonfall.showcase    # in-game test (also :1.21.1-neoforge)
./gradlew :1.21.1-fabric:runClient -Pdragonfall.arena       # End monolith tour: df-arena-*.png, arena-report.txt
```
The showcase creates a flat world, summons NoAI dragons, plays every animation, then tests the AI live
(leaf cage + stone pillar, ground assault on a husk, takeoff, hitbox shots, a running landing) and the breath; it writes
screenshots to `run/<target>/screenshots/df-*.png`, a report to `run/<target>/showcase-report.txt`, and quits.
`-Pdragonfall.showcase=landing` runs only the live AI's ground assault + takeoff and the running landing (~2 min).
Targets 1.21.11 and 26.2 are declared (GeckoLib 5) but their `src/mc/<version>` bridge is not written yet.

## Layout
- `src/main/java` -- game-free core (Stonecutter-processed), tested in `src/test`: `anim` (what plays),
  `flight` (wingbeat-driven flight model), `body` (procedural bank/neck/tail, wings turned against the body pitch, + hitbox placement from the
  generated pose track), `nav` (air/ground A*, landing sites, runways), `ai` (ground tactics, free roaming),
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
- `flight.py` -- the wingbeat (see Flight below). `anims.py` builds every flying pose from it through `flight_pose`.
- `parts.py` -- hitbox anchors + chain pivots for every keyframe, the tail's rest geometry and the flight tails
  (`anims.tail_track`) -> generated `body/PoseData.java` (long strings are `String.join` chunks: javac's 64 KB cap).
- Previewing poses offline: render the rig's cubes through `rig.matrices` (Pillow) -- the asset build takes ~7 min,
  almost all of it the standing IK, so check flight poses that way before rebuilding.
- Every animation keys every neck/tail segment, `head_group` and both shoulders: the renderer adds procedural turns on top.
  The tail is keyed straight (zero) everywhere: its whole motion is procedural (see Tail below).
- Wing rule (no gaps, no crossing): shoulders rotate freely, elbows only about Z, the hand only through `fan.py`.
- Editor convention: +X pitches a bone's front up; files store X and Y negated.
- `particles.py` -- void flame sprites: Ice and Fire's fire-breath particle recolored to Dragon's Breath, 4 frames
  cooling white -> violet with age (embers, cloud flames); and the stream's own `void_breath_*` (10 frames, drawn):
  a cloud-shaped ball of purple fire that billows into dark smoke with the last purple flames dying in it.
- `heat.py` -- the breath's heat-glow frames (see Breath attack).
- `egg.py` -- the dragon egg's block texture (overrides `minecraft:block/dragon_egg`): overlapping scales in the
  dragon texture's palette, cracked by a glowing rune-magenta vein. 16x16; vanilla's egg model maps it upside down.

## Tail
Fully procedural. `body/TailMotion` (core, tested) is what the keyframes used to do per animation (ported from
`anims.py`/`walk.py`/`stand.py`: idle sway, walk swing, roar shake, tail-strike lift + rattle, death slump;
one-shots continue into idle/hover/glide; blends from the last animation via `AnimClock.from/blend`). The flying
animations' tails (fly, flap, glide, hover, takeoff, land) are generated with them instead (`body/TailTrack` from
`anims.tail_track`): a rope hung from the keyed body, each point where a stiff tail was `TAIL_DELAY` x its share
ago, so every heave and pitch runs down it as a wave; plus a drop on each downstroke, the hover/flare droop, the
glide sway, and the standing (laid) tail where the takeoff/landing has its feet down. `body/Tail` lays a standing tail on the real ground (bisection on the root bend; flat
model y = 0 without a world), adds the turn-trailing / head-look / strike bends, then keeps every segment's
capsule out of `BlockGrid.blocked` blocks (ring search for the smallest clear extra bend, released gradually).
`body/TailChain` is the exact FK, hung from the body bone (`PoseTrack` frame or the drawn GeckoLib bone).
Hitboxes (`PartSolver.tail`), the renderer (`LimbAnimator.State.tail`, solved last in `DragonModel`) and the
strike's IK all use it. Showcase stage `tailCage` checks the drawn tail never overlaps a block.

## Flight
`tools/flight.py` is the wingbeat, after big birds (research notes: downstroke 55 % of the beat, joints breaking
root to tip, body heave/pitch answering the push, head stabilized ~70 %): one phase angle theta, smoothly warped
(C-infinity, so no key snaps), drives every joint as harmonics: shoulder, then the hand (elbow hinge) ~0.08 beat
later, the fan's pleats and fingers rippling after; sweep forward on the downstroke (ellipse + figure-eight),
twist leading edge down on the downstroke and up on the upstroke. The body's heave, pitch and surge are the
periodic response of a damped mass to `DragonAnim.downstroke` (the very curve the server's thrust and lift use),
so it is lowest early in the downstroke and peaks at the start of the upstroke. `flight_pose` solves the neck each
frame so the head holds still, legs trail by inertia. Beat phase 0 = wings level and rising; top at
`DOWNSTROKE_START` 0.225, bottom at `DOWNSTROKE_END` 0.775 (mirrored in `DragonAnim`). A push (`flap`) is one whole
beat out of the glide and back. On the ground all four limbs stay planted: the takeoff crouches and pushes off
on all fours, the wings leave the ground only at the jump, and it ends on a hover beat boundary; the server starts
the hover at `DragonAnim.TAKEOFF_PHASE` at the jump so beats and lift stay in step (`FlightModel.startAtPhase`).
Turning (`body/DragonBody`): banks into the turn, the head leads by `HEAD_LEAD` ticks of turn rate, the tail is a
rudder while a turn tightens/opens (`RUDDER`), the wings go asymmetric (`wingTurn`: inside wing swept back, hand
drooping; outside reaching forward; twisted against the roll rate) -- the last is renderer-only.
**Running landing** (`LAND`, eagle-style): `nav/Runway` (core, tested) checks a flat strip + open glide and gives a
fixed path (cubic from the dragon's state to the touch point at `TOUCH_SPEED`, then a skid of `SKID_TICKS`);
`GroundApproachPhase` uses it when the dragon arrives at `Runway.MIN_SPEED` or more (else it hovers down), lines up
via the lead point, starts the landing `Runway.startDistance(speed)` short of the touch point and then moves the
dragon itself. The animation: legs swung forward (toes ahead), flare with one braking stroke, the wings sweep down
(`LAND_PLANT`) so the claws strike with the hind feet (IK onto `TOUCH_FEET`) at `LAND_TOUCH`, then on all fours
the body rides over the planted feet into the stance.
`DragonBrain.footing()` (the takeoff before the jump, the landing after the touch) switches body/limb IK to ground.

## Breath attack
`anim/BreathAttack` (core, tested) + `mc/breath/` (phase, particles, mixins in `dragonfall.breath.mixins.json`).
After the perched roar the dragon sometimes pours a void-flame stream instead of vanilla's cloud; the server
burns along `BreathAttack`'s cone, the client spawns flames from the model's mouth (`BreathRender`). Over the 2 s inhale
the dragon heats up (`mc/client/HeatGlowLayer`): its chest glows first, the glow climbs the throat to the jaw and
mouth, then the fire comes; it flickers while pouring and cools after. A texture animation: `tools/heat.py` bakes
8 emissive frames (`textures/entity/heat/`) from the model's UV map (chest, neck underside, jaw get ignition times
by model z); the layer draws the two frames around `BreathAttack.heat`, weighted. Rerun it if the UVs change. The
dragon-fireball and perched-breath clouds use the `dragonfall:void_flame` particle. Mouth constants in
`BreathAttack` come from the `breath` pose in `anims.py`: re-derive them if that pose changes. The neck lunges out
`BREATH_LUNGE` before the fire, because the model plays the animation `BLEND_TICKS` late. Pouring, the neck
runs out straight from the chest with the head low at the chest's height; the pose keys no neck sway (it would throw
the game's aim off). The neck follows a moving target alone; the body turns only once the target leaves the neck's
`NECK_ARC` (`BreathAttack.bodyTurns`). Showcase `-Pdragonfall.showcase=breath` runs the breath stages only,
including a husk walking across the stream (head line vs husk, body must not turn).

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

## Collision and paths
Vanilla's dragon has no physics; `DragonBrain` gives it some. The hull (head, necks, chest, hips, tail root parts)
never moves deeper into solid, unbreakable blocks (`move`: in flight; `walk`: on its feet, sliding along walls), and
`pushOut` shoves it back out a little per tick when a turn or the pose swung it into one (only a hull wedged for
`ESCAPE_TICKS` may pass through to free itself). Flight (`route`): straight when clear, else `nav/AirPlanner`'s A*
route, kept while its next leg is clear, corners cut when a later waypoint is in view; whatever its momentum carries
it into within `LOOKAHEAD_TICKS` makes it `swerve` at once and replan; a route leg steeply up (over a wall) is flown
hovering. Walking: `nav/GroundPlanner` clears the whole body (`BODY_RADIUS`, low steps under the belly), keeps off
walls, and a bent path (a detour) is walked even where it leads away from the target; `followGround` never climbs a
wall the wrists are against. Showcase stage `walls` (`-Pdragonfall.showcase=walls`) checks all of it.

## Turning on the spot
Standing (idle, bite, roar, breath), the feet never slide as the body turns: `limb/TurnSteps` (core, tested) keeps
each foot planted in the world and steps it round in diagonal pairs when it falls behind, a little inward and ahead,
or at once when its limb is at full stretch (the IK's miss > `LimbAnimator.STRAIN`). IK reaches them in 3D:
`LimbIK.solveLegReach` (hip splay + leg plane) and `solveArmReach` (shoulder 3 axes + elbow Z, bent seeds: the
standing arm is straight, a singular pose). The front limb is held by its folded hand's apex (`*_wing_tip3`, as
`walk.py` plants it), and only while the hand is down (its gap to the ground, not one point's height).

## Grabs (talons and jaws)
`body/Grip` (core, tested): the holds, their synced encoding, the jaw shake (`DragonBody` adds it to the neck
bends, so hitboxes and model agree). `mc/PreyHold`: the prey *rides* the dragon (`EnderDragonMixin.positionRider`
puts its middle in the grip every tick, both sides), so it cannot move but can use items; an ender pearl, death or
any dismount frees it, shift does not (`PlayerMixin`). `client/LivingEntityRendererMixin` draws held prey lying
flat about its middle (along the dragon in the talons, across the jaws). The snatch (`phase/SnatchPhase`, wild
attacks and the arena's holding pattern): run-up, a dive down a glide slope that homes in, the right foot reaching
(`LimbAnimator.talon`, IK), catch, a hard climb, drop from 26-40 blocks. The seize (`GroundFightPhase`): a bite that
may keep its prey, shaken and chewed, then flung; anyone else hitting the head or neck makes it let go
(`DragonBrain.hurtBy`). Showcase stage `grabs` (`-Pdragonfall.showcase=grabs` runs only it).

## Sound
`python3 tools/sounds.py` (needs numpy + soundfile) cuts vanilla's dragon sounds (read from Loom's asset cache)
into single events: `roar` (the growl's roar, cut to the roar animation's open jaw), `wing` (one swing,
slightly deeper) and `step` (hard-ground step: stone + ravager step + a low thump), plus `sounds.json`
(each with its `attenuation_distance`: they play at volume <= 1 so a roar can fade). `anim/DragonVoice`
(core, tested) times them on the animation clock: a swing per downstroke, a step per foot plant, the roar
as the jaw opens; in flight the server picks occasional roars (`DragonData.VOICE`) and the jaw opens with
them (`DragonModel`); an attack fades a roar out (`RoarSound`). Vanilla's flap timer, growl and ambient
sound are silenced (`DragonVoiceMixin`).

## Arena (End spires)
`arena/Monolith` (core, tested) replaces vanilla's obsidian cylinders with spiral towers (references: Mode Gakuen
Spiral Towers, BIG's The Spiral, twisting towers), all with vanilla's flat top and obsidian only (the user rejected
crystal-like shapes, pointed tops and crying obsidian). Every tower winds its own way (seeded direction, turns,
proportions, wing count), in one of three styles: `CROWN` = twisted rounded rectangle, two sharp corners winding up
it; `WINDOW` = core wrapped in scroll wings split by spiral slits crossed by shelves; `CAGE` (vanilla's two guarded
spikes) = square tower with a barred gallery spiralling round it and a bar cage round the crystal on its top. The
crystal stays exactly where vanilla puts it (bedrock at `height`, crystal at `height + 1`), the shape is a pure
function of the spike (respawn rebuilds it identically; the ground is probed outside the tower's footprint for that
reason), nothing goes further than `REACH` (a feature's write radius), and every block but the bars is blast-proof.
`mc/arena/mixin/SpikeFeatureMixin` (`dragonfall.arena.mixins.json`) swaps `placeSpike` for `mc/arena/Monoliths.place`,
so worldgen and the respawn ritual both use it. `mc/client/ArenaTour` is its in-game test.

## Licensing
Model and texture derive from the "Ender Dragon Reborn" pack by Parrie43 (All Rights Reserved). Personal use
until permission is granted.
The void flame particle sprites (`textures/particle/void_flame_*.png`, built by `tools/particles.py`) are
recolors of Ice and Fire's `dragon_flame.png` (AlexModGuy/Ice_and_Fire, LGPL-3.0); credit it if published.
The dragon sounds (`sounds/entity/ender_dragon/*.ogg`) are cut from Minecraft's own (Mojang).
