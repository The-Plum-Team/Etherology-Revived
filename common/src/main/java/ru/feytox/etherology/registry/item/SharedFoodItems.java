package ru.feytox.etherology.registry.item;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns the loader-neutral registrations for Etherology's food items.
 */
public final class SharedFoodItems {

    private static final SharedDeferredRegister<Item> ITEMS =
            SharedDeferredRegister.create(RegistryKeys.ITEM);

    /** Supplies the forest lantern crumb with its canonical food properties. */
    public static final RegistrySupplier<Item> FOREST_LANTERN_CRUMB = ITEMS.register(
            "forest_lantern_crumb",
            () -> new Item(new Item.Settings().food(
                    SharedFoodComponents.FOREST_LANTERN_CRUMB
            ))
    );

    private SharedFoodItems() {
    }

    /**
     * Attaches this food-item registry exactly once during loader construction.
     */
    public static void register() {
        ITEMS.attach();
    }
}
