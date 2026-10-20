package ambientsounds.mixins.early;

import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ambientsounds.client.inventory.InventorySounds;

/** The creative inventory overrides slot clicking without always calling super. */
@Mixin(GuiContainerCreative.class)
public class MixinGuiContainerCreative {

    @Inject(method = "handleMouseClick", at = @At("HEAD"))
    private void ambientsounds$onSlotClick(Slot slot, int slotId, int button, int clickType, CallbackInfo ci) {
        InventorySounds.onSlotClick(slot, slotId, button, clickType);
    }
}
