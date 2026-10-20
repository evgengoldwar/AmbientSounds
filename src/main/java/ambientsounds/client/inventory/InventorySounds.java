package ambientsounds.client.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import ambientsounds.Config;
import ambientsounds.client.sound.Sounds;

/** Called from the GuiContainer mixins whenever the player clicks a slot. */
public final class InventorySounds {

    /** Minimum time between two inventory sounds (drag-splitting a stack touches many slots at once). */
    private static final long MIN_INTERVAL_MS = 45;
    private static final int CLICK_DRAG = 5;

    private static long lastPlayed;

    private InventorySounds() {}

    public static void onSlotClick(Slot slot, int slotId, int button, int clickType) {
        if (!Config.inventoryEnabled) return;
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;
        // while drag-splitting only the final release makes a sound
        if (clickType == CLICK_DRAG && (button & 3) != 2) return;

        ItemStack cursor = player.inventory.getItemStack();
        ItemStack inSlot = slot != null ? slot.getStack() : null;
        ItemStack involved;
        boolean pickUp;
        if (cursor != null) {
            involved = cursor;
            pickUp = false;
        } else if (inSlot != null) {
            involved = inSlot;
            pickUp = true;
        } else {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastPlayed < MIN_INTERVAL_MS) return;
        lastPlayed = now;

        ItemSoundType type = ItemSoundType.of(involved);
        float volume = 0.5f * Config.inventoryVolume;
        // picking up sounds a little brighter than putting down
        float pitch = pickUp ? Sounds.vary(1.1f, 0.05f) : Sounds.vary(0.94f, 0.05f);
        Sounds.playGlobal(type.sound, Sounds.vary(volume, volume * 0.1f), pitch);
    }
}
