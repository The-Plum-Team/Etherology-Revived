package ru.feytox.etherology.registry.item;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns loader-neutral registrations for Etherology's behavior-free tool items.
 */
public final class SharedToolItems {

    private static final SharedDeferredRegister<Item> ITEMS =
            SharedDeferredRegister.create(RegistryKeys.ITEM);

    /** Supplies the Warp Counter with its canonical single-item stack limit. */
    public static final RegistrySupplier<Item> WARP_COUNTER = ITEMS.register(
            "warp_counter",
            () -> new Item(new Item.Settings().maxCount(1))
    );

    private SharedToolItems() {
    }

    /**
     * Attaches this tool-item registry exactly once during loader construction.
     */
    public static void register() {
        ITEMS.attach();
    }
}
