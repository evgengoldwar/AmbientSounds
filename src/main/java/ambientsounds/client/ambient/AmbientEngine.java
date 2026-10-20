package ambientsounds.client.ambient;

import static ambientsounds.client.ambient.Environment.Biome.DESERT;
import static ambientsounds.client.ambient.Environment.Biome.FOREST;
import static ambientsounds.client.ambient.Environment.Biome.JUNGLE;
import static ambientsounds.client.ambient.Environment.Biome.MOUNTAIN;
import static ambientsounds.client.ambient.Environment.Biome.OCEAN;
import static ambientsounds.client.ambient.Environment.Biome.PLAINS;
import static ambientsounds.client.ambient.Environment.Biome.SNOW;
import static ambientsounds.client.ambient.Environment.Biome.SWAMP;

import java.util.Arrays;
import java.util.Comparator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import ambientsounds.Config;
import ambientsounds.client.sound.LoopSound;
import ambientsounds.client.sound.Sounds;

/** Mixes looping ambience layers and schedules random ambient events from the current {@link Environment}. */
public class AmbientEngine {

    private enum Layer {

        PLAINS_WIND("plains", 0.40f),
        FOREST_LEAVES("forest", 0.42f),
        NIGHT_CRICKETS("night", 0.25f),
        JUNGLE_INSECTS("jungle", 0.30f),
        SWAMP_FROGS("swamp", 0.30f),
        OCEAN_WAVES("ocean", 0.50f),
        DESERT_WIND("desert", 0.35f),
        SNOW_WIND("snow", 0.38f),
        MOUNTAIN_WIND("mountain", 0.40f),
        CAVE("cave", 0.30f),
        NETHER("nether", 0.45f),
        END("end", 0.30f),
        UNDERWATER("underwater", 0.40f);

        final ResourceLocation sound;
        final float gain;

        Layer(String name, float gain) {
            this.sound = Sounds.of("ambient.loop." + name);
            this.gain = gain;
        }
    }

    private static final Layer[] LAYERS = Layer.values();
    private static final int START_GRACE_TICKS = 100;
    private static final int HEALTH_CHECK_TICKS = 20;
    /** A streamed sound may briefly report "not playing" while buffering: only restart after this many checks. */
    private static final int DEAD_CHECKS = 5;
    private static final int RESTART_COOLDOWN_TICKS = 400;
    private static final float START_THRESHOLD = 0.05f;
    /** Layers already playing are preferred when ranking, so two similar layers do not keep swapping. */
    private static final float PLAYING_BONUS = 1.6f;

    private static final ResourceLocation BIRD = Sounds.of("ambient.bird");
    private static final ResourceLocation WOODPECKER = Sounds.of("ambient.woodpecker");
    private static final ResourceLocation DRIP = Sounds.of("ambient.drip");
    private static final ResourceLocation LAVA_POP = Sounds.of("ambient.lava_pop");

    private final Environment env = new Environment();
    private final LoopSound[] playing = new LoopSound[LAYERS.length];
    private final int[] age = new int[LAYERS.length];
    private final int[] cooldown = new int[LAYERS.length];
    private final int[] deadChecks = new int[LAYERS.length];
    private final float[] target = new float[LAYERS.length];
    private final Integer[] order = new Integer[LAYERS.length];

    public void tick(Minecraft mc, World world, EntityPlayer player) {
        env.update(world, player);

        boolean audible = Config.ambientEnabled
            && mc.gameSettings.getSoundLevel(SoundCategory.MASTER) > 0
            && mc.gameSettings.getSoundLevel(SoundCategory.AMBIENT) > 0;
        if (audible) {
            computeTargets();
            keepLoudest(Config.maxAmbientLoops);
        } else {
            Arrays.fill(target, 0);
        }
        updateLoops(mc.getSoundHandler());

        if (audible && Config.ambientEventFrequency > 0) {
            randomEvents(player);
        }
    }

    public void stop(SoundHandler handler) {
        for (int i = 0; i < playing.length; i++) {
            if (playing[i] != null) {
                playing[i].kill();
                handler.stopSound(playing[i]);
                playing[i] = null;
            }
            cooldown[i] = 0;
        }
        env.reset();
    }

    private void computeTargets() {
        Arrays.fill(target, 0);
        if (env.nether) {
            set(Layer.NETHER, 1.0f);
        } else if (env.end) {
            set(Layer.END, 1.0f);
        } else {
            float surface = 1.0f - env.cave;
            // indoors the outside is still faintly audible, like through walls and windows
            float open = (0.2f + 0.8f * env.outdoor) * surface;
            float night = 1.0f - env.day;
            float dry = 1.0f - env.rain;
            float low = 1.0f - env.altitude;
            float plains = env.get(PLAINS), forest = env.get(FOREST), jungle = env.get(JUNGLE);

            set(Layer.PLAINS_WIND, plains * open * (1.0f + 0.3f * env.rain) * (1.0f - 0.5f * env.altitude));
            set(Layer.FOREST_LEAVES, forest * open * (0.55f + 0.45f * env.day) * (1.0f + 0.2f * env.rain));
            set(Layer.NIGHT_CRICKETS, (plains + forest + 0.5f * jungle) * night * dry * low * open);
            set(Layer.JUNGLE_INSECTS, jungle * open * (0.35f + 0.65f * env.day) * (1.0f - 0.5f * env.rain));
            set(Layer.SWAMP_FROGS, env.get(SWAMP) * open * (0.45f + 0.55f * night) * (1.0f - 0.4f * env.rain));
            set(Layer.OCEAN_WAVES, env.get(OCEAN) * open * env.seaLevel);
            set(Layer.DESERT_WIND, env.get(DESERT) * open);
            set(Layer.SNOW_WIND, env.get(SNOW) * open * (1.0f + 0.2f * env.rain));
            set(Layer.MOUNTAIN_WIND, Math.max(env.get(MOUNTAIN), env.altitude) * open);
            set(Layer.CAVE, env.cave);
        }
        if (env.underwater) {
            for (int i = 0; i < target.length; i++) target[i] *= 0.15f;
            set(Layer.UNDERWATER, 1.0f);
        }
        for (int i = 0; i < target.length; i++) {
            target[i] = Math.min(1.0f, target[i] * LAYERS[i].gain * Config.ambientVolume);
        }
    }

    private void set(Layer layer, float value) {
        target[layer.ordinal()] = Math.max(0, value);
    }

    /** Streaming channels are limited, so only the most important layers are allowed to play. */
    private void keepLoudest(int max) {
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble(i -> -target[i] * (playing[i] != null ? PLAYING_BONUS : 1.0f)));
        for (int rank = max; rank < order.length; rank++) {
            target[order[rank]] = 0;
        }
    }

    private void updateLoops(SoundHandler handler) {
        for (int i = 0; i < LAYERS.length; i++) {
            LoopSound sound = playing[i];
            if (sound != null) {
                sound.setTarget(target[i]);
                age[i]++;
                if (sound.isDonePlaying()) {
                    playing[i] = null;
                } else if (age[i] > START_GRACE_TICKS && age[i] % HEALTH_CHECK_TICKS == 0) {
                    deadChecks[i] = handler.isSoundPlaying(sound) ? 0 : deadChecks[i] + 1;
                    if (deadChecks[i] >= DEAD_CHECKS) {
                        // the sound system really dropped it (resource reload, device change...): try again later
                        sound.kill();
                        handler.stopSound(sound);
                        playing[i] = null;
                        cooldown[i] = RESTART_COOLDOWN_TICKS;
                    }
                }
            } else if (cooldown[i] > 0) {
                cooldown[i]--;
            } else if (target[i] > START_THRESHOLD) {
                sound = new LoopSound(LAYERS[i].sound);
                sound.setTarget(target[i]);
                handler.playSound(sound);
                playing[i] = sound;
                age[i] = 0;
                deadChecks[i] = 0;
            }
        }
    }

    // ------------------------------------------------------------------------------------------ random events

    private void randomEvents(EntityPlayer player) {
        if (env.nether) {
            maybePositional(player, LAVA_POP, 1.0f, 10, 0.45f, 5, 12, -2, 3);
            return;
        }
        if (env.end || env.underwater) return;

        float surface = 1.0f - env.cave;
        float open = env.outdoor * surface;
        float dry = 1.0f - env.rain;
        float low = 1.0f - env.altitude;
        float plains = env.get(PLAINS), forest = env.get(FOREST), jungle = env.get(JUNGLE);
        float woods = forest + jungle;

        float birds = (woods + 0.5f * plains) * env.day * dry * open * low;
        maybePositional(player, BIRD, birds, 12, 0.5f, 7, 14, 3, 9);
        maybePositional(player, WOODPECKER, forest * env.day * dry * open, 120, 0.45f, 8, 14, 2, 6);

        maybePositional(player, DRIP, env.cave, 8, 0.5f, 4, 10, 0, 4);
    }

    /** Fires on average every {@code meanSeconds} seconds when {@code weight} is 1. */
    private boolean roll(float weight, float meanSeconds) {
        if (weight <= 0.01f) return false;
        float chance = weight * Config.ambientEventFrequency / (meanSeconds * 20.0f);
        return Sounds.RANDOM.nextFloat() < chance;
    }

    private void maybePositional(EntityPlayer player, ResourceLocation sound, float weight, float meanSeconds,
        float volume, float minDist, float maxDist, float minUp, float maxUp) {
        if (!roll(weight, meanSeconds)) return;
        double angle = Sounds.RANDOM.nextDouble() * Math.PI * 2.0;
        double dist = minDist + Sounds.RANDOM.nextDouble() * (maxDist - minDist);
        double up = minUp + Sounds.RANDOM.nextDouble() * (maxUp - minUp);
        float v = Math.min(1.0f, volume * Config.ambientVolume);
        Sounds.playAt(
            sound,
            Sounds.vary(v, v * 0.15f),
            Sounds.vary(1.0f, 0.07f),
            player.posX + Math.cos(angle) * dist,
            player.posY + up,
            player.posZ + Math.sin(angle) * dist);
    }
}
