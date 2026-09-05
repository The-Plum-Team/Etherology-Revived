package ru.feytox.etherology.registry.item;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.registry.SharedDeferredRegister;
import ru.feytox.etherology.registry.misc.EtherToolMaterials;

/**
 * Owns loader-neutral registrations for Etherology tools with vanilla item behavior.
 */
public final class SharedToolItems {

    private static final SharedDeferredRegister<Item> ITEMS =
            SharedDeferredRegister.create(RegistryKeys.ITEM);

    /** Supplies the Warp Counter with its canonical single-item stack limit. */
    public static final RegistrySupplier<Item> WARP_COUNTER = ITEMS.register(
            "warp_counter",
            () -> new Item(new Item.Settings().maxCount(1))
    );

    public static final RegistrySupplier<Item> EBONY_AXE = ITEMS.register(
            "ebony_axe",
            () -> new AxeItem(EtherToolMaterials.EBONY, 5, -3.1F, new Item.Settings())
    );
    public static final RegistrySupplier<Item> EBONY_PICKAXE = ITEMS.register(
            "ebony_pickaxe",
            () -> new PickaxeItem(EtherToolMaterials.EBONY, 0, -2.8F, new Item.Settings())
    );
    public static final RegistrySupplier<Item> EBONY_HOE = ITEMS.register(
            "ebony_hoe",
            () -> new HoeItem(EtherToolMaterials.EBONY, -2, -1, new Item.Settings())
    );
    public static final RegistrySupplier<Item> EBONY_SHOVEL = ITEMS.register(
            "ebony_shovel",
            () -> new ShovelItem(EtherToolMaterials.EBONY, 0.5F, -3, new Item.Settings())
    );
    public static final RegistrySupplier<Item> EBONY_SWORD = ITEMS.register(
            "ebony_sword",
            () -> new SwordItem(EtherToolMaterials.EBONY, 3, -2.4F, new Item.Settings())
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
