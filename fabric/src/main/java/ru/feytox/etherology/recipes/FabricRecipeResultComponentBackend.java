package ru.feytox.etherology.recipes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import net.minecraft.item.ItemStack;
import ru.feytox.etherology.magic.staff.StaffComponent;
import ru.feytox.etherology.registry.misc.ComponentTypes;

/**
 * Translates Etherology's 1.20.1 item-data staff component in recipe results.
 */
public final class FabricRecipeResultComponentBackend
        implements RecipeResultComponentBackend {

    public static final FabricRecipeResultComponentBackend INSTANCE =
            new FabricRecipeResultComponentBackend();

    private FabricRecipeResultComponentBackend() {
    }

    @Override
    public void readComponents(JsonObject components, ItemStack stack) {
        for (String componentId : components.keySet()) {
            if (!componentId.equals("etherology:staff")) {
                throw new JsonSyntaxException(
                        "Unsupported 1.20.1 recipe result component '"
                                + componentId
                                + "'"
                );
            }

            StaffComponent component = StaffComponent.CODEC.parse(
                    JsonOps.INSTANCE,
                    components.get(componentId)
            ).getOrThrow(false, message -> {
                throw new JsonSyntaxException(
                        "Invalid staff recipe result component: " + message
                );
            });
            ComponentTypes.STAFF.set(stack, component);
        }
    }

    @Override
    public void writeComponents(JsonObject components, ItemStack stack) {
        ComponentTypes.STAFF.get(stack).ifPresent(component -> {
            JsonElement componentJson = StaffComponent.CODEC.encodeStart(
                    JsonOps.INSTANCE,
                    component
            ).getOrThrow(false, message -> {
                throw new IllegalStateException(
                        "Could not encode staff recipe result component: " + message
                );
            });
            components.add("etherology:staff", componentJson);
        });
    }
}
