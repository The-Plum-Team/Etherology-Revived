package ru.feytox.etherology.data.ethersource;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

/**
 * Resolves the server-data Ether fuel value assigned to registered items.
 */
public final class EtherSources {

    private EtherSources() {
    }

    /**
     * Returns the current data-pack fuel value, or zero when the item has no Ether-source entry.
     *
     * @param item registered item to resolve
     * @return the configured Ether fuel value
     */
    public static float getEtherFuel(Item item) {
        return EtherSourceLoader.INSTANCE.getEtherItems().getOrDefault(
                Registries.ITEM.getId(item),
                0.0F
        );
    }

    /**
     * Reports whether the current server-data reload declared the item as an Ether source.
     *
     * @param item registered item to inspect
     * @return true when the item has a configured Ether fuel entry
     */
    public static boolean isEtherSource(Item item) {
        return EtherSourceLoader.INSTANCE.getEtherItems().containsKey(
                Registries.ITEM.getId(item)
        );
    }
}
