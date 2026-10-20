# AmbientSounds

Client-side Minecraft 1.7.10 (Forge) mod that adds calm, immersive sound to the game.

## Features

**Ambience** (vanilla *Ambient/Environment* slider)
- Seamless, crossfading ambience layers picked from the biomes around you (sampled on a grid, so biome borders blend):
  plains breeze, forest leaves, distant night crickets, jungle insects, swamp, ocean surf, desert wind, snow wind,
  mountain wind, a faint cave draft, soft Nether fire, a quiet End hush and an underwater layer.
- Time of day, rain, altitude and being indoors are taken into account (indoors the outside is only faintly heard).
- Occasional events: bird calls and a woodpecker by day, water drips in caves, lava bubbles in the Nether.

**Movement** (vanilla *Players* slider)
- Vanilla footsteps stay (they are real recordings) and get extra layers: brushing through tall grass/plants,
  wading in shallow water, armor rustle (leather creak, chainmail jingle, plate clank).
- Jump and landing sounds using the floor block's own step sound (landing gets firmer with the fall height).
- Optional (`customSteps=true`): replace vanilla steps with the mod's synthesized material steps (grass, dirt,
  gravel, stone, wood, sand, snow, metal, glass, cloth, leaves), also for modded blocks.

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
