package ru.feytox.etherology.recipes;

import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;

/**
 * Translates only the version-owned component payload of a recipe result stack.
 */
public interface RecipeResultComponentBackend {

    /**
     * Applies every serialized result component to the newly created stack.
     */
    void readComponents(JsonObject components, ItemStack stack);

    /**
     * Adds every component carried by the result stack to the serialized payload.
     */
    void writeComponents(JsonObject components, ItemStack stack);
}
