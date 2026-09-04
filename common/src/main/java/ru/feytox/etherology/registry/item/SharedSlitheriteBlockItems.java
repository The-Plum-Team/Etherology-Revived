package ru.feytox.etherology.registry.item;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.registry.SharedDeferredRegister;
import ru.feytox.etherology.registry.block.SharedSlitheriteBlocks;

/**
 * Owns the loader-neutral placeable items for Etherology's Slitherite family.
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

    /** Supplies the placeable item for the full polished Slitherite block. */
    public static final RegistrySupplier<BlockItem> POLISHED_SLITHERITE_ITEM =
            registerBlockItem(
                    "polished_slitherite",
                    SharedSlitheriteBlocks.POLISHED_SLITHERITE
            );

    /** Supplies the placeable item for the polished Slitherite stairs. */
    public static final RegistrySupplier<BlockItem> POLISHED_SLITHERITE_STAIRS_ITEM =
            registerBlockItem(
                    "polished_slitherite_stairs",
                    SharedSlitheriteBlocks.POLISHED_SLITHERITE_STAIRS
            );

    /** Supplies the placeable item for the polished Slitherite slab. */
    public static final RegistrySupplier<BlockItem> POLISHED_SLITHERITE_SLAB_ITEM =
            registerBlockItem(
                    "polished_slitherite_slab",
                    SharedSlitheriteBlocks.POLISHED_SLITHERITE_SLAB
            );

    /** Supplies the placeable item for the polished Slitherite wall. */
    public static final RegistrySupplier<BlockItem> POLISHED_SLITHERITE_WALL_ITEM =
            registerBlockItem(
                    "polished_slitherite_wall",
                    SharedSlitheriteBlocks.POLISHED_SLITHERITE_WALL
            );

    /** Supplies the placeable item for the polished Slitherite button. */
    public static final RegistrySupplier<BlockItem> POLISHED_SLITHERITE_BUTTON_ITEM =
            registerBlockItem(
                    "polished_slitherite_button",
                    SharedSlitheriteBlocks.POLISHED_SLITHERITE_BUTTON
            );

    /** Supplies the placeable item for the polished Slitherite pressure plate. */
    public static final RegistrySupplier<BlockItem> POLISHED_SLITHERITE_PRESSURE_PLATE_ITEM =
            registerBlockItem(
                    "polished_slitherite_pressure_plate",
                    SharedSlitheriteBlocks.POLISHED_SLITHERITE_PRESSURE_PLATE
            );

    /** Supplies the placeable item for the full polished Slitherite brick block. */
    public static final RegistrySupplier<BlockItem> POLISHED_SLITHERITE_BRICKS_ITEM =
            registerBlockItem(
                    "polished_slitherite_bricks",
                    SharedSlitheriteBlocks.POLISHED_SLITHERITE_BRICKS
            );

    /** Supplies the placeable item for the polished Slitherite brick stairs. */
    public static final RegistrySupplier<BlockItem> POLISHED_SLITHERITE_BRICK_STAIRS_ITEM =
            registerBlockItem(
                    "polished_slitherite_brick_stairs",
                    SharedSlitheriteBlocks.POLISHED_SLITHERITE_BRICK_STAIRS
            );

    /** Supplies the placeable item for the polished Slitherite brick slab. */
    public static final RegistrySupplier<BlockItem> POLISHED_SLITHERITE_BRICK_SLAB_ITEM =
            registerBlockItem(
                    "polished_slitherite_brick_slab",
                    SharedSlitheriteBlocks.POLISHED_SLITHERITE_BRICK_SLAB
            );

    /** Supplies the placeable item for the polished Slitherite brick wall. */
    public static final RegistrySupplier<BlockItem> POLISHED_SLITHERITE_BRICK_WALL_ITEM =
            registerBlockItem(
                    "polished_slitherite_brick_wall",
                    SharedSlitheriteBlocks.POLISHED_SLITHERITE_BRICK_WALL
            );

    /** Supplies the placeable item for the chiseled polished Slitherite block. */
    public static final RegistrySupplier<BlockItem> CHISELED_POLISHED_SLITHERITE_ITEM =
            registerBlockItem(
                    "chiseled_polished_slitherite",
                    SharedSlitheriteBlocks.CHISELED_POLISHED_SLITHERITE
            );

    /** Supplies the placeable item for the chiseled polished Slitherite brick block. */
    public static final RegistrySupplier<BlockItem>
            CHISELED_POLISHED_SLITHERITE_BRICKS_ITEM = registerBlockItem(
                    "chiseled_polished_slitherite_bricks",
                    SharedSlitheriteBlocks.CHISELED_POLISHED_SLITHERITE_BRICKS
            );

    /** Supplies the placeable item for the cracked polished Slitherite brick block. */
    public static final RegistrySupplier<BlockItem>
            CRACKED_POLISHED_SLITHERITE_BRICKS_ITEM = registerBlockItem(
                    "cracked_polished_slitherite_bricks",
                    SharedSlitheriteBlocks.CRACKED_POLISHED_SLITHERITE_BRICKS
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
