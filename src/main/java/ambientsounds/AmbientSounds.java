package ambientsounds;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = AmbientSounds.MODID,
    version = Tags.VERSION,
    name = "AmbientSounds",
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public class AmbientSounds {

    public static final String MODID = "ambientsounds";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "ambientsounds.ClientProxy", serverSide = "ambientsounds.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
