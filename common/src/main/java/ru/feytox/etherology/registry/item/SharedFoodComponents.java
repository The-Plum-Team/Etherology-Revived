package ru.feytox.etherology.registry.item;

import net.minecraft.item.FoodComponent;

/**
 * Defines loader-neutral food properties for Etherology items.
 */
public final class SharedFoodComponents {

    /** Supplies the canonical nutrition for a forest lantern crumb. */
    public static final FoodComponent FOREST_LANTERN_CRUMB = new FoodComponent.Builder()
            .hunger(3)
            .saturationModifier(2.0f)
            .build();

    private SharedFoodComponents() {
    }
}
