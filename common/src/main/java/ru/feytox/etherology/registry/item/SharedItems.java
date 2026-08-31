package ru.feytox.etherology.registry.item;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.item.EtherealStorageInputItem;
import ru.feytox.etherology.registry.SharedDeferredRegister;
import ru.feytox.etherology.registry.block.SharedBlocks;

/**
 * Owns the loader-neutral item registrations that have been ported to every active loader.
 */
public final class SharedItems {

    private static final SharedDeferredRegister<Item> ITEMS =
            SharedDeferredRegister.create(RegistryKeys.ITEM);

    /**
     * Supplies the basic Ether ingredient after the active loader registers the item registry.
     */
    public static final RegistrySupplier<Item> ETHER = ITEMS.register(
            "ether",
            () -> new Item(new Item.Settings())
    );

    /**
     * Supplies the exact glint-shard ID used by the bounded storage input contract.
     */
    public static final RegistrySupplier<EtherealStorageInputItem> GLINT_SHARD = ITEMS.register(
            "glint_shard",
            EtherealStorageInputItem::new
    );

    /**
     * Supplies the placeable item for the shared persistent ethereal-storage block.
     */
    public static final RegistrySupplier<BlockItem> ETHEREAL_STORAGE_ITEM = ITEMS.register(
            "ethereal_storage",
            () -> new BlockItem(SharedBlocks.ETHEREAL_STORAGE.get(), new Item.Settings())
    );

    private SharedItems() {
    }

    /**
     * Attaches this registry exactly once during loader construction, before registry events run.
     */
    public static void register() {
        ITEMS.attach();
    }
}
