package com.jeladastudios.feelthenature.client.sound;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * What is round the player, as far as the ear cares: how much of the sky is over them and what covers the rest, how
 * much open air they can hear through, and what the ground round them is that the rain falls on and the wind moves.
 * Looked at four times a second and eased, so a step under a tree or out of a door is heard as a change, not a jump.
 *
 * <p>Nothing here reads the light. Sky light passes through glass and leaves, so a glass roof read as open sky; what is
 * over a column is read off the blocks themselves.</p>
 */
final class Surroundings {

    /** How much of the sky right over the player is open, 0 to 1, from a five by five patch of columns. */
    float sky = 1f;
    /** How much of it is covered close overhead by leaves, a wooden roof, a metal roof or glass; the rest is rock or far. */
    float leavesOver, woodOver, metalOver, glassOver;
    /** How much open air the player can hear through: rays out to a dozen blocks that reach a place under the sky. */
    float openNear = 1f;
    /** The share of the ground round the player that is deciduous canopy, needles, water, bare rock, or grass. */
    float leaves, conifers, water, rock, grass;
    /** What falls where the player stands. */
    Biome.Precipitation precipitation = Biome.Precipitation.NONE;
    /** A tree near the player, for a creak to come from; null where there is none. */
    BlockPos tree;

    /**
     * How much of the weather outside reaches the ear: the open sky over the player, or the open air they can hear
     * through a door, a window or a cave's mouth. Nothing at all deep in a cave or in a closed room.
     */
    float hear() {
        return Mix.hear(sky, openNear);
    }

    private static final int COLUMNS = 40, REACH = 12;
    /** How far up a roof is heard as the roof over the player: a room's ceiling, a tree's crown. */
    private static final int ROOF_NEAR = 6, CROWN_NEAR = 14;
    /** How much of each new look goes into what is heard: changes come over half a second or so. */
    private static final float EASE = 0.5f;

    private final RandomSource random = RandomSource.create();

    void look(ClientLevel level, LocalPlayer player) {
        BlockPos eye = BlockPos.containing(player.getEyePosition());
        precipitation = level.getBiome(eye).value().getPrecipitationAt(eye);
        overhead(level, eye);
        openNear = ease(openNear, openAir(level, player));
        ground(level, eye);
    }

    /** What is over each column of a five by five patch round the player, read off the first block above the eye. */
    private void overhead(ClientLevel level, BlockPos eye) {
        int open = 0, cLeaves = 0, cWood = 0, cMetal = 0, cGlass = 0, n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = eye.getX() + dx, z = eye.getZ() + dz;
                n++;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (eye.getY() >= top) {
                    open++;
                    continue;
                }
                for (int y = eye.getY() + 1, d = 1; y < top && d <= CROWN_NEAR; y++, d++) {
                    BlockState s = level.getBlockState(m.set(x, y, z));
                    if (s.isAir()) continue;
                    if (s.is(BlockTags.LEAVES)) {
                        cLeaves++;
                        break;
                    }
                    if (!s.blocksMotion() && s.getSoundType() != SoundType.GLASS) continue;
                    if (d <= ROOF_NEAR) {
                        SoundType t = s.getSoundType();
                        if (t == SoundType.GLASS) cGlass++;
                        else if (wooden(t)) cWood++;
                        else if (metal(t)) cMetal++;
                    }
                    break;
                }
            }
        }
        sky = ease(sky, open / (float) n);
        leavesOver = ease(leavesOver, cLeaves / (float) n);
        woodOver = ease(woodOver, cWood / (float) n);
        metalOver = ease(metalOver, cMetal / (float) n);
        glassOver = ease(glassOver, cGlass / (float) n);
    }

    /**
     * How much open air the player can hear through: rays out in eight directions to four, eight and twelve blocks, each
     * counted when nothing solid stands in its way and the place it reaches is under the open sky. A doorway or a cave's
     * mouth lets some of the weather in; the depths of a cave or a closed room let none.
     */
    private static float openAir(ClientLevel level, LocalPlayer player) {
        double ex = player.getX(), ey = player.getEyeY(), ez = player.getZ();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int reached = 0, rays = 0;
        for (int dir = 0; dir < 8; dir++) {
            double a = dir * Math.PI / 4, cx = Math.cos(a), cz = Math.sin(a);
            ray:
            for (int dist = 4; dist <= REACH; dist += 4) {
                rays++;
                for (int step = 1; step <= dist; step++) {
                    m.set(Mth.floor(ex + cx * step), Mth.floor(ey), Mth.floor(ez + cz * step));
                    if (level.getBlockState(m).isSolidRender(level, m)) break ray;
                }
                int tx = Mth.floor(ex + cx * dist), tz = Mth.floor(ez + cz * dist);
                if (Mth.floor(ey) >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, tx, tz)) reached++;
            }
        }
        return reached / (float) rays;
    }

    /** The ground round the player that the rain falls on and the wind moves, from forty random columns. */
    private void ground(ClientLevel level, BlockPos eye) {
        int n = 0, cLeaves = 0, cNeedles = 0, cWater = 0, cRock = 0, cGrass = 0;
        BlockPos found = null;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < COLUMNS; i++) {
            int x = eye.getX() + random.nextInt(2 * REACH + 1) - REACH;
            int z = eye.getZ() + random.nextInt(2 * REACH + 1) - REACH;
            if (!level.hasChunkAt(m.set(x, eye.getY(), z))) continue;
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            // Rain far below in a valley or high over a roof is not what the ear hears from here.
            if (y < eye.getY() - 20 || y > eye.getY() + 28) continue;
            BlockState s = level.getBlockState(m.set(x, y, z));
            n++;
            if (s.getFluidState().is(FluidTags.WATER)) {
                cWater++;
            } else if (s.is(BlockTags.LEAVES)) {
                if (conifer(s)) cNeedles++; else cLeaves++;
                if (found == null || random.nextInt(3) == 0) found = m.immutable();
            } else if (s.is(Blocks.GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN)) {
                cGrass++;
            } else if (rocky(s)) {
                cRock++;
            }
        }
        if (n > 0) {
            leaves = ease(leaves, cLeaves / (float) n);
            conifers = ease(conifers, cNeedles / (float) n);
            water = ease(water, cWater / (float) n);
            rock = ease(rock, cRock / (float) n);
            grass = ease(grass, cGrass / (float) n);
        }
        tree = found;
    }

    private static float ease(float was, float now) {
        return was + (now - was) * EASE;
    }

    private static boolean wooden(SoundType t) {
        return t == SoundType.WOOD || t == SoundType.BAMBOO_WOOD || t == SoundType.CHERRY_WOOD
                || t == SoundType.NETHER_WOOD || t == SoundType.SCAFFOLDING;
    }

    private static boolean metal(SoundType t) {
        return t == SoundType.METAL || t == SoundType.COPPER || t == SoundType.NETHERITE_BLOCK
                || t == SoundType.CHAIN || t == SoundType.LANTERN || t == SoundType.ANVIL;
    }

    /** Needles rather than leaves, by the block's name: vanilla's spruce, and the pines and firs of other mods. */
    private static boolean conifer(BlockState s) {
        String p = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        return p.contains("spruce") || p.contains("pine") || p.contains("fir") || p.contains("hemlock")
                || p.contains("cedar") || p.contains("larch") || p.contains("redwood");
    }

    /** Bare ground the rain rattles on: rock of any kind, gravel and scree. */
    private static boolean rocky(BlockState s) {
        if (s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL) || s.is(Blocks.COBBLESTONE)
                || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(BlockTags.TERRACOTTA)) return true;
        SoundType t = s.getSoundType();
        return t == SoundType.STONE || t == SoundType.DEEPSLATE || t == SoundType.BASALT || t == SoundType.TUFF
                || t == SoundType.CALCITE || t == SoundType.DRIPSTONE_BLOCK || t == SoundType.GRAVEL;
    }
}
