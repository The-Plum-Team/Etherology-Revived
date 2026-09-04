package ru.feytox.etherology.recipes;

import net.minecraft.inventory.Inventory;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeType;

/**
 * Derives an Etherology recipe's type from its canonical serializer.
 */
public interface FeyInputRecipe<T extends Inventory> extends Recipe<T> {

    @Override
    FeyRecipeSerializer<?> getSerializer();

    @Override
    default RecipeType<?> getType() {
        return getSerializer().getRecipeType();
    }
}
