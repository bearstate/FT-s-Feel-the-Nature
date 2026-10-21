package com.jeladastudios.feelthenature.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * A boulder of the rock it lies on: fallen from the outcrop above it or left standing as the softer ground round it
 * wore away, so it is the very rock the ground is made of here: gneiss in a fold belt, basalt on a rift's floor,
 * granite over a pluton. FT's Geology lays that rock under the soil; the boulder reads it from there.
 *
 * <p>A few overlapping blobs, as vanilla's forest rock, half sunk into the ground. In damp forest it takes moss.</p>
 */
public class BoulderFeature extends Feature<BoulderFeature.Config> {

    public record Config(float mossChance) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0f, 1f).fieldOf("moss_chance").forGetter(Config::mossChance)
        ).apply(i, Config::new));
    }

    /** How deep under the surface the rock is looked for. */
    private static final int PROBE = 24;

    public BoulderFeature(Codec<Config> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        // The ground a block or two either way of where it was asked for, as for a fallen log.
        BlockPos origin = null;
        for (int dy : new int[]{0, 1, -1, 2, -2}) {
            BlockPos p = context.origin().above(dy);
            BlockState here = level.getBlockState(p), under = level.getBlockState(p.below());
            if ((here.isAir() || here.canBeReplaced()) && here.getFluidState().isEmpty()
                    && (under.is(BlockTags.DIRT) || rock(under) || under.is(Blocks.GRAVEL))) {
                origin = p;
                break;
            }
        }
        if (origin == null) return false;
        // No rock within reach under deep soil (a floodplain's silt, a thick moraine) and so no boulder of it.
        BlockState stone = rockUnder(level, origin);
        if (stone == null) return false;

        // Three to four blobs a block or two across, the first sunk a block into the ground.
        BlockPos at = origin.below();
        int blobs = 3 + random.nextInt(2);
        for (int b = 0; b < blobs; b++) {
            int rx = random.nextInt(2), ry = random.nextInt(2), rz = random.nextInt(2);
            float r = (rx + ry + rz) * 0.333f + 0.5f;
            for (BlockPos p : BlockPos.betweenClosed(at.offset(-rx, -ry, -rz), at.offset(rx, ry, rz))) {
                if (p.distSqr(at) > r * r) continue;
                BlockState was = level.getBlockState(p);
                if (!was.isAir() && !was.canBeReplaced() && !was.is(BlockTags.DIRT) && !rock(was)) continue;
                level.setBlock(p, stone, 2);
            }
            at = at.offset(-1 + random.nextInt(2), -random.nextInt(2), -1 + random.nextInt(2));
        }
        if (context.config().mossChance() > 0f) {
            for (BlockPos p : BlockPos.betweenClosed(origin.offset(-2, 0, -2), origin.offset(2, 3, 2))) {
                if (level.getBlockState(p).isAir() && level.getBlockState(p.below()) == stone
                        && random.nextFloat() < context.config().mossChance()) {
                    level.setBlock(p, Blocks.MOSS_CARPET.defaultBlockState(), 2);
                }
            }
        }
        return true;
    }

    /** The first rock under the soil here, or null where there is none within reach. */
    static BlockState rockUnder(WorldGenLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos m = origin.mutable();
        for (int d = 1; d <= PROBE; d++) {
            BlockState s = level.getBlockState(m.move(0, -1, 0));
            if (rock(s) && !s.is(Blocks.BEDROCK)) return s;
            // Through soil and loose ground; anything else (water, wood, an ore, something built) ends the search.
            boolean loose = s.isAir() || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL)
                    || s.is(Blocks.CLAY);
            if (!loose || !s.getFluidState().isEmpty()) return null;
        }
        return null;
    }

    /** Rock, by what it is made of: stone of any kind, but not soil, sand, ore, wood or a built block. */
    private static boolean rock(BlockState s) {
        if (s.is(BlockTags.BASE_STONE_OVERWORLD)) return true;
        if (!s.isSolidRender(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return false;
        if (s.is(net.minecraftforge.common.Tags.Blocks.ORES)) return false;
        SoundType t = s.getSoundType();
        return t == SoundType.STONE || t == SoundType.DEEPSLATE || t == SoundType.BASALT || t == SoundType.TUFF
                || t == SoundType.CALCITE;
    }
}
