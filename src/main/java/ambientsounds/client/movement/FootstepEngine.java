package ambientsounds.client.movement;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import ambientsounds.Config;
import ambientsounds.client.sound.Sounds;

/**
 * Material aware footsteps for the local player, with armor rustle, foliage brushing, jump and landing sounds.
 * Steps follow the same cadence as vanilla (one step per 1/0.6 blocks walked).
 */
public class FootstepEngine {

    private static final float STRIDE = 1.0f;
    private static final float BASE_STEP_VOLUME = 0.22f;

    private static final ResourceLocation JUMP = Sounds.of("movement.jump");
    private static final ResourceLocation LAND = Sounds.of("movement.land");
    private static final ResourceLocation LAND_HARD = Sounds.of("movement.land_hard");
    private static final ResourceLocation ARMOR_LEATHER = Sounds.of("armor.leather");
    private static final ResourceLocation ARMOR_CHAIN = Sounds.of("armor.chain");
    private static final ResourceLocation ARMOR_PLATE = Sounds.of("armor.plate");

    private boolean initialized;
    private boolean wasOnGround;
    private double lastX, lastY, lastZ;
    private float walked;
    private float airFall;
    private int stillTicks;

    public void reset() {
        initialized = false;
    }

    public void tick(World world, EntityPlayer player) {
        if (!initialized) {
            initialized = true;
            wasOnGround = player.onGround;
            lastX = player.posX;
            lastY = player.posY;
            lastZ = player.posZ;
            walked = 0;
            airFall = 0;
            return;
        }

        double dx = player.posX - lastX, dy = player.posY - lastY, dz = player.posZ - lastZ;
        lastX = player.posX;
        lastY = player.posY;
        lastZ = player.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        // teleports, respawns and dimension changes are not steps
        if (horizontal > 4.0 || Math.abs(dy) > 4.0) {
            wasOnGround = player.onGround;
            airFall = 0;
            return;
        }

        boolean onGround = player.onGround;
        boolean flying = player.capabilities.isFlying;
        boolean riding = player.ridingEntity != null;
        boolean ladder = player.isOnLadder();

        if (Config.footstepsEnabled && !riding && !flying) {
            if (onGround || ladder) {
                if (horizontal < 0.01 && (!ladder || Math.abs(dy) < 0.01)) {
                    stillTicks++;
                } else {
                    // when starting to walk from standing still the first step comes quickly
                    if (stillTicks > 5) walked = STRIDE * 0.6f;
                    stillTicks = 0;
                }
                walked += (float) (horizontal * 0.6);
                if (ladder && !onGround) walked += (float) (Math.abs(dy) * 0.6);
                if (walked >= STRIDE) {
                    walked -= STRIDE;
                    if (walked > STRIDE) walked = 0;
                    step(world, player, 1.0f, ladder && !onGround);
                }
            }
        }

        if (Config.jumpLandSounds && !riding && !flying && !player.isInWater()) {
            if (wasOnGround && !onGround && player.motionY > 0.2 && !ladder) {
                jump(world, player);
            } else if (!wasOnGround && onGround && airFall > 0.5f) {
                land(world, player, airFall);
                walked = 0;
            }
        }
        if (!onGround) airFall = Math.max(airFall, player.fallDistance);
        else airFall = 0;
        wasOnGround = onGround;
    }

    private void step(World world, EntityPlayer player, float loudness, boolean climbing) {
        Block floor = climbing ? null : floorBlock(world, player, 0.2);
        StepMaterial material = climbing ? climbMaterial(world, player) : (floor == null ? null : materialOf(world, player, floor));
        if (material == null) return;

        float volume = BASE_STEP_VOLUME * loudness * material.gain * Config.footstepVolume;
        float pitch = Sounds.vary(1.0f, 0.07f);
        if (player.isSneaking()) volume *= 0.4f;
        else if (player.isSprinting()) {
            volume *= 1.25f;
            pitch += 0.03f;
        }
        if (player.isInWater()) {
            playAtFeet(player, StepMaterial.WADE.sound, volume, pitch);
        } else if (Config.customFootsteps) {
            playAtFeet(player, material.sound, volume, pitch);
        }
        // otherwise the vanilla step (a real recording) plays and the layers below are added to it

        if (!climbing && feetInFoliage(world, player) && material != StepMaterial.LEAVES) {
            playAtFeet(player, StepMaterial.LEAVES.sound, volume * 0.4f, Sounds.vary(1.1f, 0.08f));
        }
        if (Config.armorSounds && !player.isSneaking()) {
            ResourceLocation armor = armorSound(player);
            if (armor != null) {
                float av = 0.18f * loudness * Config.armorVolume * (player.isSprinting() ? 1.3f : 1.0f);
                playAtFeet(player, armor, av, Sounds.vary(1.0f, 0.08f));
            }
        }
    }

    private void jump(World world, EntityPlayer player) {
        playAtFeet(player, JUMP, 0.18f * Config.jumpLandVolume, Sounds.vary(1.0f, 0.08f));
        Block floor = floorBlock(world, player, 0.6);
        if (floor != null) blockSound(floor, 0.12f * Config.jumpLandVolume, 1.1f);
    }

    private void land(World world, EntityPlayer player, float fall) {
        Block floor = floorBlock(world, player, 0.2);
        // stepping down a single block only makes a normal step, real jumps and falls land audibly
        if (fall > 1.1f) {
            boolean hard = fall > 3.5f;
            float v = Math.min(0.6f, 0.25f + fall * 0.04f) * Config.jumpLandVolume;
            playAtFeet(player, hard ? LAND_HARD : LAND, v, Sounds.vary(1.0f, 0.06f));
            if (floor != null) blockSound(floor, Math.min(0.45f, 0.2f + fall * 0.03f) * Config.jumpLandVolume, 0.8f);
        } else if (floor != null && Config.customFootsteps && Config.footstepsEnabled) {
            step(world, player, 1.1f, false);
        }
    }

    /** The block's own (vanilla or modded) step recording, used for the floor part of jumps and landings. */
    private static void blockSound(Block block, float volume, float pitch) {
        Block.SoundType type = block.stepSound;
        if (type == null) return;
        Sounds.playGlobal(new ResourceLocation(type.getStepResourcePath()), volume * type.getVolume(),
            Sounds.vary(pitch * type.getPitch(), 0.05f));
    }

    private static void playAtFeet(EntityPlayer player, ResourceLocation sound, float volume, float pitch) {
        // played without distance attenuation: it is our own body, always right here
        Sounds.playGlobal(sound, volume, pitch);
    }

    private static StepMaterial materialOf(World world, EntityPlayer player, Block floor) {
        // a snow layer or carpet at feet level wins over the block below
        Block atFeet = world.getBlock(
            MathHelper.floor_double(player.posX),
            MathHelper.floor_double(player.boundingBox.minY + 0.01),
            MathHelper.floor_double(player.posZ));
        StepMaterial overlay = StepMaterial.overlay(atFeet);
        return overlay != null ? overlay : StepMaterial.of(floor);
    }

    private static Block floorBlock(World world, EntityPlayer player, double below) {
        int y = MathHelper.floor_double(player.boundingBox.minY - below);
        // check every block column the player stands on, so edges of blocks still make sound
        int[] xs = { MathHelper.floor_double(player.posX), MathHelper.floor_double(player.boundingBox.minX + 0.01),
            MathHelper.floor_double(player.boundingBox.maxX - 0.01) };
        int[] zs = { MathHelper.floor_double(player.posZ), MathHelper.floor_double(player.boundingBox.minZ + 0.01),
            MathHelper.floor_double(player.boundingBox.maxZ - 0.01) };
        for (int x : xs) {
            for (int z : zs) {
                Block block = world.getBlock(x, y, z);
                if (block.getMaterial() != Material.air && !block.getMaterial()
                    .isLiquid()) {
                    return block;
                }
                // fences and walls are taller than one block
                Block below2 = world.getBlock(x, y - 1, z);
                int render = below2.getRenderType();
                if (render == 11 || render == 21 || render == 32) return below2;
            }
        }
        return null;
    }

    private static StepMaterial climbMaterial(World world, EntityPlayer player) {
        Block block = world.getBlock(
            MathHelper.floor_double(player.posX),
            MathHelper.floor_double(player.boundingBox.minY),
            MathHelper.floor_double(player.posZ));
        if (block.getMaterial() == Material.air) return StepMaterial.WOOD;
        if (block.getMaterial() == Material.vine) return StepMaterial.LEAVES;
        return StepMaterial.of(block);
    }

    private static boolean feetInFoliage(World world, EntityPlayer player) {
        int x = MathHelper.floor_double(player.posX), z = MathHelper.floor_double(player.posZ);
        int y = MathHelper.floor_double(player.boundingBox.minY + 0.01);
        return StepMaterial.isFoliage(world.getBlock(x, y, z)) || StepMaterial.isFoliage(world.getBlock(x, y + 1, z));
    }

    /** The heaviest worn piece decides: plate clanks, chainmail jingles, leather creaks. */
    private static ResourceLocation armorSound(EntityPlayer player) {
        int weight = 0;
        for (int slot = 0; slot < 4; slot++) {
            ItemStack stack = player.getCurrentArmor(slot);
            if (stack == null || !(stack.getItem() instanceof ItemArmor)) continue;
            ItemArmor.ArmorMaterial material = ((ItemArmor) stack.getItem()).getArmorMaterial();
            int w;
            switch (material.name()) {
                case "CLOTH":
                    w = 1;
                    break;
                case "CHAIN":
                    w = 2;
                    break;
                case "IRON":
                case "GOLD":
                case "DIAMOND":
                    w = 3;
                    break;
                default:
                    // modded armor: guess from how protective the chestplate of that material is
                    int protection = material.getDamageReductionAmount(1);
                    w = protection >= 6 ? 3 : (protection >= 4 ? 2 : 1);
            }
            // leggings and chestplate are what you actually hear
            if (slot == 0 || slot == 3) w = Math.min(w, 1);
            weight = Math.max(weight, w);
        }
        switch (weight) {
            case 1:
                return ARMOR_LEATHER;
            case 2:
                return ARMOR_CHAIN;
            case 3:
                return ARMOR_PLATE;
            default:
                return null;
        }
    }
}
