package ambientsounds.client.movement;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import ambientsounds.client.sound.Sounds;

/** What a footstep sounds like, derived from the block's step sound type and material (works for modded blocks). */
public enum StepMaterial {

    GRASS(1.0f),
    DIRT(1.0f),
    GRAVEL(1.0f),
    STONE(1.0f),
    WOOD(1.0f),
    SAND(1.0f),
    SNOW(1.0f),
    METAL(0.9f),
    GLASS(0.9f),
    CLOTH(1.0f),
    LEAVES(1.0f),
    WADE(1.0f);

    public final ResourceLocation sound;
    public final float gain;

    StepMaterial(float gain) {
        this.sound = Sounds.of("footstep." + name().toLowerCase());
        this.gain = gain;
    }

    public static StepMaterial of(Block block) {
        if (block == Blocks.snow_layer || block == Blocks.snow) return SNOW;
        if (block == Blocks.gravel) return GRAVEL;
        if (block == Blocks.ladder) return WOOD;

        Material m = block.getMaterial();
        if (m == Material.leaves) return LEAVES;

        Block.SoundType type = block.stepSound;
        if (type == Block.soundTypeGrass) return GRASS;
        if (type == Block.soundTypeGravel) return m == Material.ground || m == Material.clay ? DIRT : GRAVEL;
        if (type == Block.soundTypeSand) return SAND;
        if (type == Block.soundTypeSnow) return SNOW;
        if (type == Block.soundTypeWood || type == Block.soundTypeLadder) return WOOD;
        if (type == Block.soundTypeCloth) return CLOTH;
        if (type == Block.soundTypeGlass) return GLASS;
        if (type == Block.soundTypeMetal || type == Block.soundTypeAnvil) return METAL;

        if (m == Material.grass || m == Material.plants || m == Material.vine) return GRASS;
        if (m == Material.ground || m == Material.clay) return DIRT;
        if (m == Material.sand) return SAND;
        if (m == Material.snow || m == Material.craftedSnow) return SNOW;
        if (m == Material.wood || m == Material.gourd || m == Material.cactus) return WOOD;
        if (m == Material.iron || m == Material.anvil) return METAL;
        if (m == Material.glass || m == Material.ice || m == Material.packedIce) return GLASS;
        if (m == Material.cloth || m == Material.carpet || m == Material.sponge || m == Material.web) return CLOTH;
        return STONE;
    }

    /** Thin blocks lying on top of the floor (snow layers, carpets) decide the sound of the step. */
    public static StepMaterial overlay(Block block) {
        if (block == Blocks.snow_layer) return SNOW;
        Material m = block.getMaterial();
        if (m == Material.carpet) return CLOTH;
        return null;
    }

    /** Plants the feet brush through, adding a rustle on top of the step. */
    public static boolean isFoliage(Block block) {
        Material m = block.getMaterial();
        return m == Material.plants || m == Material.vine || m == Material.leaves;
    }
}
