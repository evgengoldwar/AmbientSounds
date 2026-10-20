package ambientsounds;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        AmbientSounds.LOG.info("AmbientSounds " + Tags.VERSION + " loaded");
    }

    public void init(FMLInitializationEvent event) {}
}
