package ru.feytox.etherology.registry.item;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.item.UnadjustedLens;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns loader-neutral registrations for Etherology's canonical lens items.
 */
public final class SharedLensItems {

    private static final SharedDeferredRegister<Item> ITEMS =
            SharedDeferredRegister.create(RegistryKeys.ITEM);

    /** Supplies the canonical unadjusted lens subtype. */
    public static final RegistrySupplier<UnadjustedLens> UNADJUSTED_LENS =
            ITEMS.register("unadjusted_lens", () -> new UnadjustedLens());

    private SharedLensItems() {
    }

    /**
     * Attaches this lens-item registry exactly once during loader construction.
     */
    public static void register() {
        ITEMS.attach();
    }
}
