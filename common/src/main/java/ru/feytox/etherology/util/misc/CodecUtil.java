package ru.feytox.etherology.util.misc;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArraySet;

public final class CodecUtil {

    public static final Codec<IntArraySet> INT_SET =
            Codec.list(Codec.INT).xmap(IntArraySet::new, IntArrayList::new).stable();

    private CodecUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
