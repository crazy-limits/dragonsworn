# Asset licensing

The **source code** of Dragonsworn (Java, Gradle scripts, and the Python asset pipeline in `tools/`) is licensed
under the GNU Lesser General Public License v3.0 only: see [`LICENSE`](LICENSE) and [`COPYING`](COPYING).

The LGPL does **not** cover the assets listed below. They are not free to reuse.

## Ender Dragon Reborn model and textures: All Rights Reserved

The dragon's model and texture derive from the "Ender Dragon Reborn" resource pack by **Parrie43**
(All Rights Reserved). They are used for personal development only, until permission is granted. This covers:

- `tools/source/dragon.png`, `tools/source/dragon_eyes.png`, `tools/source/dragon_raw.geo.json`
- everything generated from them by `tools/build_assets.py`: the GeckoLib model and animations
  (`src/gecko4/resources/**`, `src/gecko5/resources/**`), the entity texture, glowmask and heat frames
  (`src/mc/shared/resources/assets/dragonsworn/textures/entity/**`), and the hitbox data
  `src/main/java/crazylimits/dragonsworn/body/PoseData.java`

## Derived from Minecraft (Mojang): Minecraft EULA

- the dragon sounds `src/mc/shared/resources/assets/dragonsworn/sounds/entity/ender_dragon/*.ogg`, cut from
  Minecraft's own by `tools/sounds.py`
- the dragon fire textures (`textures/block/dragon_fire_*`), Minecraft's soul fire recoloured by `tools/dragon_fire.py`
- the End crystal beam texture's runes, drawn from Minecraft's Standard Galactic Alphabet glyphs by
  `tools/crystal_beam.py`

The tools that make these files are LGPL. The game assets they read are Mojang's, so the tools never ship those
assets: they read them from the Minecraft jar in your Gradle cache.

## Original assets: All Rights Reserved, by the Dragonsworn authors

Everything else under `src/mc/shared/resources` is drawn by the code in `tools/`: the particles, the dragon egg, the
mod icon and the rest of the beam texture. These assets stay All Rights Reserved for now, like the model.
Contributions to them are welcome under the terms in [`CONTRIBUTING.md`](CONTRIBUTING.md).
