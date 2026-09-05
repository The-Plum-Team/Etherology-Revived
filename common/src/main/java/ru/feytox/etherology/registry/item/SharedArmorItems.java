package ru.feytox.etherology.registry.item;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.item.EbonyArmorItem;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/** Owns the Ebony armor registrations without resolving material repair ingredients. */
public final class SharedArmorItems {

    private static final SharedDeferredRegister<Item> ITEMS =
            SharedDeferredRegister.create(RegistryKeys.ITEM);

    /** Supplies the head-slot piece with its independent movement-speed modifier. */
    public static final RegistrySupplier<EbonyArmorItem> EBONY_HELMET = ITEMS.register(
            "ebony_helmet", () -> new EbonyArmorItem(
                    EbonyArmorMaterial.INSTANCE, ArmorItem.Type.HELMET, new Item.Settings()));

    /** Supplies the chest-slot piece with its independent movement-speed modifier. */
    public static final RegistrySupplier<EbonyArmorItem> EBONY_CHESTPLATE = ITEMS.register(
            "ebony_chestplate", () -> new EbonyArmorItem(
                    EbonyArmorMaterial.INSTANCE, ArmorItem.Type.CHESTPLATE, new Item.Settings()));

    /** Supplies the legs-slot piece with its independent movement-speed modifier. */
    public static final RegistrySupplier<EbonyArmorItem> EBONY_LEGGINGS = ITEMS.register(
            "ebony_leggings", () -> new EbonyArmorItem(
                    EbonyArmorMaterial.INSTANCE, ArmorItem.Type.LEGGINGS, new Item.Settings()));

    /** Supplies the feet-slot piece with its independent movement-speed modifier. */
    public static final RegistrySupplier<EbonyArmorItem> EBONY_BOOTS = ITEMS.register(
            "ebony_boots", () -> new EbonyArmorItem(
                    EbonyArmorMaterial.INSTANCE, ArmorItem.Type.BOOTS, new Item.Settings()));

    private SharedArmorItems() {
    }

    /** Attaches all four declarations once before loader registry events. */
    public static void register() {
        ITEMS.attach();
    }
}
