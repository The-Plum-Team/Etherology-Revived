package ru.feytox.etherology.recipes;

import com.google.gson.JsonObject;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.feytox.etherology.magic.aspects.Aspect;
import ru.feytox.etherology.magic.aspects.AspectContainer;
import ru.feytox.etherology.recipes.alchemy.AlchemyRecipe;
import ru.feytox.etherology.recipes.alchemy.AlchemyRecipeSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class AlchemyRecipeFoundationContractTest {

    private static final Identifier RECIPE_ID =
            new Identifier("etherology", "contract_alchemy");
    private static final TestResultComponentBackend RESULT_COMPONENT_BACKEND =
            new TestResultComponentBackend();

    @BeforeAll
    static void initializeComponentBackend() {
        RecipeResultComponents.bind(RESULT_COMPONENT_BACKEND);
    }

    @Test
    void recipeTypeSerializerAndProviderKeepOneCanonicalIdentity() {
        AlchemyRecipeSerializer serializer = AlchemyRecipeSerializer.INSTANCE;
        assertEquals(
                new Identifier("etherology", "alchemy_recipe"),
                serializer.getId()
        );
        assertSame(serializer.getRecipeType(), serializer.getRecipeType());
        assertEquals("etherology:alchemy_recipe", serializer.getRecipeType().toString());

        AspectContainer aspects = requiredAspects();
        AlchemyRecipe recipe = new AlchemyRecipe(
                null,
                2,
                aspects,
                null,
                RECIPE_ID
        );
        assertSame(serializer, recipe.getSerializer());
        assertSame(serializer.getRecipeType(), recipe.getType());
        assertNull(recipe.getInputItem());
        assertEquals(2, recipe.getInputAmount());
        assertSame(aspects, recipe.getInputAspects());
        assertEquals(RECIPE_ID, recipe.getId());
        assertFalse(recipe.fits(1, 1));
        assertFalse(recipe.fits(99, 99));
        assertNotEquals(
                recipe,
                new AlchemyRecipe(null, 2, aspects, null, RECIPE_ID)
        );

        RecipeJsonProvider provider = serializer.toProvider(recipe);
        assertSame(serializer, provider.getSerializer());
        assertEquals(RECIPE_ID, provider.getRecipeId());
        assertNull(provider.toAdvancementJson());
        assertNull(provider.getAdvancementId());
    }

    @Test
    void componentBackendBindingIsSingleOwnerAndDelegatesBothDirections() {
        assertThrows(
                NullPointerException.class,
                () -> RecipeResultComponents.bind(null)
        );
        RecipeResultComponents.bind(RESULT_COMPONENT_BACKEND);
        assertThrows(
                IllegalStateException.class,
                () -> RecipeResultComponents.bind(
                        new TestResultComponentBackend()
                )
        );

        JsonObject encodedStack = new JsonObject();
        RecipeResultComponents.write(encodedStack, null);
        assertEquals(
                1,
                encodedStack.getAsJsonObject("components")
                        .get("test:writes")
                        .getAsInt()
        );

        JsonObject decodedStack = new JsonObject();
        JsonObject decodedComponents = new JsonObject();
        decodedComponents.addProperty("test:read", true);
        decodedStack.add("components", decodedComponents);
        RecipeResultComponents.read(decodedStack, null);
        assertEquals(1, RESULT_COMPONENT_BACKEND.reads);
    }

    private static AspectContainer requiredAspects() {
        Map<Aspect, Integer> aspects = new LinkedHashMap<>();
        aspects.put(Aspect.RELLA, 3);
        aspects.put(Aspect.ETHA, 1);
        return new AspectContainer(aspects);
    }

    private static final class TestResultComponentBackend
            implements RecipeResultComponentBackend {

        private int reads;
        private int writes;

        @Override
        public void readComponents(
                JsonObject components,
                net.minecraft.item.ItemStack stack
        ) {
            reads++;
        }

        @Override
        public void writeComponents(
                JsonObject components,
                net.minecraft.item.ItemStack stack
        ) {
            writes++;
            components.addProperty("test:writes", writes);
        }
    }
}
