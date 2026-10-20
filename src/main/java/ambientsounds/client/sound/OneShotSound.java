package ambientsounds.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ITickableSound;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * A fire-and-forget sound, either positioned in the world or attached to the listener. Listener sounds keep
 * following the player: OpenAL still pans non-attenuated mono sources by direction, so they must not stay behind.
 */
public class OneShotSound implements ITickableSound {

    private final ResourceLocation location;
    private final float volume;
    private final float pitch;
    private final boolean followListener;
    private final AttenuationType attenuation;
    private float x, y, z;

    private OneShotSound(ResourceLocation location, float volume, float pitch, float x, float y, float z,
        boolean followListener) {
        this.location = location;
        this.volume = volume;
        this.pitch = pitch;
        this.x = x;
        this.y = y;
        this.z = z;
        this.followListener = followListener;
        this.attenuation = followListener ? AttenuationType.NONE : AttenuationType.LINEAR;
        if (followListener) update();
    }

    public static OneShotSound at(ResourceLocation location, float volume, float pitch, double x, double y, double z) {
        return new OneShotSound(location, volume, pitch, (float) x, (float) y, (float) z, false);
    }

    public static OneShotSound global(ResourceLocation location, float volume, float pitch) {
        return new OneShotSound(location, volume, pitch, 0, 0, 0, true);
    }

    @Override
    public void update() {
        if (!followListener) return;
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            x = (float) player.posX;
            y = (float) player.posY;
            z = (float) player.posZ;
        }
    }

    @Override
    public boolean isDonePlaying() {
        // the sound manager drops the source by itself once it finished playing
        return false;
    }

    @Override
    public ResourceLocation getPositionedSoundLocation() {
        return location;
    }

    @Override
    public boolean canRepeat() {
        return false;
    }

    @Override
    public int getRepeatDelay() {
        return 0;
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public float getPitch() {
        return pitch;
    }

    @Override
    public float getXPosF() {
        return x;
    }

    @Override
    public float getYPosF() {
        return y;
    }

    @Override
    public float getZPosF() {
        return z;
    }

    @Override
    public AttenuationType getAttenuationType() {
        return attenuation;
    }
}
