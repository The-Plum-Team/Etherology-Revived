package ru.feytox.etherology.recipes.alchemy;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import ru.feytox.etherology.magic.aspects.AspectContainer;

/**
 * Presents one cauldron input stack together with its current aspect contents.
 */
public class AlchemyRecipeInventory extends SimpleInventory {

    private final AspectContainer cauldronAspects;

    /**
     * Creates the one-slot recipe input consumed by vanilla recipe lookup.
     */
    public AlchemyRecipeInventory(
            AspectContainer cauldronAspects,
            ItemStack stack
    ) {
        super(stack);
        this.cauldronAspects = cauldronAspects;
    }

    /**
     * Returns the cauldron aspects evaluated by alchemy matching.
     */
    public AspectContainer cauldronAspects() {
        return cauldronAspects;
    }

    /**
     * Returns the sole input slot.
     */
    public ItemStack stack() {
        return getStack(0);
    }
}
