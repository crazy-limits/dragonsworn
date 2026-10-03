# CLAUDE.md

Dragonsworn (formerly Dragonfall): an Ender Dragon overhaul (design notes: `~/Documents/Notes/obsidian-notes/Minecraft/Mods/Ideas/Dragonfall*.md`).
Stage 1 so far: the boss **model, animations and AI**. The entity stays vanilla `minecraft:ender_dragon`:
its renderer is replaced (GeckoLib replaced-entity renderer) and mixins add phases, flight, hitboxes and
synced state (`src/mc/<version>/java/.../mc/mixin`, `dragonsworn.mixins.json`).

## Build (Stonecutter 0.9.8, same layout as bookworm)

```bash
./gradlew :1.21.1-fabric:build :1.21.1-neoforge:build      # jars + unit tests
./gradlew :1.21.1-fabric:runClient -Pdragonsworn.showcase    # in-game test (also :1.21.1-neoforge)
./gradlew :1.21.1-fabric:runClient -Pdragonsworn.arena       # End monolith tour: df-arena-*.png, arena-report.txt
./gradlew :1.21.1-fabric:runClient -Pdragonsworn.film        # End island clips, a frame a tick: film-<clip>-*.png
python3 tools/film_gifs.py                                     # -> gallery/dragonsworn-<clip>.gif
```
The film (`Film`, ~6 min) levels a pad on the main island, ends the vanilla fight and films flight + running landing,
takeoff, walk, stalk (first person), stream breath (third + first person), breath pass, claw grab and jaw grab (third +
first person each). The player is the prey (survival, resistance 255); third-person shots use a free camera
(`FilmCamera`, placed by `CameraMixin`) so the player's model is in the shot. `-Pdragonsworn.film=walk,jaws` films
only those scenes (`flight`, `walk`, `stalk`, `breath`, `pass`, `claws`, `jaws`); the others' frames are kept.
`-Pdragonsworn.film=gallery` (only when named) shoots the CurseForge stills instead: composed shots (low angles, long
lens, gamma up), each a burst of `still-<shot>-<n>.png`; `python3 tools/gallery.py` grades the picked frames (`PICKS`)
into `gallery/*.jpg` (1920x1080).
The showcase creates a flat world, summons NoAI dragons, plays every animation, then tests the AI live
(leaf cage + stone pillar, ground assault on a husk, takeoff, hitbox shots, a running landing) and the breath; it writes
screenshots to `run/<target>/screenshots/df-*.png`, a report to `run/<target>/showcase-report.txt`, and quits.
`-Pdragonsworn.showcase=landing` runs only the live AI's ground assault + takeoff and the running landing (~2 min).
`-Pdragonsworn.showcase=stance` runs only the wild fight's ground -> air break -> ground cycle (~1.5 min).
`-Pdragonsworn.showcase=pass` runs only the breath pass over a husk (~20 s).
`-Pdragonsworn.showcase=death` runs only a wild dragon's death (brought down in the air: the rise, then the cocoon; ~25 s).
`-Pdragonsworn.showcase=config` runs only the config screen check (screenshots, the file written).
`-Pdragonsworn.showcase=narrow` runs only the narrow footholds (a 3x3 platform, a lone pillar beside a husk's pillar).
`-Pdragonsworn.showcase=air` runs only the air attacks (a husk on a lone 16-block pillar, one hanging in the air: fly-by
bite, hover bite, hover breath each, then the wild AI's own choice; ~6 min).
Targets: 1.21.1 (GeckoLib 4), 1.21.11 (GeckoLib 5.4) and 26.3 (GeckoLib 5.5, `com.geckolib`, Java 25, unobfuscated), each
on Fabric and NeoForge (`:1.21.11-fabric`, `:26.3-neoforge`, ...; `./gradlew buildAll`). Each has its own bridge tree
`src/mc/<version>` (the 1.21.11 and 26.3 trees were ported from 1.21.1: a change to the bridge goes into every tree).
Porting notes: GeckoLib 5 poses bones per render pass through `BoneSnapshot`s handed to the renderer's
`adjustModelBonesForRender` (`GeoBones.Model`/`Bone` wrap them with GeckoLib 4's whole-turn API, so `LimbAnimator` & co.
read the same); the animation handler sees only the render state (`ReplacedEnderDragon.DRAGON` carries the dragon);
`DragonRenderer.State` implements `GeoRenderState` itself (NeoForge does not see GeckoLib's injected interface at
compile time); what read GeckoLib 4's world matrices after drawing (`BreathRender`, `LimbContact`) runs in the posing
step on the limbs' forward kinematics. Mixin targets moved a lot between versions: check each against the decompiled
sources (`genSources`) -- an injection that matches only some of its methods fails silently unless `require` says how many.
Releasing (same setup as shared-resources-cl): push a `release`, `release-beta` or `release-alpha` tag at the tip of
`main` (`git tag -f release-alpha && git push -f origin release-alpha`). `.github/workflows/release.yml` picks the version
from Conventional Commits (`.github/scripts/release.py`; the first release is `mod.version`), writes it and `CHANGELOG.md`,
runs `collectAll testAll`, tags it, makes the GitHub release and runs `publishAll` (mod-publish-plugin, wired in
`buildSrc/DragonswornPublishing.kt`; ids `mod.modrinth`/`mod.curseforge`, org secrets `MODRINTH_TOKEN`/`CURSEFORGE_TOKEN`).
`publish.yml` re-uploads a released tag by hand. Check uploads locally: `./gradlew publishAll -PpublishDryRun -PcurseforgeToken=x`.

## Layout
- `src/main/java` -- game-free core (Stonecutter-processed), tested in `src/test`: `anim` (what plays),
  `flight` (wingbeat-driven flight model), `body` (procedural bank/neck/tail, wings turned against the body pitch, + hitbox placement from the
  generated pose track), `nav` (air/ground A*, landing sites, runways), `ai` (ground tactics, free roaming, combat stance),
  `limb` (foot IK on uneven ground, body tilt over terrain, head look-at; applied by `mc/client/LimbAnimator`),
  `attack` (breath, breath pass, fly-by and hover attack geometry/timing), `config` (`DragonConfig`: every AI knob and
  the only home of its default), `math` (shared clamps, easing, angles, 3x3 rotations), `arena`, `debug`.
  Packages form layers: `ArchitectureTest` fails on an import cycle or any game/loader import in the core.
- `src/mc/<version>/java` -- Minecraft bridge per MC version:
  - `mc`: `DragonBrain` (one per dragon: what plays, body, hitboxes) and its parts: `Flight` (+ `AirRoute`),
    `HullCollision`, `Fireballs`, `CombatMemory`, `Tactics`, `WildDirector`/`ArenaDirector`; `DragonPhases` (the
    phase registry: append only, ids are saved); `Targets`; mixins.
  - `mc/phase`: the phases. Air attacks extend `AirAttackPhase` (target, run-up, way out) and share `JawBlow`
    (what a bite hits) and `FlyingBreath` (the stream in flight); `GroundWalker` walks a landed dragon.
  - `mc/client`: GeckoLib renderer and model, `LimbAnimator` (+ `TalonPose`, `ToePose`, `GeoBones`: the only
    GeckoLib bone access, `GroundClearance`); `mc/client/showcase`: the in-game test (`TestRun` runner, `Script`
    steps and helpers, one class per stage, `Stages` lists them; `ArenaTour`).
- `src/fabric`, `src/neoforge` -- entrypoints only (`src/fabric/mc<version>` (`FabricClientParts`), `src/neoforge/mc<version>` for moved APIs).
- `src/gecko4/resources` (1.21.1) / `src/gecko5/resources` (1.21.2+) -- model + animations; textures in `src/mc/shared/resources`.
- `tools/` -- the asset pipeline. **Never hand-edit the generated model/animation JSON.**

## Asset pipeline (`python3 tools/build_assets.py`, needs Pillow + numpy)
- `build_wings.py` -- geometry from `tools/source/` (the Ender Dragon Reborn CEM model, converted): fan-wing
  wedge slices, 4-segment neck and 9-segment tail (`chain.py`, per-face UV; the neck split was verified
  pixel-exact in Blockbench). Poses may still name `neck_rot*`/`tail_rot*`; `chain.expand` spreads them.
  Wing parts: `*_wing` (shoulder: `_arm` + `_membrane`), `*_wing_tip` (elbow, the `_sail` membrane), `tip1`
  forearm, fingers `tip2/3/5/6` at 0/30/60/90 deg round the wrist apex, `tip7` (no cube) the sail's crease at
  120 deg; slices `web0-5` fill the finger gaps, `web6/7` the sail gap (tip6-tip7, cut out of the sail);
  `*_wing_root_web`: the inner membrane folds down at x = 19 (just outside the flank) into a 24 px strip tilted 30 deg in under the body, its
  own texture (the membrane's art mirrored across the fold); the renderer turns it about the fold so it keeps
  pointing where it points at rest (`body/WingRoot`, within 40 deg of that). Every cube carries a `name`. Only the left
  wing is built: every `right_wing*` bone is its exact mirror (pivot -x, rotation (x, -y, -z)), so right-side
  poses are the left's with Y and Z negated (also the web pleats), and the right wears the left's art.
  Arm, forearm and fingers are raised 0.1 px each over the one before (no z-fight where they overlap at the
  wrist). Every hind toe is its own bone hinged at its knuckle (`foot_*_toe1..3`, the toe + the claw under its
  tip) and each hind foot has a back toe (`foot_*_back_toe`: the middle toe + claw again, turned 180 degrees);
  no animation keys them: `limb/Toes` (core, tested) turns them in `ToePose` (see Grabs).
- `pack_uv.py` -- last step: lays the texture out as **box UV** (no per-face UV, so it edits like any Blockbench
  model): every cube its own unfolded net, the right wing wearing the left's nets through `mirror: true`, cubes
  with identical nets sharing one (toes, fingers, horns), the nets packed by body part (body, neck, head, legs,
  tail, wing: one block each, `GAP` apart) into the smallest power-of-two texture (512x512), skin, glowmask and
  heat frames together. It asserts every face samples exactly the texels it did before, except the tail's
  fractional-length segments (`tail_1..5`: GeckoLib floors box-UV sizes), which are resampled nearest.
- `anims.py` -- every animation as a pose function. Ground poses are solved by IK (`walk.py`, `stand.py`,
  `ik.py` on the FK in `rig.py`), so planted feet do not slide (residuals < 0.02 px).
- `flight.py` -- the wingbeat (see Flight below). `anims.py` builds every flying pose from it through `flight_pose`.
- `parts.py` -- hitbox anchors + chain pivots for every keyframe, the tail's rest geometry and the flight tails
  (`anims.tail_track`) -> generated `body/PoseData.java` (long strings are `String.join` chunks: javac's 64 KB cap).
- Previewing poses offline: render the rig's cubes through `rig.matrices` (Pillow) -- the asset build takes ~7 min,
  almost all of it the standing IK, so check flight poses that way before rebuilding.
- Every animation keys every neck/tail segment, `head_group` and both shoulders: the renderer adds procedural turns on top.
  The tail is keyed straight (zero) everywhere: its whole motion is procedural (see Tail below).
- Wing rule (no gaps, no crossing): shoulders rotate freely, elbows only about Z, the hand only through `fan.py`
  (4 gaps; the sail gap pleats at `fan.SAIL` x its neighbour's, `tip7` never moves), plus the wrist twist
  (`fan.wrist_twist`, keyed into `tip2`): the hand turned about the `tip7` crease, the one line it shares with the
  sail, so that seam folds and never opens.
- Standing (`stand.py`): chest up, the arm arched (elbow above the shoulder, forearm back down to the wrist), the
  hand half spread (`stand.FAN`) and laid on the ground along its leading finger (`tip2`) out to the tip, the rest
  of the hand fanned up from it to the forearm (`walk.solve_limbs(hand_laid=True)`: shoulder, elbow and wrist twist
  put the claw on its mark, the wrist on the ground, tip2's tip level with it, the other fingers above). The hand
  cannot lie flat with the elbow up: its plane holds the crease, only 20 deg off the forearm.
- Editor convention: +X pitches a bone's front up; files store X and Y negated.
- `particles.py` -- void flame sprites, drawn (no source image), pixel art from smooth noise at 4x: a puff of
  billows that burns as a ball of purple fire (white heart, violet rim), a ring of smoke closes in on it (glowing
  violet beside the fire), then it is only smoke clouds (shaded per billow, darker underneath, as vanilla's big
  smoke) that thin out. The puff grows inside the sprite (a small ball in the middle to the whole sprite): the
  particle's quad keeps one size, so texels never get bigger. `void_breath_*` (48x48, 16 frames, the stream; pure
  smoke from frame 9) and `void_flame_*` (24x24, 12 frames, the breath clouds' flames and the mouth's embers; pure
  smoke from frame 7). `VoidFlameParticle`'s `smokeFrom` (where the world's light takes over and the puff rises)
  matches those frames.
- `heat.py` -- the breath's heat-glow frames (see Breath attack).
- `dragon_fire.py` -- the dragon fire block's textures: vanilla's soul fire (read from Loom's client jar) tinted
  violet (red and green swapped, dimmed); vanilla's animation `.mcmeta` copied as is.
- `crystal_beam.py` -- the End crystal beam's texture (overrides `minecraft:entity/end_crystal/end_crystal_beam`,
  the dragon's healing beam and the crystals' own beams): 32x512 (vanilla x2), dim 1-px sparkle and a double helix
  of Standard Galactic Alphabet runes (the font's `particle/sga_*` glyphs, drawn 2x tall: a texel is ~0.145 blocks
  round the tube and 0.0625 along it). `ArenaTour` photographs it (`df-arena-beam*.png`). The texture flows from the
  crystal toward the dragon (vanilla's runs the other way): `mc/arena/mixin/client/EnderDragonRendererMixin` redraws
  vanilla's tube with the scroll's sign flipped.
- `egg.py` -- the dragon egg (overrides `minecraft:block/dragon_egg`): the sniffer egg's model (one box, a texture per
  face) at 14 x 16 x 14, scales in the dragon texture's palette (no glow), rows of them from tip to tip, small at the
  top and growing toward the bottom, spiralling into both tips; each texel shows its nearest scale seed in 3D, so they
  run on across the faces' edges. Three hatch stages (`not_cracked`, `slightly_cracked`, `very_cracked`: dark
  cracks, then open ones with lit edges) on a `hatch` 0..2 property (`mc/mixin/DragonEggBlockMixin`, vanilla's
  `HATCH`) and blockstate. Nothing raises it yet (hatching comes later). The item is a flat sprite like the
  sniffer egg's (`textures/item/dragon_egg`; `items/dragon_egg.json` for 1.21.4+). `egg.py <png>` previews it all.
- `icon.py` -- the mod icon (`assets/dragonsworn/icon.png`, 128x128 pixel art, drawn: the dragon in flight against a
  great light, its silhouette traced from `tools/source/icon_dragon.png`, backlit wings, twisted spires with crystals beaming to it, a player's black silhouette
  braced with the sword raised). `icon.py <png>` previews it x4.

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

## Hitboxes follow the drawn model
`body/PartSolver` places every part where the model draws it (both sides, `DragonBrain.placeParts`): the pose the
model *shows* (`AnimClock.shownSeconds`: GeckoLib plays an animation `BLEND_TICKS` late, after blending into it from
whatever pose showed when the choice changed: `PartSolver` blends from the frame it last solved, as GeckoLib from its
bone snapshot), then the same bends the renderer adds. The neck is exact FK (`body/NeckChain.bend`): each bone's
keyed pitch/yaw are recovered from the frame's pivots (no neck bone keys roll or position) and the bends go into
Ry/Rx as GeckoLib adds them to the bone's rotation; the strike's IK and the breath's straightening
(`NeckChain.aim`) use it too. The head-look (`limb/HeadLook`) ticks on both sides in `DragonBrain.look`
(measured from the head posed by the last solve), and the renderer reads it with the partial tick. Showcase stage
`hitboxes` (`-Pdragonsworn.showcase=hitboxes`) compares the drawn head/neck anchors with the hitboxes as the head turns.

## Flight
`tools/flight.py` is the wingbeat, after big birds (research notes: downstroke 55 % of the beat, joints breaking
root to tip, body heave/pitch answering the push, head stabilized ~70 %): one phase angle theta, smoothly warped
(C-infinity, so no key snaps), drives every joint as harmonics: shoulder, then the hand (elbow hinge) ~0.08 beat
later, the fan's pleats and fingers rippling after; sweep forward on the downstroke (ellipse + figure-eight),
twist leading edge down on the downstroke and up on the upstroke. The body's heave, pitch and surge are the
periodic response of a damped mass to `flight/Wingbeat.downstroke` (the very curve the server's thrust and lift use),
so it is lowest early in the downstroke and peaks at the start of the upstroke. `flight_pose` solves the neck each
frame so the head holds still, legs trail by inertia. Beat phase 0 = wings level and rising; top at
`DOWNSTROKE_START` 0.225, bottom at `DOWNSTROKE_END` 0.775 (`flight/Wingbeat`). A push (`flap`) is one whole
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
`attack/BreathAttack` (core, tested) + `mc/breath/` (phase, particles, mixins in `dragonsworn.breath.mixins.json`).
After the perched roar the dragon sometimes pours a void-flame stream instead of vanilla's cloud; the client spawns
flames from the model's mouth (`BreathRender`), the server pours a puff each damage interval (`attack/FlamePuff`, core,
tested; `mc/breath/BreathFlames`, ticked by the brain) that flies as the particles do (same jet speed, drag, rise, burning
`FIRE_TICKS` until they turn to smoke): it hurts each thing it passes through once, splashes where it meets a block, and
flies on after the breath ends, so damage and dragon fire land only when and where the flames reach (every stream breath:
perched, pass, hover). Over the 2 s inhale
the dragon heats up (`mc/client/HeatGlowLayer`): its chest glows first, the glow climbs the throat to the jaw and
mouth, then the fire comes; it flickers while pouring and cools after. A texture animation: `tools/heat.py` bakes
8 emissive frames (`textures/entity/heat/`) from the model's UV map (chest, neck underside, jaw get ignition times
by model z); the layer draws the two frames around `BreathAttack.heat`, weighted. It bakes on `build_wings.py`'s
layout, before `pack_uv.py` repacks it with the skin. Every fireball (roam pass/barrage, the arena's strafe via
`DragonStrafePlayerPhaseMixin`) goes through `mc/Fireballs`: first the head turns to the target (look attention full,
`Fireballs.aiming`, both sides); only once it points within `FIREBALL_CONE` of it does the same glow play
`FIREBALL_SPEEDUP` (3) x faster (a head that does not come round within `FIREBALL_AIM_TICKS` drops the shot before any
glow), and the fireball always flies when the glow reaches the jaw (at the target, or down the head's line if it slipped
out of the cone: never backwards; synced as count x 3 + stage, `DragonData.FIREBALL`), from in front of the mouth; projectiles never hit their owner's own parts (`ProjectileMixin`).
**Dragon fire** (`mc/breath/DragonFire`, block `dragonsworn:dragon_fire`): every fire attack leaves it where it lands
(`DragonFire.spread`): the stream and the breath pass where their puffs splash (`BreathFlames`), the fireball a few
flames right where it bursts, the perched cloud breath under its cloud. Soul fire tinted violet, `DAMAGE` 3 a touch
(fire: 1); it does not spread or burn blocks, stands on any solid top and burns out after 5-10 s; the dragon is
immune. Created inside the loaders' block registration; cutout via `BlockRenderLayerMap` (Fabric) or the models'
`render_type` (NeoForge). Showcase `breath` checks the fire and its damage against vanilla fire's; `pass` the pass's. The
dragon-fireball and perched-breath clouds use the `dragonsworn:void_flame` particle. Mouth constants in
`BreathAttack` come from the `breath` pose in `anims.py`: re-derive them if that pose changes. The neck lunges out
`BREATH_LUNGE` before the fire, because the model plays the animation `BLEND_TICKS` late. Pouring, the neck
runs out straight from the chest with the head low at the chest's height; the pose keys no neck sway (it would throw
the game's aim off). The neck follows a moving target alone; the body turns only once the target leaves the neck's
`NECK_ARC` (`BreathAttack.bodyTurns`). Showcase `-Pdragonsworn.showcase=breath` runs the breath stages only,
including a husk walking across the stream (head line vs husk, body must not turn).
**Breath pass** (`attack/BreathPass` core, tested; `mc/phase/BreathPassPhase`, wild attacks and the arena's holding
pattern): run-up, back in `BreathPass.HEIGHT` over the prey, and once lined up `START_DISTANCE` short of it the
action `GLIDE_BREATH` (`anims.glide_breath`: the glide, a short inhale, then the straight neck swung ~50 deg down,
jaw open) on a forced glide; the aim is a direction from the neck's base inside a cone ahead and below
(`PITCH_MIN..MAX`, `YAW_ARC`) that swings after the prey at `STREAM_TURN`, so the flames rake the ground along the
flight path through it; they touch down `BreathPass.lead` short of the prey and walk up to it, reaching it `CATCH` short. Synced as `DragonData.STRIKE` (the ground point), the neck straightened onto it by
`body/Strike` as the perched breath's; the client times flames, heat and sounds off the animation clock
(`BreathPassPhase.breathTicks`), the flames carrying the dragon's speed. Showcase `-Pdragonsworn.showcase=pass`.

## Ground combat
`ai/GroundTactics` (core, tested) picks one blow at a time (bite and tail share one recovery): in front the bite,
to the side the tail (nearer the head: a dice roll between the two), behind it turns round, unless the
target just hurt it from there (then the tail). `body/Strike` (core, tested) aims the blow by IK on top of the
keyframes: the neck + head (or the 9 tail segments) are bent so that on the blow's frame the jaws (or the tail's
tip) are exactly on the aim; the same bends go into the hitboxes (`PartSolver`) and the renderer (`DragonModel`),
the aim is synced in `DragonData.STRIKE`. The aim follows the target until `REACTION_TICKS` before the blow, then
only what is at the jaws/tip when it lands is hit (a dodge is a miss; the jaws hit anything within `Strike.BITE_HIT_RADIUS`, config `bite_radius`, 2 blocks: the fly-by `FlybyBite.RADIUS` 2.5, wider than the IK's reach tolerance `BITE_RADIUS`). Through a tail strike the head keeps
watching the target (`LimbAnimator.look`, gaze from between the eyes on `jaw_upper`); the tail drops its
balancing counter-swing then, so the strike's aim stays exact. Whether a blow reaches at all is the IK's
answer, so the dragon has real blind spots; `ai/HitTally` makes it take off when hit too often at once. The
stream breath uses the same aim: the neck is straightened toward a point that chases the target at
`BreathAttack.AIM_SPEED`, and the flames go from the model's mouth to it.

## Wild dragons are lazy
Outside the End fight (`DragonBrain.Context.WILD`) a dragon lives on foot: `ai/Roaming` gives long ground spells
(walks and rests) and only short flights (a few short, low legs) to come down somewhere else. A target is fought on
the ground: in the air with a target and `CombatStance.grounded()`, `WildDirector` lands beside it (`tryGroundAssault`,
retried every 40 ticks; air attacks meanwhile), and a wild `GroundFightPhase` with a target never takes off on its
timer. `ai/CombatStance` (core, tested): losing `GROUND_LIMIT` of its health on the ground (or `HitTally`) starts a
break in the air (`BREAK_MIN..MAX` ticks of air attacks: pass, barrage, charge, snatch); the break's end or
`AIR_LIMIT` more damage up there sends it back down to fight on foot; `CALM_TICKS` without a target resets it.
The arena's dragon keeps vanilla's fight, except that it never
lands on the exit portal: where vanilla's would (`LANDING_APPROACH`, remapped in `DragonBrain.remap`), it perches beside the
player nearest it on the island (`ArenaDirector.perchByPlayer`: `GroundApproachPhase.perch`, then vanilla's sitting phases there; its
takeoffs are all `LIFTOFF`), or flies on when nobody stands where it can land. Showcase stage `stance` checks the cycle (hits are fed to the brain as the husk's).

## Narrow footholds
`ai/Foothold`: where all four limbs do not fit (`LandingSite.fits(x, z, foothold)`), a ground assault
(`Tactics.tryGroundAssault`) comes down within a bite of its prey (`LandingSite.near`, every column, at the prey's
height) on a smaller one: `UPRIGHT` (ground under the 3x3 round its feet: sat up on its hind feet, the tail laid
behind, the wings held out half spread for balance, teetering, a righting stroke at `BALANCE_SECONDS`) or `CLING`
(only the center column, a pillar's top: feet planted, the wings beating the hover's stroke). Both need the body sat
up clear (`UPRIGHT_HEIGHT`, `BODY_RADIUS`) and air for the wings (`WING_RADIUS`), and are always hovered down onto.
The foothold is synced (`DragonData.FOOTHOLD`, `DragonBrain.foothold()`) and picks the stance animation
(`DragonAnimSelector`) and the bite (`UPRIGHT_BITE`, `CLING_BITE`: `ATTACK`'s timing, aimed by `body/Strike`). Up
there `GroundTactics.decide(foothold, ...)` only bites and turns: no walking, tail or roar; out of the jaws' reach
for `NARROW_PATIENCE` it takes off (`LiftoffPhase` springs straight into the hover, no crouch on all fours); clinging
lasts `CLING_MAX`. One-shots settle back into their stance (`DragonAnim.then`). Poses: `anims.upright_pose`
(`stand.Stand` with only the hind feet solved: body pitch and lift only, so the feet stay planted), `cling_pose`.

## Death
Brought down by a player (anywhere: `EnderDragonMixin` drops vanilla's die-on-the-spot when sitting), the dragon
takes vanilla's `DYING` phase, health held at 1 and unhurtable, but `DragonDeathPhaseMixin` flies it by
`ai/DeathFlight` (core, tested): a cry, then to the End fight's altar (`EndPodiumFeature` + heightmap) `HEIGHT` over it,
hovering in from `HOVER_RADIUS`, and straight up `RISE` more (a wild dragon: only the rise, from where it is); then
`DragonBrain.deathTick` sets its health to 0 and vanilla's 10 s of dying (light rays, floating up) plays `DEATH`:
from the hover one last stroke up (`DEATH_RAISE_SHOULDER`), then the wings close round the body into a cocoon,
the folded hands meeting edge to edge in front, the head curled onto the chest, the legs drawn up, the tail
tucked forward between them (`TailMotion.TUCK`); it breathes ever more weakly and shudders, the wrap only ever
opening from its closed pose. The wings never overlap: `anims.assert_wings_apart` fails the asset build if a
forearm or hand crosses the body's middle in any frame. Dead, `aiStep` returns early, so `tickEnd` hooks every
return (else the clock and tail freeze). Showcase stage `death`.

## Fighting from the air
Where the dragon cannot come down by its prey it fights it in the air. `ai/AirTactics` (core, tested) gives the
repertoire by `Reach`: `GROUND` (room to land: snatch, breath pass, fireball pass, charge, barrage), `WALL` (on solid
ground but no landing site nor foothold: a wall's top, a lone pillar, a player pillaring up a spire to its crystal;
`Tactics.walled` remembers a failed `tryGroundAssault` for `WALLED_TICKS`) and `AIR` (`Tactics.airborne`:
elytra, flying, or `AIRBORNE_GAP` blocks of air under it); the brain tries `choices` in order until one starts
(the last, the barrage, always does; the End fight uses vanilla's strafe for it). Wild: `WildDirector`; the End fight:
`ArenaDirector` (a player in the air, or one it cannot land by). Against WALL/AIR it attacks every 80-160 ticks.
**Fly-by bite** (`attack/FlybyBite` core, `mc/phase/FlybyBitePhase`, animation `GLIDE_BITE`): run-up, then a level
glide on the line that puts the bite's resting jaws (`Strike.rest`: the blow frame's head part before the IK) on the
prey's middle, the body `CLEARANCE` over and `PULL` behind it (the neck's sweet spot for a low bite, measured);
it settles to that height from `LEVEL_OFF` (no stoop at the end), homes in from `HOME_IN`, starts the bite
`HIT_TICKS` short and holds the speed that arrives exactly then. Prey in the air is led by its velocity. Damage and
knockback (along the flight) grow with speed. **Hover attacks** (`attack/HoverAttack`, `mc/phase/HoverAttackPhase`,
`HOVER_BITE` one beat long, `HOVER_BREATH` three beats with the breath pass's timing so `BreathPassPhase.breathTicks`,
`BreathRender` and the heat glow serve both): it picks a side round the prey where the hovering body is clear and
sees it, hovers at the bite's spot (`biteSpot`) or `BREATH_DISTANCE` off and `BREATH_RISE` over, faces the prey
(`DragonswornPhase.hoverLook`) and starts each attack on a beat boundary (`onBeat`, `DragonBrain.beatPhase`): up to
`BITES` bites, or one breath whose aim chases the prey inside a cone that reaches a little above level.

## Soft hitboxes
The parts push what stands in them like mobs push each other (`mc/PartCollision`, after `placeParts`): each tick
anything overlapping a part gets vanilla's mob push (`body/BodyPush`, `Entity.push`'s formula) away from the part's
middle, so it slides out; only it moves, the dragon stays put. Nothing is solid (one cannot stand on the dragon).
Each side pushes what it simulates: the server its mobs (NoAI mobs never move, as in vanilla), a client its own
player. The prey a dragon carries is left in its grip. Showcase stage `collision`.

## Collision and paths
Vanilla's dragon has no physics; `DragonBrain` gives it some. The hull (head, necks, chest, hips, tail root parts)
never moves deeper into solid, unbreakable blocks (`HullCollision.move`: in flight; `walk`: on its feet, sliding along walls), and
`pushOut` shoves it back out a little per tick when a turn or the pose swung it into one (only a hull wedged for
`ESCAPE_TICKS` may pass through to free itself). Flight (`AirRoute`): straight when clear, else `nav/AirPlanner`'s A*
route, kept while its next leg is clear, corners cut when a later waypoint is in view; whatever its momentum carries
it into within `LOOKAHEAD_TICKS` makes it `swerve` at once and replan; a route leg steeply up (over a wall) is flown
hovering. Walking: `nav/GroundPlanner` clears the whole body (`BODY_RADIUS`, low steps under the belly), keeps off
walls, and a bent path (a detour) is walked even where it leads away from the target; `GroundFightPhase.followGround` never climbs a
wall the wrists are against. Showcase stage `walls` (`-Pdragonsworn.showcase=walls`) checks all of it.

## Turning on the spot
Standing (idle, bite, roar, breath), the feet never slide as the body turns: `limb/TurnSteps` (core, tested) keeps
each foot planted in the world and steps it round in diagonal pairs when it falls behind, a little inward and ahead,
or at once when its limb is at full stretch (the IK's miss > `LimbAnimator.STRAIN`). IK reaches them in 3D:
`LimbIK.solveLegReach` (hip splay + leg plane) and `solveArmReach` (shoulder 3 axes + elbow Z, bent seeds: the
standing arm is straight, a singular pose). The front limb is held by its folded hand's apex (`*_wing_tip3`, as
`walk.py` plants it), and only while the hand is down (its gap to the ground, not one point's height).

## Grabs (talons and jaws)
`body/Grip` (core, tested): the holds, their synced encoding, what fits (`Grip.fits`: width <= 1, width^2 x height
<= 1.5 on the kind's full standing size, so humanoids, endermen, cows and sheep, never a warden, digging or not), the jaw shake (`DragonBody` adds it to the neck
bends, so hitboxes and model agree). `mc/PreyHold`: the prey is *carried*, not mounted (no dismount key, no vehicle
health bar): the level skips its tick (`ServerLevelMixin`/`client/ClientLevelMixin`), the dragon ticks it right after
its own and puts its middle in the grip (both sides; the server ignores a held player's moves and does not kick it for
flying, `ServerGamePacketListenerImplMixin`); the entity knows its carrier (`Carried`, `EntityMixin`). It cannot move
but can use items; moved out of the hold (an ender pearl), dying or released, it is free. `client/LivingEntityRendererMixin`
draws held prey lying flat about its middle (along the dragon in the talons, across the jaws); in first person the
camera sits at its lying head (`client/CameraMixin`, `PreyHold.lyingEyes`). The snatch (`phase/SnatchPhase`, wild
attacks and the arena's holding pattern): run-up, a dive down a glide slope that homes in with both hind legs thrown
forward under the chest, toes spread (`Grip.REACH_ANKLE`, an eagle's), the right foot reaching out for the prey over
the last `Grip.REACH_NEAR` blocks (`TalonPose`, IK), catch, a hard climb, drop from 26-40 blocks. The gripping foot is held level and turned across the prey
(`Grip.TALON_YAW`), the prey's back against its sole (`Grip.talonPad`, `PreyHold.talonPoint`); its toes close until
each meets the prey and then stay frozen until it lets go. Elsewhere the toes (`limb/Toes`) are straight on the
ground (the user wants the dragon standing on its feet, not on its claws), hang half curled and stir in the air, and
open wide for a landing or a swoop, on a slightly springy lag. Toe bones are set from their rest pose every frame
(GeckoLib does not reset unkeyed bones every frame: adding to them spins them). The seize (`GroundFightPhase`): a bite that
may keep its prey (only one that lands: dodged, nothing is held), shaken and chewed, then flung; anyone else hitting the head or neck makes it let go
(`CombatMemory.hurtBy`). Showcase stage `grabs` (`-Pdragonsworn.showcase=grabs` runs only it).

## Sound
`python3 tools/sounds.py` (needs numpy + soundfile) cuts vanilla's dragon sounds (read from Loom's asset cache)
into single events: `roar` (the growl's roar, cut to the roar animation's open jaw), `wing` (one swing,
slightly deeper) and `step` (hard-ground step: stone + ravager step + a low thump), plus `sounds.json`
(each with its `attenuation_distance`: they play at volume <= 1 so a roar can fade). `anim/DragonVoice`
(core, tested) times them on the animation clock: a swing per downstroke, a step per foot plant, the roar
as the jaw opens; in flight the server picks occasional roars (`DragonData.VOICE`) and the jaw opens with
them (`DragonModel`); an attack fades a roar out (`RoarSound`). Vanilla's flap timer, growl and ambient
sound are silenced (`DragonVoiceMixin`).
Every sound the dragon makes has a subtitle that says what it does (for deaf and hard-of-hearing players):
the attacks keep vanilla's sounds but play them through events of their own (`DragonSounds`: `bite`, `tail`,
`snatch`, `chew`, `fling`, `breath`, `flames`; sounds.json points each at vanilla's event with `"type": "event"`,
written by `sounds.py`'s `ALIASES`), never vanilla's event directly ("Ravager bites", "Blaze shoots" would lie).
`LangFilesTest` fails on a sound event without a subtitle.

## Translations
Every player-facing text is a lang key (`src/mc/shared/resources/assets/dragonsworn/lang/*.json`). The core
words its texts as `Text` (key + English fallback + `%s` args; `DragonConfig`'s labels, tooltips and ranges,
`DragonCommand`'s feedback); the bridge shows them through `mc/Lang.of` (`translatableWithFallback`).
`en_us.json` must hold `DragonConfig.translations()` word for word: change an option's comment and update
`en_us.json` (`LangFilesTest` lists what differs); other languages may lag (English shows for missing keys) but
may only use en_us keys with the same `%s` count. The config file (TOML) stays English.

## Arena (End spires)
`arena/Monolith` (core, tested) replaces vanilla's obsidian cylinders with spiral towers (references: Mode Gakuen
Spiral Towers, BIG's The Spiral, twisting towers), all with vanilla's flat top and obsidian only (the user rejected
crystal-like shapes, pointed tops and crying obsidian). Every tower winds its own way (seeded direction, turns,
proportions, wing count), in one of two styles: `CROWN` = twisted rounded rectangle, two sharp corners winding up
it; `WINDOW` = core wrapped in scroll wings split by spiral slits crossed by shelves. No spike is caged, not even
vanilla's two guarded ones (the user removed the caged style). The
crystal stays exactly where vanilla puts it (bedrock at `height`, crystal at `height + 1`), the shape is a pure
function of the spike (respawn rebuilds it identically; the ground is probed outside the tower's footprint for that
reason), nothing goes further than `REACH` (a feature's write radius), and every block is blast-proof.
`mc/arena/mixin/SpikeFeatureMixin` (`dragonsworn.arena.mixins.json`) swaps `placeSpike` for `mc/arena/Monoliths.place`,
so worldgen and the respawn ritual both use it. Both it and `EndPlatformFeatureMixin` step aside (vanilla's or
the other mod's build) when the server config turns them off (`end_island.spires`, `end_island.entrance_platform`)
or when any other mod's mixin is applied to the same class (`mc/arena/OtherMods`: YUNG's Better End Island
injects at HEAD of both methods, as we do). An End crystal on bedrock (the spires', the exit portal's) burns
`DragonFire` under itself instead of common fire (`EndCrystalMixin` -> `DragonFire.crystalFire`); dragon fire on
bedrock never burns out. `mc/client/ArenaTour` is its in-game test.
The entrance platform (where the portal drops you) is a sphere, `arena/EntrancePlatform` (core, tested): radius 5
round vanilla's arrival spot, its bottom quarter obsidian (the floor's top stays vanilla's), the rest air;
`EndPlatformFeatureMixin` swaps vanilla's `createEndPlatform` for it (worldgen and every portal arrival).

## Server config
`config/DragonConfig` (core, tested) is every AI knob as a constant (`SEIZE_CHANCE.get()`, read where used, so a
reload acts at once): targeting, wandering, stance, ground combat (which blows, odds, cooldowns, damage), air
attacks (on/off each, and per-`Reach` first-choice weights: `AirTactics.choices` drops a disabled attack, even as a
fallback), attack details, the arena's choices, `end_island`. Its defaults are the AI classes' own constants (tests
run on them). `config/Toml` reads/writes the file: `config/dragonsworn-server.toml`, loaded by
`DragonswornCommon.loadConfig` at init, server start and `/reload`; clamped, problems logged, missing keys written
back. Config screens (`mc/client/config/ConfigScreens`): YACL if installed, else Cloth Config, else the vanilla-widget
`PlainConfigScreen`, all generated from the option list; opened by Mod Menu (`DragonswornModMenu`, `src/fabric/mc1.21.1`)
and NeoForge's mods list (`IConfigScreenFactory`). The libraries are compile-only (`deps.modmenu/yacl/cloth-config`).
Showcase stage `config` photographs the screen (`df-config-*.png`); `-Pdragonsworn.configLibs` puts YACL and Cloth
in the dev client (Fabric), `-Pdragonsworn.configScreen=plain|cloth|yacl` picks one.

## Licensing
Model and texture derive from the "Ender Dragon Reborn" pack by Parrie43 (All Rights Reserved). Personal use
until permission is granted.
The dragon sounds (`sounds/entity/ender_dragon/*.ogg`) are cut from Minecraft's own (Mojang); the dragon fire
textures are Minecraft's soul fire, recolored.
