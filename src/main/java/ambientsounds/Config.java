package ambientsounds;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    private static final String CAT_AMBIENT = "ambient";
    private static final String CAT_FOOTSTEPS = "footsteps";
    private static final String CAT_EQUIP = "equip";
    private static final String CAT_INVENTORY = "inventory";
    /** Bump when defaults change in a way old config files must not keep. */
    private static final int CONFIG_VERSION = 2;

    // ambient
    public static boolean ambientEnabled = true;
    public static float ambientVolume = 1.0f;
    public static float ambientEventFrequency = 1.0f;
    public static int maxAmbientLoops = 3;

    // footsteps, jumping and landing
    public static boolean footstepsEnabled = true;
    public static boolean customFootsteps = false;
    public static float footstepVolume = 1.0f;
    public static boolean armorSounds = true;
    public static float armorVolume = 1.0f;
    public static boolean jumpLandSounds = true;
    public static float jumpLandVolume = 1.0f;

    // weapon / tool swapping
    public static boolean equipEnabled = true;
    public static float equipVolume = 1.0f;

    // inventory clicks
    public static boolean inventoryEnabled = true;
    public static float inventoryVolume = 1.0f;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);
        if (configuration.get(Configuration.CATEGORY_GENERAL, "configVersion", 0)
            .getInt() < CONFIG_VERSION && configFile.exists()) {
            // defaults of an older version were too loud / replaced vanilla steps: start from a fresh file
            configFile.delete();
            configuration = new Configuration(configFile);
        }
        configuration.get(Configuration.CATEGORY_GENERAL, "configVersion", CONFIG_VERSION, "Do not edit.")
            .set(CONFIG_VERSION);

        configuration.setCategoryComment(CAT_AMBIENT, "Biome, weather, time of day and cave ambience.");
        ambientEnabled = configuration.getBoolean("enabled", CAT_AMBIENT, ambientEnabled, "Play ambient sounds.");
        ambientVolume = configuration.getFloat(
            "volume",
            CAT_AMBIENT,
            ambientVolume,
            0.0f,
            2.0f,
            "Volume multiplier for ambience (also follows the vanilla Ambient/Environment slider).");
        ambientEventFrequency = configuration.getFloat(
            "eventFrequency",
            CAT_AMBIENT,
            ambientEventFrequency,
            0.0f,
            4.0f,
            "How often random ambient events (birds, owls, cave drips, gusts...) happen. 0 disables them.");
        maxAmbientLoops = configuration
            .getInt("maxLoops", CAT_AMBIENT, maxAmbientLoops, 1, 6, "Maximum number of ambience layers mixed at once.");

        configuration.setCategoryComment(CAT_FOOTSTEPS, "Material aware footsteps, armor rustle, jumping and landing.");
        footstepsEnabled = configuration
            .getBoolean("enabled", CAT_FOOTSTEPS, footstepsEnabled, "Play the mod's footstep layers.");
        customFootsteps = configuration.getBoolean(
            "customSteps",
            CAT_FOOTSTEPS,
            customFootsteps,
            "Replace the vanilla step sounds of your own player with the mod's synthesized material steps. "
                + "When false, vanilla steps play and the mod only adds armor, foliage, water, jump and landing layers.");
        footstepVolume = configuration
            .getFloat("volume", CAT_FOOTSTEPS, footstepVolume, 0.0f, 2.0f, "Footstep volume multiplier.");
        armorSounds = configuration
            .getBoolean("armor", CAT_FOOTSTEPS, armorSounds, "Add leather/chain/plate rustle to steps when armored.");
        armorVolume = configuration.getFloat("armorVolume", CAT_FOOTSTEPS, armorVolume, 0.0f, 2.0f, "Armor volume.");
        jumpLandSounds = configuration
            .getBoolean("jumpAndLand", CAT_FOOTSTEPS, jumpLandSounds, "Play sounds when jumping and landing.");
        jumpLandVolume = configuration
            .getFloat("jumpAndLandVolume", CAT_FOOTSTEPS, jumpLandVolume, 0.0f, 2.0f, "Jump and landing volume.");

        configuration.setCategoryComment(CAT_EQUIP, "Sounds when switching the held item (sword draw, tool, bow...).");
        equipEnabled = configuration.getBoolean("enabled", CAT_EQUIP, equipEnabled, "Play item swap sounds.");
        equipVolume = configuration.getFloat("volume", CAT_EQUIP, equipVolume, 0.0f, 2.0f, "Item swap volume.");

        configuration.setCategoryComment(CAT_INVENTORY, "Sounds when picking up and placing items in inventories.");
        inventoryEnabled = configuration
            .getBoolean("enabled", CAT_INVENTORY, inventoryEnabled, "Play inventory click sounds.");
        inventoryVolume = configuration
            .getFloat("volume", CAT_INVENTORY, inventoryVolume, 0.0f, 2.0f, "Inventory click volume.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
