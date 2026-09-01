package ru.feytox.etherology.registry.item;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.registry.SharedDeferredRegister;
import ru.feytox.etherology.registry.block.SharedSlitheriteBlocks;

/**
 * Owns the loader-neutral placeable items for Etherology's base Slitherite family.
 */
public final class SharedSlitheriteBlockItems {

    private static final SharedDeferredRegister<Item> ITEMS =
            SharedDeferredRegister.create(RegistryKeys.ITEM);

    /** Supplies the placeable item for the full Slitherite block. */
    public static final RegistrySupplier<BlockItem> SLITHERITE_ITEM = registerBlockItem(
            "slitherite",
            SharedSlitheriteBlocks.SLITHERITE
    );

    /** Supplies the placeable item for the Slitherite stairs. */
    public static final RegistrySupplier<BlockItem> SLITHERITE_STAIRS_ITEM = registerBlockItem(
            "slitherite_stairs",
            SharedSlitheriteBlocks.SLITHERITE_STAIRS
    );

    /** Supplies the placeable item for the Slitherite slab. */
    public static final RegistrySupplier<BlockItem> SLITHERITE_SLAB_ITEM = registerBlockItem(
            "slitherite_slab",
            SharedSlitheriteBlocks.SLITHERITE_SLAB
    );

    /** Supplies the placeable item for the Slitherite wall. */
    public static final RegistrySupplier<BlockItem> SLITHERITE_WALL_ITEM = registerBlockItem(
            "slitherite_wall",
            SharedSlitheriteBlocks.SLITHERITE_WALL
    );

    private SharedSlitheriteBlockItems() {
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
     * Attaches this item registry exactly once after the shared Slitherite blocks.
     */
    public static void register() {
        ITEMS.attach();
    }
}
