package ambientsounds.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.sound.PlaySoundEvent17;

import ambientsounds.Config;
import ambientsounds.client.ambient.AmbientEngine;
import ambientsounds.client.equip.EquipSounds;
import ambientsounds.client.movement.FootstepEngine;
import ambientsounds.client.sound.OneShotSound;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Drives every sound engine of the mod from the client tick. */
public class ClientEvents {

    private final AmbientEngine ambient = new AmbientEngine();
    private final FootstepEngine footsteps = new FootstepEngine();
    private final EquipSounds equip = new EquipSounds();
    private World lastWorld;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        EntityPlayer player = mc.thePlayer;

        if (world != lastWorld || player == null) {
            // left the game or changed dimension: start from a clean state
            ambient.stop(mc.getSoundHandler());
            footsteps.reset();
            equip.reset();
            lastWorld = world;
        }
        if (world == null || player == null || mc.isGamePaused()) return;

        ambient.tick(mc, world, player);
        footsteps.tick(world, player);
        equip.tick(mc, player);
    }

    /** Mutes vanilla step sounds of the local player, the mod plays its own material aware ones instead. */
    @SubscribeEvent
    public void onPlaySound(PlaySoundEvent17 event) {
        if (!Config.footstepsEnabled || !Config.customFootsteps || event.sound == null) return;
        // block sounds the mod itself plays for jumps and landings
        if (event.sound instanceof OneShotSound) return;
        ResourceLocation location = event.sound.getPositionedSoundLocation();
        if (!"minecraft".equals(location.getResourceDomain()) || !location.getResourcePath()
            .startsWith("step.")) {
            return;
        }
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null || player.ridingEntity != null) return;
        ISound sound = event.sound;
        double dx = sound.getXPosF() - player.posX;
        double dy = sound.getYPosF() - player.boundingBox.minY;
        double dz = sound.getZPosF() - player.posZ;
        if (dx * dx + dy * dy + dz * dz < 1.0) {
            event.result = null;
        }
    }
}
