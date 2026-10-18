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
        BlockPos origin = context.origin();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ());
        if (!level.getBiome(new BlockPos(origin.getX(), y, origin.getZ())).is(COUNTRY)) return false;
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
        Builder b = new Builder(level, foot, Rotation.getRandom(random));
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
                    b.set(x, yy, z, random.nextFloat() < 0.15f ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : rock);
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
            b.set(x, 3, -2, rock);
            b.set(x, 3, 2, rock);
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
        // Inside: the stove in the north-west corner and its chimney up through the roof, smoke from it.
        b.set(-2, 0, -1, Blocks.FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        for (int yy = 1; yy <= 6; yy++) b.set(-2, yy, -1, Blocks.BRICKS.defaultBlockState());
        b.set(-2, 7, -1, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false));
        // The bunk along the east end, its head to the north.
        BlockState bed = Blocks.BROWN_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        b.set(2, 0, -1, bed.setValue(BedBlock.PART, BedPart.HEAD));
        b.set(2, 0, 0, bed.setValue(BedBlock.PART, BedPart.FOOT));
        b.set(-2, 0, 1, Blocks.CRAFTING_TABLE.defaultBlockState());
        b.set(-1, 0, -1, Blocks.BARREL.defaultBlockState());
        b.set(0, 5, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        return true;
    }
}
