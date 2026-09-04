package ru.feytox.etherology.recipes;

import com.google.gson.JsonObject;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

final class FeyRecipeJsonProvider<T extends Recipe<?>> implements RecipeJsonProvider {

    private final T recipe;
    private final FeyRecipeSerializer<T> serializer;

    FeyRecipeJsonProvider(T recipe, FeyRecipeSerializer<T> serializer) {
        this.recipe = recipe;
        this.serializer = serializer;
    }

    @Override
    public void serialize(JsonObject json) {
        serializer.writeJson(json, recipe);
    }

    @Override
    public Identifier getRecipeId() {
        return recipe.getId();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return serializer;
    }

    @Nullable
    @Override
    public JsonObject toAdvancementJson() {
        return null;
    }

    @Nullable
    @Override
    public Identifier getAdvancementId() {
        return null;
    }
}
