package ru.feytox.etherology.item;

import net.minecraft.item.Item;

/**
 * Identifies the bounded shared glint input accepted by the storage-menu core.
 *
 * <p>This item intentionally does not claim the canonical per-glint Ether state behavior.
 */
public final class EtherealStorageInputItem extends Item {

    /**
     * Creates a non-stackable storage input matching the canonical glint slot cardinality.
     */
    public EtherealStorageInputItem() {
        super(new Settings().maxCount(1));
    }
}
