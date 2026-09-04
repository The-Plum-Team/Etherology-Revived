package ru.feytox.etherology.recipes;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonHelper;

import java.util.Objects;

/**
 * Holds the loader backend for the component portion of recipe result stacks.
 */
public final class RecipeResultComponents {

    private static final RecipeResultComponentBackend UNAVAILABLE =
            new UnavailableBackend();
    private static volatile RecipeResultComponentBackend backend = UNAVAILABLE;

    private RecipeResultComponents() {
    }

    /**
     * Binds one loader backend, allowing an idempotent repeat with the same instance only.
     */
    public static synchronized void bind(RecipeResultComponentBackend backend) {
        Objects.requireNonNull(backend, "backend");
        if (RecipeResultComponents.backend != UNAVAILABLE
                && RecipeResultComponents.backend != backend) {
            throw new IllegalStateException(
                    "A recipe result component backend is already bound"
            );
        }
        RecipeResultComponents.backend = backend;
    }

    static void read(JsonObject stackJson, ItemStack stack) {
        if (!stackJson.has("components")) return;

        backend.readComponents(
                JsonHelper.getObject(stackJson, "components"),
                stack
        );
    }

    static void write(JsonObject stackJson, ItemStack stack) {
        JsonObject components = new JsonObject();
        backend.writeComponents(components, stack);
        if (components.size() > 0) stackJson.add("components", components);
    }

    private static final class UnavailableBackend
            implements RecipeResultComponentBackend {

        @Override
        public void readComponents(JsonObject components, ItemStack stack) {
            String componentId = components.keySet().stream()
                    .findFirst()
                    .orElse("<empty>");
            throw new JsonSyntaxException(
                    "Unsupported 1.20.1 recipe result component '"
                            + componentId
                            + "'"
            );
        }

        @Override
        public void writeComponents(JsonObject components, ItemStack stack) {
            throw new IllegalStateException(
                    "Recipe result components cannot be written before a backend is bound"
            );
        }
    }
}
