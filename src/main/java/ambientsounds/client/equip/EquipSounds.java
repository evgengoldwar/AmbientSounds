package ambientsounds.client.equip;

import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.ResourceLocation;

import ambientsounds.Config;
import ambientsounds.client.sound.Sounds;

/** Plays a draw / equip sound when the player switches to a weapon, tool or other item. */
public class EquipSounds {

    private enum Kind {

        SWORD("sword", 0.45f),
        HEAVY("heavy", 0.45f),
        TOOL("tool", 0.4f),
        BOW("bow", 0.4f),
        GENERIC("generic", 0.3f);

        final ResourceLocation sound;
        final float volume;

        Kind(String name, float volume) {
            this.sound = Sounds.of("equip." + name);
            this.volume = volume;
        }
    }

    /** Wait this many ticks after the last change so scrolling through the hotbar does not spam sounds. */
    private static final int SETTLE_TICKS = 3;

    private boolean initialized;
    private int lastSlot;
    private Item lastItem;
    private int pending;

    public void reset() {
        initialized = false;
        pending = 0;
    }

    public void tick(Minecraft mc, EntityPlayer player) {
        int slot = player.inventory.currentItem;
        ItemStack held = player.getCurrentEquippedItem();
        Item item = held == null ? null : held.getItem();
        if (!initialized) {
            initialized = true;
            lastSlot = slot;
            lastItem = item;
            return;
        }

        boolean slotChanged = slot != lastSlot;
        // a different item appearing in the same slot while no GUI is open (e.g. picked from the creative menu)
        boolean itemChanged = item != lastItem && item != null && mc.currentScreen == null;
        lastSlot = slot;
        lastItem = item;
        if (slotChanged || itemChanged) {
            pending = SETTLE_TICKS;
        }

        if (pending > 0 && --pending == 0 && held != null && Config.equipEnabled) {
            Kind kind = classify(held);
            float volume = kind.volume * Config.equipVolume;
            Sounds.playGlobal(kind.sound, Sounds.vary(volume, volume * 0.1f), Sounds.vary(1.0f, 0.05f));
        }
    }

    private static Kind classify(ItemStack stack) {
        Item item = stack.getItem();
        EnumAction action = item.getItemUseAction(stack);
        if (item instanceof ItemSword || action == EnumAction.block) {
            return isWooden(stack) ? Kind.TOOL : Kind.SWORD;
        }
        if (item instanceof ItemBow || action == EnumAction.bow) return Kind.BOW;

        Set<String> classes = item.getToolClasses(stack);
        if (item instanceof ItemAxe || classes.contains("axe") || classes.contains("hammer")) return Kind.HEAVY;
        if (item instanceof ItemTool || item instanceof ItemHoe || item instanceof ItemShears || !classes.isEmpty()) {
            return Kind.TOOL;
        }
        return Kind.GENERIC;
    }

    private static boolean isWooden(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof ItemSword && "WOOD".equals(((ItemSword) item).getToolMaterialName());
    }
}
