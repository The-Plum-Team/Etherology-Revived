package ru.feytox.etherology.registry.item;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.registry.SharedDeferredRegister;
import ru.feytox.etherology.registry.block.SharedAttrahiteBlocks;

/**
 * Owns the loader-neutral placeable items for Etherology's Attrahite block family.
 */
public final class SharedAttrahiteBlockItems {

    private static final SharedDeferredRegister<Item> ITEMS =
            SharedDeferredRegister.create(RegistryKeys.ITEM);

    /** Supplies the placeable item for raw Attrahite without defining a generic block drop. */
    public static final RegistrySupplier<BlockItem> ATTRAHITE_ITEM = registerBlockItem(
            "attrahite",
            SharedAttrahiteBlocks.ATTRAHITE
    );

    /** Supplies the placeable item for the full Attrahite brick block. */
    public static final RegistrySupplier<BlockItem> ATTRAHITE_BRICKS_ITEM = registerBlockItem(
            "attrahite_bricks",
            SharedAttrahiteBlocks.ATTRAHITE_BRICKS
    );

    /** Supplies the placeable item for the Attrahite brick slab. */
    public static final RegistrySupplier<BlockItem> ATTRAHITE_BRICK_SLAB_ITEM = registerBlockItem(
            "attrahite_brick_slab",
            SharedAttrahiteBlocks.ATTRAHITE_BRICK_SLAB
    );

    /** Supplies the placeable item for the Attrahite brick stairs. */
    public static final RegistrySupplier<BlockItem> ATTRAHITE_BRICK_STAIRS_ITEM = registerBlockItem(
            "attrahite_brick_stairs",
            SharedAttrahiteBlocks.ATTRAHITE_BRICK_STAIRS
    );

    private SharedAttrahiteBlockItems() {
    }

    private static RegistrySupplier<BlockItem> registerBlockItem(
            String id,
            RegistrySupplier<? extends Block> block
    ) {
        return ITEMS.register(id, () -> {
            BlockItem blockItem = new BlockItem(block.get(), new Item.Settings());
            blockItem.appendBlocks(Item.BLOCK_ITEMS, blockItem);
            return blockItem;
        });
    }

    /**
     * Attaches this item registry exactly once after the shared Attrahite blocks.
     */
    public static void register() {
        ITEMS.attach();
    }
}
