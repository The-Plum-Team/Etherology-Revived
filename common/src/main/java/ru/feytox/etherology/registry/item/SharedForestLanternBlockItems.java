package ru.feytox.etherology.registry.item;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.registry.SharedDeferredRegister;
import ru.feytox.etherology.registry.block.SharedForestLanternBlocks;

/**
 * Owns the loader-neutral placeable item for the Forest Lantern.
 */
public final class SharedForestLanternBlockItems {

    private static final SharedDeferredRegister<Item> ITEMS =
            SharedDeferredRegister.create(RegistryKeys.ITEM);

    /** Supplies the placeable Forest Lantern item after the item registry event. */
    public static final RegistrySupplier<BlockItem> FOREST_LANTERN_ITEM = ITEMS.register(
            "forest_lantern",
            () -> {
                BlockItem blockItem = new BlockItem(
                        SharedForestLanternBlocks.FOREST_LANTERN.get(),
                        new Item.Settings()
                );
                blockItem.appendBlocks(Item.BLOCK_ITEMS, blockItem);
                return blockItem;
            }
    );

    private SharedForestLanternBlockItems() {
    }

    /**
     * Attaches the Forest Lantern item registry exactly once after its block registry.
     */
    public static void register() {
        ITEMS.attach();
    }
}
