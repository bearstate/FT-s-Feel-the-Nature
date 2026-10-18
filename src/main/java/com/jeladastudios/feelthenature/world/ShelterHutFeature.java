package com.jeladastudios.feelthenature.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A mountain shelter on the high pasture: a low hut of the rock round it, dry-stone walls with a lichened stone here and
 * there, a pitched roof of dark boards, a door to the south and a small window in each end. Inside, what a night up
 * there needs: a bunk, a stove with its chimney, a bench to work at, a barrel, a lantern.
 *
 * <p>On open ground within a couple of blocks of level only.</p>
 */
public class ShelterHutFeature extends Feature<NoneFeatureConfiguration> {

    /** The country shelters are built in: the high pastures and the tree line. */
    static final net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> COUNTRY = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.BIOME,
            new net.minecraft.resources.ResourceLocation(com.jeladastudios.feelthenature.FeelTheNature.MODID, "shelter_country"));

    public ShelterHutFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        // One chunk of a cell may have one; there, a few spots of it are tried for level ground.
        net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(context.origin());
        if (!Builder.chosen(level, chunk, CELL, 0x5E17E2L)) return false;
        for (int tries = 0; tries < 8; tries++) {
            BlockPos at = new BlockPos(chunk.getMinBlockX() + 4 + random.nextInt(8), 0, chunk.getMinBlockZ() + 4 + random.nextInt(8));
            if (tryAt(level, random, at)) return true;
        }
        return false;
    }

    /** How many chunks square the country is cut into for shelters, at most one to each. */
    static final int CELL = 24;

    private static boolean tryAt(WorldGenLevel level, RandomSource random, BlockPos origin) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ());
        if (!level.getBiome(new BlockPos(origin.getX(), y, origin.getZ())).is(COUNTRY)) return false;
        if (LookoutTowerFeature.nearTower(level, origin.getX(), origin.getZ(), CLEAR)) return false;
        // Near enough level and open over the hut's footprint and a block round it: what stands higher is cut away,
        // what lies lower built up.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX() + dx, origin.getZ() + dz);
                if (Math.abs(h - y) > 2) return false;
                if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, origin.getX() + dx, origin.getZ() + dz) != h) return false;
                if (!level.getFluidState(new BlockPos(origin.getX() + dx, h - 1, origin.getZ() + dz)).isEmpty()) return false;
            }
        }
        BlockPos foot = new BlockPos(origin.getX(), y, origin.getZ());
        BlockState rock = BoulderFeature.rockUnder(level, foot);
        if (rock == null || rock.is(Blocks.STONE)) rock = Blocks.COBBLESTONE.defaultBlockState();
        else if (rock.is(Blocks.DEEPSLATE)) rock = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        BlockState corner = polished(rock);
        Builder b = new Builder(level, foot, Rotation.getRandom(random));
        Builder.built("shelter", foot);
        b.clear(-4, 4, -4, 4, 8);
        // The floor, and the ground under the walls made good.
        for (int x = -3; x <= 3; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean wall = Math.abs(x) == 3 || Math.abs(z) == 2;
                b.set(x, -1, z, wall ? rock : Blocks.SPRUCE_PLANKS.defaultBlockState());
                b.fillDown(x, -2, z, rock);
            }
        }
        // Dry-stone walls, three courses high.
        for (int yy = 0; yy <= 2; yy++) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (Math.abs(x) != 3 && Math.abs(z) != 2) continue;
                    boolean quoin = Math.abs(x) == 3 && Math.abs(z) == 2;
                    b.set(x, yy, z, quoin ? corner : rubble(rock, random));
                }
            }
        }
        // The door to the south, a window in each end.
        BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        b.set(0, 0, 2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        b.set(0, 1, 2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        b.set(-3, 1, 0, Blocks.GLASS_PANE.defaultBlockState());
        b.set(3, 1, 0, Blocks.GLASS_PANE.defaultBlockState());
        // The long walls one course higher, under the roof's second course.
        for (int x = -3; x <= 3; x++) {
            b.set(x, 3, -2, rubble(rock, random));
            b.set(x, 3, 2, rubble(rock, random));
        }
        // The gable ends in boards, the roof pitched over them from eave to ridge.
        for (int c = 0; c <= 2; c++) {
            for (int z = -2 + c; z <= 2 - c; z++) {
                if (c == 0 && Math.abs(z) == 2) continue;
                b.set(-3, 3 + c, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                b.set(3, 3 + c, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
        }
        for (int x = -4; x <= 4; x++) {
            for (int c = 0; c <= 2; c++) {
                b.set(x, 3 + c, -3 + c, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
                b.set(x, 3 + c, 3 - c, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
            }
            b.set(x, 6, 0, Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        }
        // Inside: the stove in the north-west corner and its chimney up through the roof.
        b.set(-2, 0, -1, Blocks.FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        LookoutTowerFeature.chimney(b, -2, 1, -1, 6);
        // The bunk along the east end, its head to the north.
        BlockState bed = Blocks.BROWN_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        b.set(2, 0, -1, bed.setValue(BedBlock.PART, BedPart.HEAD));
        b.set(2, 0, 0, bed.setValue(BedBlock.PART, BedPart.FOOT));
        b.set(-2, 0, 1, Blocks.CRAFTING_TABLE.defaultBlockState());
        b.barrel(-1, 0, -1, LOOT, random);
        b.set(0, 5, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        // Outside: a stack of firewood along the west end under the eaves, a rain butt by the door catching the roof's run-off.
        BlockState logs = Blocks.SPRUCE_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z);
        for (int z = -1; z <= 1; z++) {
            if (b.get(-4, 0, z).isAir() && b.get(-4, -1, z).isSolidRender(level, b.m)) {
                b.set(-4, 0, z, logs);
                if (z != 1 && random.nextBoolean()) b.set(-4, 1, z, logs);
            }
        }
        if (b.get(1, 0, 3).isAir() && b.get(1, -1, 3).isSolidRender(level, b.m)) {
            b.set(1, 0, 3, Blocks.WATER_CAULDRON.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 1 + random.nextInt(3)));
        }
        b.join();
        return true;
    }

    /** How far from a lookout a shelter keeps, in blocks: out of the tower's way, and a walk from it. */
    static final int CLEAR = 24;

    /** The loot a shelter's barrel is filled from. */
    static final net.minecraft.resources.ResourceLocation LOOT =
            new net.minecraft.resources.ResourceLocation(com.jeladastudios.feelthenature.FeelTheNature.MODID, "chests/shelter");

    /** A stone of a dry-stone wall: rubble, a lichened one now and then, a stone of the rock under it here and there. */
    static BlockState rubble(BlockState rock, RandomSource random) {
        float r = random.nextFloat();
        if (r < 0.15f) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        if (r < 0.30f) return rock;
        return rock.is(Blocks.COBBLED_DEEPSLATE) ? rock : Blocks.COBBLESTONE.defaultBlockState();
    }

    /** The dressed stone for a wall's corners: the rock's polished form where it has one, else stone bricks. */
    static BlockState polished(BlockState rock) {
        if (rock.is(Blocks.COBBLED_DEEPSLATE)) return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        var key = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(rock.getBlock());
        if (key != null && !rock.is(Blocks.COBBLESTONE)) {
            var block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
                    new net.minecraft.resources.ResourceLocation(key.getNamespace(), "polished_" + key.getPath()));
            if (block != null && block != Blocks.AIR) return block.defaultBlockState();
        }
        return Blocks.STONE_BRICKS.defaultBlockState();
    }
}
