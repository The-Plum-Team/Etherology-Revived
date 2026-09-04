package ru.feytox.etherology.magic.lens;

import com.mojang.serialization.Codec;
import ru.feytox.etherology.util.misc.ItemDataKey;

/**
 * Owns the persisted item-data keys shared by both loader implementations of lenses.
 */
public final class LensDataKeys {

    public static final ItemDataKey<LensComponent> LENS =
            new ItemDataKey<>("lens", LensComponent.CODEC);
    public static final ItemDataKey<Integer> PSEUDO_DAMAGE =
            new ItemDataKey<>("pseudo_damage", Codec.INT);

    private LensDataKeys() {
    }
}
