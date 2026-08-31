package ru.feytox.etherology.registry.item;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.registry.SharedDeferredRegister;
import ru.feytox.etherology.registry.block.SharedMetalBlocks;

/**
 * Owns the loader-neutral placeable items for Etherology's shared metal blocks.
 */
public final class SharedMetalBlockItems {

    private static final SharedDeferredRegister<Item> ITEMS =
            SharedDeferredRegister.create(RegistryKeys.ITEM);

    /** Supplies the placeable item for the azel storage block. */
    public static final RegistrySupplier<BlockItem> AZEL_BLOCK_ITEM = registerBlockItem(
            "azel_block",
            SharedMetalBlocks.AZEL_BLOCK
    );

    /** Supplies the placeable item for the ethril storage block. */
    public static final RegistrySupplier<BlockItem> ETHRIL_BLOCK_ITEM = registerBlockItem(
            "ethril_block",
            SharedMetalBlocks.ETHRIL_BLOCK
    );

    /** Supplies the placeable item for the ebony storage block. */
    public static final RegistrySupplier<BlockItem> EBONY_BLOCK_ITEM = registerBlockItem(
            "ebony_block",
            SharedMetalBlocks.EBONY_BLOCK
    );

    private SharedMetalBlockItems() {
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
     * Attaches this item registry exactly once after the shared metal blocks.
     */
    public static void register() {
        ITEMS.attach();
    }
}
