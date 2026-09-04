package ru.feytox.etherology.recipes;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;

/**
 * Owns the stable identifier, recipe type, data provider, and result-stack envelope
 * shared by Etherology's custom recipe serializers.
 */
public abstract class FeyRecipeSerializer<T extends Recipe<?>>
        implements RecipeSerializer<T> {

    private final Identifier id;
    private volatile RecipeType<T> recipeType;

    /**
     * Creates a serializer and recipe type under Etherology's namespace.
     */
    public FeyRecipeSerializer(String id) {
        this.id = new Identifier("etherology", id);
    }

    /**
     * Returns the registry identifier shared by this serializer and its recipe type.
     */
    public Identifier getId() {
        return id;
    }

    /**
     * Returns the single lazily created recipe type owned by this serializer.
     */
    public RecipeType<T> getRecipeType() {
        RecipeType<T> result = recipeType;
        if (result != null) return result;

        synchronized (this) {
            result = recipeType;
            if (result == null) {
                result = createType();
                recipeType = result;
            }
        }
        return result;
    }

    private RecipeType<T> createType() {
        return new RecipeType<>() {
            @Override
            public String toString() {
                return id.toString();
            }
        };
    }

    /**
     * Wraps a recipe for data generation without creating an advancement.
     */
    public final RecipeJsonProvider toProvider(T recipe) {
        return new FeyRecipeJsonProvider<>(recipe, this);
    }

    protected abstract void writeJson(JsonObject json, T recipe);

    protected static ItemStack readItemStack(JsonObject json, String fieldName) {
        JsonObject stackJson = JsonHelper.getObject(json, fieldName);
        Item item = readItem(stackJson, "id");
        int count = JsonHelper.getInt(stackJson, "count", 1);
        if (count <= 0) {
            throw new JsonSyntaxException(
                    "Expected a positive item count in '"
                            + fieldName
                            + "', found "
                            + count
            );
        }

        ItemStack stack = new ItemStack(item, count);
        RecipeResultComponents.read(stackJson, stack);
        return stack;
    }

    protected static void writeItemStack(
            JsonObject json,
            String fieldName,
            ItemStack stack
    ) {
        JsonObject stackJson = new JsonObject();
        RecipeResultComponents.write(stackJson, stack);
        stackJson.addProperty("count", stack.getCount());
        stackJson.addProperty(
                "id",
                Registries.ITEM.getId(stack.getItem()).toString()
        );
        json.add(fieldName, stackJson);
    }

    protected static Item readItem(JsonObject json, String fieldName) {
        Identifier itemId = new Identifier(JsonHelper.getString(json, fieldName));
        return Registries.ITEM.getOrEmpty(itemId)
                .orElseThrow(() -> new JsonSyntaxException(
                        "No such item " + itemId
                ));
    }
}
