package ru.feytox.etherology.client.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

final class NamespacedRecipeJsonProvider implements RecipeJsonProvider {

    private final RecipeJsonProvider delegate;
    private final Identifier sourceRecipeId;
    private final Identifier recipeId;

    NamespacedRecipeJsonProvider(RecipeJsonProvider delegate, Identifier recipeId) {
        this.delegate = delegate;
        this.sourceRecipeId = delegate.getRecipeId();
        this.recipeId = recipeId;
    }

    @Override
    public void serialize(JsonObject json) {
        delegate.serialize(json);
    }

    @Override
    public Identifier getRecipeId() {
        return recipeId;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return delegate.getSerializer();
    }

    @Nullable
    @Override
    public JsonObject toAdvancementJson() {
        JsonObject advancementJson = delegate.toAdvancementJson();
        if (advancementJson == null || sourceRecipeId.equals(recipeId)) return advancementJson;

        boolean hasCriterionReference = normalizeCriterionRecipeIds(advancementJson);
        boolean hasRewardReference = normalizeRewardRecipeIds(advancementJson);
        if (!hasCriterionReference || !hasRewardReference) {
            throw new IllegalStateException("Recipe advancement does not reference " + sourceRecipeId);
        }
        return advancementJson;
    }

    @Nullable
    @Override
    public Identifier getAdvancementId() {
        Identifier advancementId = delegate.getAdvancementId();
        return advancementId == null ? null : new Identifier(recipeId.getNamespace(), advancementId.getPath());
    }

    private boolean normalizeCriterionRecipeIds(JsonObject advancementJson) {
        JsonObject criteria = advancementJson.getAsJsonObject("criteria");
        if (criteria == null) return false;

        boolean foundRecipeReference = false;
        for (Map.Entry<String, JsonElement> entry : criteria.entrySet()) {
            JsonObject criterion = entry.getValue().getAsJsonObject();
            JsonObject conditions = criterion.getAsJsonObject("conditions");
            if (conditions != null) foundRecipeReference |= normalizeRecipeId(conditions, "recipe");
        }
        return foundRecipeReference;
    }

    private boolean normalizeRewardRecipeIds(JsonObject advancementJson) {
        JsonObject rewards = advancementJson.getAsJsonObject("rewards");
        if (rewards == null) return false;

        JsonArray recipes = rewards.getAsJsonArray("recipes");
        if (recipes == null) return false;

        boolean foundRecipeReference = false;
        for (int index = 0; index < recipes.size(); index++) {
            JsonElement recipe = recipes.get(index);
            if (!recipe.isJsonPrimitive()) continue;

            String recipeIdValue = recipe.getAsString();
            if (sourceRecipeId.toString().equals(recipeIdValue)) {
                recipes.set(index, new JsonPrimitive(recipeId.toString()));
                foundRecipeReference = true;
            } else if (recipeId.toString().equals(recipeIdValue)) {
                foundRecipeReference = true;
            }
        }
        return foundRecipeReference;
    }

    private boolean normalizeRecipeId(JsonObject object, String property) {
        JsonElement value = object.get(property);
        if (value == null || !value.isJsonPrimitive()) return false;

        String recipeIdValue = value.getAsString();
        if (sourceRecipeId.toString().equals(recipeIdValue)) {
            object.addProperty(property, recipeId.toString());
            return true;
        }
        return recipeId.toString().equals(recipeIdValue);
    }
}
