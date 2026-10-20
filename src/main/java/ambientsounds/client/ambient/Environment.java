package ambientsounds.client.ambient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeDictionary;

/**
 * Describes what surrounds the player as a set of smoothly changing 0..1 weights. Biomes are sampled on a grid
 * around the player so that ambience blends naturally at biome borders.
 */
public class Environment {

    /** Biome flavours that have their own ambience. */
    public enum Biome {
        PLAINS,
        FOREST,
        JUNGLE,
        SWAMP,
        OCEAN,
        DESERT,
        SNOW,
        MOUNTAIN
    }

    private static final int GRID_RADIUS = 3;
    private static final int GRID_STEP = 5;
    private static final float SMOOTHING = 0.03f;

    private final float[] biomeTarget = new float[Biome.values().length];
    private final float[] biome = new float[Biome.values().length];
    private final Map<BiomeGenBase, float[]> biomeCache = new HashMap<>();

    /** 1 at noon, 0 at midnight. */
    public float day;
    /** Local rain strength (0 in biomes where it never rains). */
    public float rain;
    /** How much open sky is around the player: 1 outside, 0 deep inside a cave or a house. */
    public float outdoor;
    /** Underground factor: no sky and below the surface. */
    public float cave;
    /** 0 at normal heights, 1 high up on mountains. */
    public float altitude;
    /** 1 close to sea level, fades out when far above it. */
    public float seaLevel;
    public boolean underwater;
    public boolean nether;
    public boolean end;

    private float outdoorTarget, caveTarget, rainTarget;
    private int scanTimer;
    private boolean initialized;

    public float get(Biome b) {
        return biome[b.ordinal()];
    }

    public void reset() {
        initialized = false;
        scanTimer = 0;
        biomeCache.clear();
    }

    public void update(World world, EntityPlayer player) {
        int px = MathHelper.floor_double(player.posX);
        int py = MathHelper.floor_double(player.posY + player.getEyeHeight());
        int pz = MathHelper.floor_double(player.posZ);

        nether = world.provider.dimensionId == -1 || world.provider.isHellWorld;
        end = world.provider.dimensionId == 1;
        underwater = player.isInsideOfMaterial(Material.water);

        if (--scanTimer <= 0) {
            scanTimer = 10;
            scan(world, px, py, pz);
        }

        float angle = world.getCelestialAngle(1.0f);
        float sun = MathHelper.cos(angle * (float) Math.PI * 2.0f);
        day = smoothstep(-0.12f, 0.22f, sun);

        double y = player.posY;
        altitude = clamp((float) (y - 95.0) / 45.0f);
        seaLevel = 1.0f - clamp((float) (y - 75.0) / 35.0f);

        float k = initialized ? SMOOTHING : 1.0f;
        for (int i = 0; i < biome.length; i++) {
            biome[i] += (biomeTarget[i] - biome[i]) * k;
        }
        outdoor += (outdoorTarget - outdoor) * k;
        cave += (caveTarget - cave) * k;
        rain += (rainTarget - rain) * k;
        initialized = true;
    }

    private void scan(World world, int px, int py, int pz) {
        Arrays.fill(biomeTarget, 0);
        int samples = 0;
        float rainy = 0;
        for (int dx = -GRID_RADIUS; dx <= GRID_RADIUS; dx++) {
            for (int dz = -GRID_RADIUS; dz <= GRID_RADIUS; dz++) {
                BiomeGenBase b = world.getBiomeGenForCoords(px + dx * GRID_STEP, pz + dz * GRID_STEP);
                if (b == null) continue;
                float[] w = weightsFor(b);
                for (int i = 0; i < w.length; i++) biomeTarget[i] += w[i];
                rainy += b.rainfall > 0.15f ? 1 : 0;
                samples++;
            }
        }
        if (samples > 0) {
            for (int i = 0; i < biomeTarget.length; i++) biomeTarget[i] /= samples;
            rainy /= samples;
        }
        rainTarget = world.getRainStrength(1.0f) * rainy;

        // sky exposure around the head: sky light is 15 under open sky (independent of the time of day)
        int sky = 0, count = 0;
        for (int dx = -4; dx <= 4; dx += 4) {
            for (int dz = -4; dz <= 4; dz += 4) {
                for (int dy = 0; dy <= 2; dy += 2) {
                    int y = py + dy;
                    if (y < 0 || y > 255) continue;
                    sky += world.getSavedLightValue(EnumSkyBlock.Sky, px + dx, y, pz + dz);
                    count++;
                }
            }
        }
        float exposure = count > 0 ? sky / (15.0f * count) : 1.0f;
        if (world.provider.hasNoSky) exposure = 0;
        outdoorTarget = smoothstep(0.25f, 0.9f, exposure);
        // underground: no sky and a thick layer of ground above (a roofed house is not a cave)
        float cover = world.getHeightValue(px, pz) - py;
        float depth = clamp((cover - 4.0f) / 12.0f);
        if (py < 40) depth = 1.0f;
        caveTarget = world.provider.hasNoSky ? 0 : (1.0f - outdoorTarget) * depth;
    }

    private float[] weightsFor(BiomeGenBase b) {
        float[] cached = biomeCache.get(b);
        if (cached != null) return cached;

        float[] w = new float[Biome.values().length];
        boolean beach = is(b, "BEACH"), ocean = is(b, "OCEAN");
        if (beach || ocean) {
            w[Biome.OCEAN.ordinal()] = 1.0f;
        } else if (is(b, "SWAMP")) {
            w[Biome.SWAMP.ordinal()] = 1.0f;
        } else if (is(b, "JUNGLE")) {
            w[Biome.JUNGLE.ordinal()] = 1.0f;
        } else if (is(b, "SANDY") || is(b, "MESA") || (is(b, "HOT") && is(b, "DRY"))) {
            w[Biome.DESERT.ordinal()] = 1.0f;
        } else {
            boolean snowy = is(b, "SNOWY") || b.getEnableSnow();
            boolean wooded = is(b, "FOREST") || is(b, "CONIFEROUS") || is(b, "DENSE");
            if (snowy) {
                w[Biome.SNOW.ordinal()] = wooded ? 0.5f : 1.0f;
                if (wooded) w[Biome.FOREST.ordinal()] = 0.5f;
            } else if (wooded) {
                w[Biome.FOREST.ordinal()] = 1.0f;
            } else {
                w[Biome.PLAINS.ordinal()] = 1.0f;
            }
        }
        if (is(b, "MOUNTAIN") || is(b, "HILLS")) {
            for (int i = 0; i < w.length; i++) w[i] *= 0.6f;
            w[Biome.MOUNTAIN.ordinal()] += 0.4f;
        }
        biomeCache.put(b, w);
        return w;
    }

    /** Looks a dictionary type up by name, so a type missing from this Forge version simply never matches. */
    private static boolean is(BiomeGenBase b, String type) {
        try {
            return BiomeDictionary.isBiomeOfType(b, BiomeDictionary.Type.valueOf(type));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static float clamp(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    static float smoothstep(float lo, float hi, float v) {
        float t = clamp((v - lo) / (hi - lo));
        return t * t * (3 - 2 * t);
    }
}
