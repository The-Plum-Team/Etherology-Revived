package ru.feytox.etherology.recipes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import lombok.Getter;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import ru.feytox.etherology.magic.staff.StaffComponent;
import ru.feytox.etherology.registry.misc.ComponentTypes;
import ru.feytox.etherology.util.misc.EIdentifier;

public abstract class FeyRecipeSerializer<T extends Recipe<?>> implements RecipeSerializer<T> {

    @Getter
    private final Identifier id;
    @Getter(lazy = true)
    private final RecipeType<T> recipeType = createType();

    public FeyRecipeSerializer(String id) {
        this.id = EIdentifier.of(id);
    }

    private RecipeType<T> createType() {
        return new RecipeType<>() {
            @Override
            public String toString() {
                return id.toString();
            }
        };
    }

    public final RecipeJsonProvider toProvider(T recipe) {
        return new FeyRecipeJsonProvider<>(recipe, this);
    }

    protected abstract void writeJson(JsonObject json, T recipe);

    protected static ItemStack readItemStack(JsonObject json, String fieldName) {
        JsonObject stackJson = JsonHelper.getObject(json, fieldName);
        Item item = readItem(stackJson, "id");
        int count = JsonHelper.getInt(stackJson, "count", 1);
        if (count <= 0) {
            throw new JsonSyntaxException("Expected a positive item count in '" + fieldName + "', found " + count);
        }

        ItemStack stack = new ItemStack(item, count);
        if (!stackJson.has("components")) return stack;

        JsonObject components = JsonHelper.getObject(stackJson, "components");
        for (String componentId : components.keySet()) {
            if (!componentId.equals("etherology:staff")) {
                throw new JsonSyntaxException("Unsupported 1.20.1 recipe result component '" + componentId + "'");
            }

            StaffComponent component = StaffComponent.CODEC.parse(JsonOps.INSTANCE, components.get(componentId))
                    .getOrThrow(false, message -> {
                        throw new JsonSyntaxException("Invalid staff recipe result component: " + message);
                    });
            ComponentTypes.STAFF.set(stack, component);
        }
        return stack;
    }

    protected static void writeItemStack(JsonObject json, String fieldName, ItemStack stack) {
        JsonObject stackJson = new JsonObject();
        ComponentTypes.STAFF.get(stack).ifPresent(component -> {
            JsonElement componentJson = StaffComponent.CODEC.encodeStart(JsonOps.INSTANCE, component)
                    .getOrThrow(false, message -> {
                        throw new IllegalStateException("Could not encode staff recipe result component: " + message);
                    });
            JsonObject components = new JsonObject();
            components.add("etherology:staff", componentJson);
            stackJson.add("components", components);
        });
        stackJson.addProperty("count", stack.getCount());
        stackJson.addProperty("id", Registries.ITEM.getId(stack.getItem()).toString());
        json.add(fieldName, stackJson);
    }

    protected static Item readItem(JsonObject json, String fieldName) {
        Identifier itemId = new Identifier(JsonHelper.getString(json, fieldName));
        return Registries.ITEM.getOrEmpty(itemId)
                .orElseThrow(() -> new JsonSyntaxException("No such item " + itemId));
    }
}
