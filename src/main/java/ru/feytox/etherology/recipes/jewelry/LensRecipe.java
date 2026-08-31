package ru.feytox.etherology.recipes.jewelry;

import lombok.Getter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import ru.feytox.etherology.block.jewelryTable.JewelryTableInventory;
import ru.feytox.etherology.magic.lens.LensComponent;
import ru.feytox.etherology.magic.lens.LensPattern;
import ru.feytox.etherology.recipes.FeyRecipeSerializer;

@Getter
public class LensRecipe extends AbstractJewelryRecipe {

    private final Item outputItem;

    public LensRecipe(Pattern pattern, Item outputItem, int ether, Identifier id) {
        super(pattern, ether, id);
        this.outputItem = outputItem;
    }

    @Override
    public ItemStack getOutput(DynamicRegistryManager registryManager) {
        return outputItem.getDefaultStack();
    }

    @Override
    public ItemStack craft(JewelryTableInventory inventory) {
        ItemStack newLens = outputItem.getDefaultStack();
        ItemStack oldLens = inventory.getStack(0);
        if (oldLens.hasNbt()) newLens.setNbt(oldLens.getNbt().copy());
        LensComponent.getWrapper(newLens).ifPresent(data -> data.set(LensPattern.empty(), LensComponent::withPattern).save());
        return newLens;
    }

    @Override
    public FeyRecipeSerializer<?> getSerializer() {
        return LensRecipeSerializer.INSTANCE;
    }
}
