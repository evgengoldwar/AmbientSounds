# AmbientSounds

Client-side Minecraft 1.7.10 (Forge) mod that adds calm, immersive sound to the game.

## Features

**Ambience** (vanilla *Ambient/Environment* slider)
- Seamless, crossfading ambience layers picked from the biomes around you (sampled on a grid, so biome borders blend):
  plains breeze, forest leaves, night crickets, jungle insects, swamp frogs, ocean surf, desert wind, cold snow wind,
  high mountain wind, cave drone, Nether rumble, End drone and an underwater layer.
- Time of day, rain, altitude and being indoors are taken into account (indoors the outside is only faintly heard).
- Random events: several kinds of bird songs, doves, woodpeckers, owls at night, wind and leaf gusts, cave water
  drips and distant rumbles, lava bubbles in the Nether, soft shimmers in the End.

**Movement** (vanilla *Players* slider)
- Material-aware footsteps: grass, dirt, gravel, stone, wood, sand, snow, metal, glass, cloth, leaves, shallow water.
  Works with modded blocks through their step sound type and material.
- Brushing through tall grass/plants, armor rustle (leather creak, chainmail jingle, plate clank).
- Jump and landing sounds (landing gets heavier with the fall height).
- Vanilla footsteps of your own player are muted by default so steps are not doubled (`replaceVanilla`).

**Item sounds** (vanilla *Players* slider)
- Weapon/tool swap: sword draw, axe/hammer, tools, bow, and a soft sound for other items.
- Inventory: picking up / putting down items makes a sound matching the item (metal, gem, wood, stone, cloth,
  paper, glass, food, dust, liquid...). Modded items are classified through the Ore Dictionary.

Everything can be toggled and rebalanced in `config/ambientsounds.cfg`.

## Sounds

All sounds are original and procedurally synthesized by [`tools/soundgen`](tools/soundgen), so there are no
licensing concerns. To tweak a recipe and regenerate the `.ogg` files and `sounds.json`:

```sh
pip install numpy scipy      # ffmpeg with libvorbis must be on PATH
python3 tools/soundgen/generate.py
```

## Building

```sh
./gradlew build
```
