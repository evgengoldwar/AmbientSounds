package ambientsounds.client.inventory;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBook;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemBucketMilk;
import net.minecraft.item.ItemEditableBook;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemExpBottle;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemGlassBottle;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSoup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.item.ItemWritableBook;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.oredict.OreDictionary;

import ambientsounds.client.sound.Sounds;

/** The kind of sound an item makes when picked up or put down in an inventory. */
public enum ItemSoundType {

    METAL,
    GEM,
    WOOD,
    STONE,
    CLOTH,
    PAPER,
    GLASS,
    FOOD,
    DUST,
    LIQUID,
    GENERIC;

    public final ResourceLocation sound = Sounds.of("inventory." + name().toLowerCase());

    private static final Map<Item, Map<Integer, ItemSoundType>> CACHE = new IdentityHashMap<>();

    public static ItemSoundType of(ItemStack stack) {
        Item item = stack.getItem();
        if (item == null) return GENERIC;
        Map<Integer, ItemSoundType> byMeta = CACHE.get(item);
        if (byMeta == null) {
            byMeta = new HashMap<>();
            CACHE.put(item, byMeta);
        }
        int meta = item.getHasSubtypes() ? stack.getItemDamage() : 0;
        ItemSoundType type = byMeta.get(meta);
        if (type == null) {
            try {
                type = classify(stack, item);
            } catch (RuntimeException e) {
                type = GENERIC;
            }
            byMeta.put(meta, type);
        }
        return type;
    }

    private static ItemSoundType classify(ItemStack stack, Item item) {
        if (item instanceof ItemPotion || item instanceof ItemGlassBottle || item instanceof ItemExpBottle) {
            return GLASS;
        }
        if (item instanceof ItemBucketMilk || FluidContainerRegistry.isFilledContainer(stack)) return LIQUID;
        if (item instanceof ItemBucket) return METAL;
        if (item instanceof ItemFood || item instanceof ItemSoup) return FOOD;
        if (item instanceof ItemBook || item instanceof ItemWritableBook || item instanceof ItemEditableBook
            || item instanceof ItemEnchantedBook
            || item instanceof ItemMap
            || item == Items.paper
            || item == Items.map) {
            return PAPER;
        }
        if (item instanceof ItemArmor) {
            return ((ItemArmor) item).getArmorMaterial() == ItemArmor.ArmorMaterial.CLOTH ? CLOTH : METAL;
        }
        if (item instanceof ItemSword) return toolMaterial(((ItemSword) item).getToolMaterialName());
        if (item instanceof ItemTool) return toolMaterial(((ItemTool) item).getToolMaterialName());
        if (item instanceof ItemHoe) return toolMaterial(((ItemHoe) item).getToolMaterialName());

        ItemSoundType vanilla = vanillaItem(item);
        if (vanilla != null) return vanilla;

        ItemSoundType ore = fromOreDictionary(stack);
        if (ore != null) return ore;

        if (item instanceof ItemBlock) {
            Block block = Block.getBlockFromItem(item);
            if (block != null) return fromBlock(block);
        }
        return fromName(item.getUnlocalizedName(stack));
    }

    private static ItemSoundType toolMaterial(String name) {
        if ("WOOD".equals(name)) return WOOD;
        if ("STONE".equals(name)) return STONE;
        if ("EMERALD".equals(name)) return GEM;
        return METAL;
    }

    private static ItemSoundType vanillaItem(Item item) {
        if (item == Items.diamond || item == Items.emerald || item == Items.quartz || item == Items.nether_star
            || item == Items.ender_pearl
            || item == Items.ender_eye
            || item == Items.ghast_tear) {
            return GEM;
        }
        if (item == Items.redstone || item == Items.glowstone_dust || item == Items.gunpowder || item == Items.sugar
            || item == Items.blaze_powder) {
            return DUST;
        }
        if (item == Items.coal || item == Items.flint || item == Items.brick || item == Items.netherbrick) return STONE;
        if (item == Items.iron_ingot || item == Items.gold_ingot || item == Items.gold_nugget) return METAL;
        if (item == Items.stick || item == Items.bowl || item == Items.bone || item == Items.blaze_rod) return WOOD;
        if (item == Items.string || item == Items.feather || item == Items.leather || item == Items.reeds) return CLOTH;
        if (item == Items.slime_ball || item == Items.clay_ball || item == Items.wheat || item == Items.wheat_seeds) {
            return FOOD;
        }
        return null;
    }

    private static ItemSoundType fromOreDictionary(ItemStack stack) {
        for (int id : OreDictionary.getOreIDs(stack)) {
            ItemSoundType type = fromOreName(OreDictionary.getOreName(id));
            if (type != null) return type;
        }
        return null;
    }

    private static ItemSoundType fromOreName(String name) {
        if (name == null) return null;
        if (startsWith(name, "ingot", "nugget", "plate", "gear", "screw", "bolt", "ring", "wire", "foil", "rotor",
            "spring", "casing", "pipe", "cell", "circuit", "component", "craftingTool")) {
            return METAL;
        }
        if (startsWith(name, "gem", "crystal", "lens", "shard")) return GEM;
        if (startsWith(name, "dust", "crushed", "pulp", "powder")) return DUST;
        if (startsWith(name, "log", "plank", "stick", "slabWood", "stairWood", "treeSapling", "fenceWood")) return WOOD;
        if (startsWith(name, "stone", "cobblestone", "ore", "brick", "rock", "sandstone", "obsidian")) return STONE;
        if (startsWith(name, "block")) return name.contains("Wood") ? WOOD : METAL;
        if (startsWith(name, "paper", "book")) return PAPER;
        if (startsWith(name, "glass", "pane")) return GLASS;
        if (startsWith(name, "wool", "cloth", "fabric", "string", "leather", "slimeball", "itemLeather")) return CLOTH;
        if (startsWith(name, "crop", "food", "seed", "list")) return FOOD;
        if (startsWith(name, "treeLeaves", "sapling", "flower", "dye")) return CLOTH;
        return null;
    }

    private static ItemSoundType fromBlock(Block block) {
        Material m = block.getMaterial();
        if (m == Material.wood || m == Material.gourd) return WOOD;
        if (m == Material.iron || m == Material.anvil || m == Material.circuits || m == Material.piston) return METAL;
        if (m == Material.glass || m == Material.ice || m == Material.packedIce || m == Material.redstoneLight) {
            return GLASS;
        }
        if (m == Material.cloth || m == Material.carpet || m == Material.web || m == Material.sponge) return CLOTH;
        if (m == Material.plants || m == Material.vine || m == Material.leaves || m == Material.cactus) return CLOTH;
        if (m == Material.sand || m == Material.ground || m == Material.grass || m == Material.clay
            || m == Material.snow
            || m == Material.craftedSnow) {
            return DUST;
        }
        if (m == Material.cake) return FOOD;
        if (m == Material.rock) return STONE;
        if (block.stepSound == Block.soundTypeMetal || block.stepSound == Block.soundTypeAnvil) return METAL;
        if (block.stepSound == Block.soundTypeWood) return WOOD;
        if (block.stepSound == Block.soundTypeGlass) return GLASS;
        return STONE;
    }

    /** Last resort for modded items without dictionary entries. */
    private static ItemSoundType fromName(String name) {
        if (name == null) return GENERIC;
        String n = name.toLowerCase();
        if (contains(n, "ingot", "plate", "gear", "metal", "iron", "steel", "copper", "tin", "bronze", "circuit",
            "wrench", "cell", "battery")) {
            return METAL;
        }
        if (contains(n, "gem", "crystal", "diamond", "emerald", "ruby", "sapphire")) return GEM;
        if (contains(n, "dust", "powder", "sand")) return DUST;
        if (contains(n, "wood", "plank", "stick", "log")) return WOOD;
        if (contains(n, "paper", "book", "scroll", "page")) return PAPER;
        if (contains(n, "glass", "bottle", "vial", "jar")) return GLASS;
        if (contains(n, "cloth", "wool", "leather", "string", "fiber", "fabric")) return CLOTH;
        if (contains(n, "food", "fruit", "meat", "bread", "seed", "berry")) return FOOD;
        if (contains(n, "bucket", "fluid", "liquid", "water", "can")) return LIQUID;
        if (contains(n, "stone", "rock", "brick", "coal", "flint")) return STONE;
        return GENERIC;
    }

    private static boolean startsWith(String s, String... prefixes) {
        for (String p : prefixes) {
            if (s.startsWith(p)) return true;
        }
        return false;
    }

    private static boolean contains(String s, String... parts) {
        for (String p : parts) {
            if (s.contains(p)) return true;
        }
        return false;
    }
}
