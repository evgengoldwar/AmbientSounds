package ambientsounds.client.sound;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.util.ResourceLocation;

import ambientsounds.AmbientSounds;

/** Sound event names (see assets/ambientsounds/sounds.json) and small playback helpers. */
public final class Sounds {

    public static final Random RANDOM = new Random();

    private Sounds() {}

    public static ResourceLocation of(String event) {
        return new ResourceLocation(AmbientSounds.MODID, event);
    }

    public static void play(ISound sound) {
        Minecraft.getMinecraft()
            .getSoundHandler()
            .playSound(sound);
    }

    public static void playGlobal(ResourceLocation sound, float volume, float pitch) {
        if (volume > 0.001f) {
            play(OneShotSound.global(sound, Math.min(volume, 1.0f), pitch));
        }
    }

    public static void playAt(ResourceLocation sound, float volume, float pitch, double x, double y, double z) {
        if (volume > 0.001f) {
            play(OneShotSound.at(sound, volume, pitch, x, y, z));
        }
    }

    /** Random value in [center - spread, center + spread]. */
    public static float vary(float center, float spread) {
        return center + (RANDOM.nextFloat() * 2.0f - 1.0f) * spread;
    }
}
