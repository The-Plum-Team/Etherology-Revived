package ru.feytox.etherology.recipes.alchemy;

import com.google.common.collect.ImmutableMap;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import ru.feytox.etherology.magic.aspects.Aspect;
import ru.feytox.etherology.magic.aspects.AspectContainer;
import ru.feytox.etherology.recipes.FeyInputRecipe;
import ru.feytox.etherology.recipes.FeyRecipeSerializer;

import java.util.Map;

/**
 * Matches one input stack and a minimum amount of every required cauldron aspect.
 */
public class AlchemyRecipe implements FeyInputRecipe<AlchemyRecipeInventory> {

    private final Ingredient inputItem;
    private final int inputAmount;
    private final AspectContainer inputAspects;
    private final ItemStack outputStack;
    private final Identifier id;

    /**
     * Creates the complete immutable recipe definition and copies outputs on access.
     */
    public AlchemyRecipe(
            Ingredient inputItem,
            int inputAmount,
            AspectContainer inputAspects,
            ItemStack outputStack,
            Identifier id
    ) {
        this.inputItem = inputItem;
        this.inputAmount = inputAmount;
        this.inputAspects = inputAspects;
        this.outputStack = outputStack;
        this.id = id;
    }

    @Override
    public boolean matches(AlchemyRecipeInventory inventory, World world) {
        if (!inputItem.test(inventory.stack())
                || inventory.stack().getCount() < inputAmount) {
            return false;
        }

        ImmutableMap<Aspect, Integer> cauldronAspects =
                inventory.cauldronAspects().getAspects();
        for (Map.Entry<Aspect, Integer> inputEntry
                : inputAspects.getAspects().entrySet()) {
            Integer cauldronValue = cauldronAspects.get(inputEntry.getKey());
            if (cauldronValue == null
                    || cauldronValue < inputEntry.getValue()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack craft(
            AlchemyRecipeInventory inventory,
            DynamicRegistryManager registryManager
    ) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean fits(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getOutput(DynamicRegistryManager registryManager) {
        return getOutput();
    }

    /**
     * Returns a defensive copy of the result stack.
     */
    public ItemStack getOutput() {
        return outputStack.copy();
    }

    /**
     * Returns the ingredient consumed by the brewing cauldron.
     */
    public Ingredient getInputItem() {
        return inputItem;
    }

    /**
     * Returns the minimum number of matching items required.
     */
    public int getInputAmount() {
        return inputAmount;
    }

    /**
     * Returns the minimum aspect amounts required in the cauldron.
     */
    public AspectContainer getInputAspects() {
        return inputAspects;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public FeyRecipeSerializer<?> getSerializer() {
        return AlchemyRecipeSerializer.INSTANCE;
    }
}
