package ambientsounds;

import net.minecraftforge.common.MinecraftForge;

import ambientsounds.client.ClientEvents;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ClientEvents events = new ClientEvents();
        // tick events live on the FML bus, sound events on the Forge bus
        FMLCommonHandler.instance()
            .bus()
            .register(events);
        MinecraftForge.EVENT_BUS.register(events);
    }
}
