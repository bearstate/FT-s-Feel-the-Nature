package com.jeladastudios.feelthenature.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The log a lookout keeps: a written book left on the lectern by the last keeper. A first page on what the tower is for
 * and the rescue frequency, then a few days' entries of weather and smoke seen, drawn from a stock of them. Written in
 * translatable text, so each reader reads it in their own language.
 */
public final class Logbook {

    private Logbook() {}

    /** How many entries the language files hold. */
    static final int ENTRIES = 16;

    /** A lookout's log, its entries picked and dated at random. */
    public static ItemStack lookout(RandomSource random) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        tag.putString("title", "Lookout Log");
        tag.putString("author", "-");
        tag.putBoolean("resolved", true);
        ListTag pages = new ListTag();
        pages.add(page(Component.translatable("book.fts_feelthenature.lookout_log.cover")));
        // Four to six entries, from a few days into the season, in order.
        int count = 4 + random.nextInt(3), day = 3 + random.nextInt(20);
        java.util.List<Integer> pool = new java.util.ArrayList<>();
        for (int i = 1; i <= ENTRIES; i++) pool.add(i);
        java.util.Collections.shuffle(pool, new java.util.Random(random.nextLong()));
        for (int i = 0; i < count; i++) {
            pages.add(page(Component.translatable("book.fts_feelthenature.lookout_log.entry." + pool.get(i), day)));
            day += 1 + random.nextInt(4);
        }
        pages.add(page(Component.translatable("book.fts_feelthenature.lookout_log.last")));
        tag.put("pages", pages);
        book.setHoverName(Component.translatable("book.fts_feelthenature.lookout_log.name"));
        return book;
    }

    private static StringTag page(Component text) {
        return StringTag.valueOf(Component.Serializer.toJson(text));
    }
}
