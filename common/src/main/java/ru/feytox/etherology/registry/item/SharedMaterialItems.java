package ru.feytox.etherology.registry.item;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns the loader-neutral registrations for Etherology's behavior-free material items.
 */
public final class SharedMaterialItems {

    private static final SharedDeferredRegister<Item> ITEMS =
            SharedDeferredRegister.create(RegistryKeys.ITEM);

    /** Supplies the Etheroscope crafting material. */
    public static final RegistrySupplier<Item> ETHEROSCOPE = registerSimple("etheroscope");

    /** Supplies the thuja-oil crafting and generator fuel item. */
    public static final RegistrySupplier<Item> THUJA_OIL = registerSimple("thuja_oil");

    /** Supplies the azel ingot. */
    public static final RegistrySupplier<Item> AZEL_INGOT = registerSimple("azel_ingot");

    /** Supplies the azel nugget. */
    public static final RegistrySupplier<Item> AZEL_NUGGET = registerSimple("azel_nugget");

    /** Supplies the ethril ingot. */
    public static final RegistrySupplier<Item> ETHRIL_INGOT = registerSimple("ethril_ingot");

    /** Supplies the ethril nugget. */
    public static final RegistrySupplier<Item> ETHRIL_NUGGET = registerSimple("ethril_nugget");

    /** Supplies the ebony ingot. */
    public static final RegistrySupplier<Item> EBONY_INGOT = registerSimple("ebony_ingot");

    /** Supplies the ebony nugget. */
    public static final RegistrySupplier<Item> EBONY_NUGGET = registerSimple("ebony_nugget");

    /** Supplies enriched attrahite with its canonical stack limit. */
    public static final RegistrySupplier<Item> ENRICHED_ATTRAHITE = ITEMS.register(
            "enriched_attrahite",
            () -> new Item(new Item.Settings().maxCount(16))
    );

    /** Supplies raw azel. */
    public static final RegistrySupplier<Item> RAW_AZEL = registerSimple("raw_azel");

    /** Supplies an attrahite brick. */
    public static final RegistrySupplier<Item> ATTRAHITE_BRICK = registerSimple("attrahite_brick");

    /** Supplies the alchemical binder. */
    public static final RegistrySupplier<Item> BINDER = registerSimple("binder");

    /** Supplies raw ebony. */
    public static final RegistrySupplier<Item> EBONY = registerSimple("ebony");

    /** Supplies the resonating wand crafting material. */
    public static final RegistrySupplier<Item> RESONATING_WAND = registerSimple("resonating_wand");

    private SharedMaterialItems() {
    }

    private static RegistrySupplier<Item> registerSimple(String id) {
        return ITEMS.register(id, () -> new Item(new Item.Settings()));
    }

    /**
     * Attaches this material registry exactly once during loader construction.
     */
    public static void register() {
        ITEMS.attach();
    }
}
