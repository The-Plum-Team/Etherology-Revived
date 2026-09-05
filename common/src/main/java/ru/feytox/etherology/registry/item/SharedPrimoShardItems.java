package ru.feytox.etherology.registry.item;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.item.PrimoShard;
import ru.feytox.etherology.magic.seal.SealType;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns the loader-neutral registrations for Etherology's four canonical Primoshards.
 */
public final class SharedPrimoShardItems {

    private static final SharedDeferredRegister<Item> ITEMS =
            SharedDeferredRegister.create(RegistryKeys.ITEM);

    /** Supplies the Keta-aspect Primoshard. */
    public static final RegistrySupplier<PrimoShard> PRIMOSHARD_KETA =
            ITEMS.register(
                    "primoshard_keta",
                    () -> new PrimoShard(SealType.KETA)
            );

    /** Supplies the Rella-aspect Primoshard. */
    public static final RegistrySupplier<PrimoShard> PRIMOSHARD_RELLA =
            ITEMS.register(
                    "primoshard_rella",
                    () -> new PrimoShard(SealType.RELLA)
            );

    /** Supplies the Clos-aspect Primoshard. */
    public static final RegistrySupplier<PrimoShard> PRIMOSHARD_CLOS =
            ITEMS.register(
                    "primoshard_clos",
                    () -> new PrimoShard(SealType.CLOS)
            );

    /** Supplies the Via-aspect Primoshard. */
    public static final RegistrySupplier<PrimoShard> PRIMOSHARD_VIA =
            ITEMS.register(
                    "primoshard_via",
                    () -> new PrimoShard(SealType.VIA)
            );

    private SharedPrimoShardItems() {
    }

    /**
     * Attaches this Primoshard registry exactly once during loader construction.
     */
    public static void register() {
        ITEMS.attach();
    }
}
