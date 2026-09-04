package ru.feytox.etherology.registry.misc;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.recipes.alchemy.AlchemyRecipe;
import ru.feytox.etherology.recipes.alchemy.AlchemyRecipeSerializer;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns the loader-neutral alchemy recipe serializer and type registrations.
 */
public final class SharedAlchemyRecipes {

    private static final String RECIPE_ID = "alchemy_recipe";
    private static final SharedDeferredRegister<RecipeSerializer<?>>
            RECIPE_SERIALIZERS = SharedDeferredRegister.create(
                    RegistryKeys.RECIPE_SERIALIZER
            );
    private static final SharedDeferredRegister<RecipeType<?>> RECIPE_TYPES =
            SharedDeferredRegister.create(RegistryKeys.RECIPE_TYPE);

    /**
     * Supplies the canonical alchemy serializer after recipe serializers register.
     */
    public static final RegistrySupplier<AlchemyRecipeSerializer>
            ALCHEMY_RECIPE_SERIALIZER = RECIPE_SERIALIZERS.register(
                    RECIPE_ID,
                    () -> AlchemyRecipeSerializer.INSTANCE
            );

    /**
     * Supplies the canonical alchemy type after recipe types register.
     */
    public static final RegistrySupplier<RecipeType<AlchemyRecipe>>
            ALCHEMY_RECIPE_TYPE = RECIPE_TYPES.register(
                    RECIPE_ID,
                    () -> AlchemyRecipeSerializer.INSTANCE.getRecipeType()
            );

    private SharedAlchemyRecipes() {
    }

    /**
     * Attaches the serializer before its matching recipe type.
     */
    public static void register() {
        RECIPE_SERIALIZERS.attach();
        RECIPE_TYPES.attach();
    }
}
