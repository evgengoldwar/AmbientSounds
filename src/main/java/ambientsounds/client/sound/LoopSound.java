package ambientsounds.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ITickableSound;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * A seamless looping ambience layer. Its volume glides towards {@link #setTarget(float)} every tick, so layers
 * crossfade smoothly as the player moves between biomes, goes underground or the night falls.
 */
public class LoopSound implements ITickableSound {

    /** Fraction of the remaining distance to the target covered each tick (about 3 seconds for a full fade). */
    private static final float FADE = 0.035f;
    private static final float MIN_STEP = 0.0015f;

    private final ResourceLocation location;
    private float volume = 0.001f;
    private float target;
    private boolean done;
    private float x, y, z;

    public LoopSound(ResourceLocation location) {
        this.location = location;
    }

    public void setTarget(float target) {
        this.target = target;
    }

    public float getTarget() {
        return target;
    }

    /** Fade out and stop. */
    public void finish() {
        target = 0;
    }

    public boolean isSilent() {
        return target <= 0 && volume <= MIN_STEP;
    }

    public void kill() {
        done = true;
    }

    @Override
    public void update() {
        // loops are stereo (never panned), but keep them on the listener anyway
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            x = (float) player.posX;
            y = (float) player.posY;
            z = (float) player.posZ;
        }
        float delta = target - volume;
        float step = delta * FADE;
        if (Math.abs(step) < MIN_STEP) {
            step = Math.abs(delta) < MIN_STEP ? delta : Math.copySign(MIN_STEP, delta);
        }
        volume += step;
        if (target <= 0 && volume <= MIN_STEP) {
            // never report a volume of exactly zero while still alive: the sound manager would skip it
            volume = MIN_STEP;
            done = true;
        }
    }

    @Override
    public boolean isDonePlaying() {
        return done;
    }

    @Override
    public ResourceLocation getPositionedSoundLocation() {
        return location;
    }

    @Override
    public boolean canRepeat() {
        return true;
    }

    @Override
    public int getRepeatDelay() {
        return 0;
    }

    @Override
    public float getVolume() {
        return Math.max(volume, MIN_STEP);
    }

    @Override
    public float getPitch() {
        return 1.0f;
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
        return AttenuationType.NONE;
    }
}
