package ru.feytox.etherology.recipes;

import net.minecraft.inventory.Inventory;

/**
 * Marks Etherology recipes whose input is a vanilla inventory.
 */
public interface FeyRecipe<T extends Inventory> extends FeyInputRecipe<T> {
}
