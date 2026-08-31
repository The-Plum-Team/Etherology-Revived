package ru.feytox.etherology.recipes.alchemy;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import ru.feytox.etherology.magic.aspects.AspectContainer;

public class AlchemyRecipeInventory extends SimpleInventory {

    private final AspectContainer cauldronAspects;

    public AlchemyRecipeInventory(AspectContainer cauldronAspects, ItemStack stack) {
        super(stack);
        this.cauldronAspects = cauldronAspects;
    }

    public AspectContainer cauldronAspects() {
        return cauldronAspects;
    }

    public ItemStack stack() {
        return getStack(0);
    }
}
