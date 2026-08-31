package ru.feytox.etherology.item;

import net.minecraft.item.Item;

/**
 * Identifies a non-stackable glint whose Ether state is persisted by the shared data owner.
 */
public class EtherealStorageInputItem extends Item {

    /**
     * Defines the canonical capacity shared by the registered Fabric and Common glint shard.
     */
    public static final float MAX_ETHER = 128.0f;

    private final float maxEther;

    /**
     * Creates the canonical non-stackable 128-Ether glint shard.
     */
    public EtherealStorageInputItem() {
        this(MAX_ETHER);
    }

    /**
     * Lets the canonical Fabric presentation retain its existing capacity constructor.
     *
     * @param maxEther Ether capacity exposed by the concrete glint type
     */
    protected EtherealStorageInputItem(float maxEther) {
        super(new Settings().maxCount(1));
        this.maxEther = maxEther;
    }

    /**
     * Returns the maximum Ether units this glint accepts.
     *
     * @return glint capacity in Ether units
     */
    public float getMaxEther() {
        return maxEther;
    }
}
