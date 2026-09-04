package ru.feytox.etherology.registry.item;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.registry.SharedDeferredRegister;
import ru.feytox.etherology.registry.block.SharedPedestalBlocks;

/**
 * Owns the loader-neutral placeable Pedestal item.
 */
public final class SharedPedestalBlockItems {

    private static final SharedDeferredRegister<Item> ITEMS =
            SharedDeferredRegister.create(RegistryKeys.ITEM);

    /** Supplies the placeable Pedestal item after block registration. */
    public static final RegistrySupplier<BlockItem> PEDESTAL_ITEM = ITEMS.register(
            "pedestal",
            () -> {
                BlockItem blockItem = new BlockItem(
                        SharedPedestalBlocks.PEDESTAL.get(),
                        new Item.Settings()
                );
                blockItem.appendBlocks(Item.BLOCK_ITEMS, blockItem);
                return blockItem;
            }
    );

    private SharedPedestalBlockItems() {
    }

    /** Attaches the Pedestal item registry exactly once after its block registry. */
    public static void register() {
        ITEMS.attach();
    }
}
