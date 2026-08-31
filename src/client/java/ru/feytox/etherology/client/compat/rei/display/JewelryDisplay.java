package ru.feytox.etherology.client.compat.rei.display;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.item.ItemStack;
import ru.feytox.etherology.client.compat.rei.EtherREIPlugin;
import ru.feytox.etherology.magic.lens.LensComponent;
import ru.feytox.etherology.magic.lens.LensModifier;
import ru.feytox.etherology.recipes.jewelry.AbstractJewelryRecipe;
import ru.feytox.etherology.recipes.jewelry.LensRecipe;
import ru.feytox.etherology.recipes.jewelry.ModifierRecipe;
import ru.feytox.etherology.registry.item.EItems;
import ru.feytox.etherology.registry.misc.ComponentTypes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public abstract class JewelryDisplay<T extends AbstractJewelryRecipe> extends BasicDisplay {

    protected final T recipe;

    protected JewelryDisplay(List<EntryIngredient> inputs, List<EntryIngredient> outputs, T recipe) {
        super(inputs, outputs, Optional.of(recipe.getId()));
        this.recipe = recipe;
    }

    public T getRecipe() {
        return recipe;
    }

    public static class Lens extends JewelryDisplay<LensRecipe> {

        private Lens(List<EntryIngredient> inputs, List<EntryIngredient> outputs, LensRecipe recipe) {
            super(inputs, outputs, recipe);
        }

        public static Lens of(LensRecipe recipe) {
            List<EntryIngredient> input = Collections.singletonList(EntryIngredients.of(EItems.UNADJUSTED_LENS));
            List<EntryIngredient> output = Collections.singletonList(EntryIngredients.of(recipe.getOutputItem()));
            return new Lens(input, output, recipe);
        }

        @Override
        public CategoryIdentifier<?> getCategoryIdentifier() {
            return EtherREIPlugin.JEWELRY_LENS;
        }
    }

    public static class Modifier extends JewelryDisplay<ModifierRecipe> {

        private Modifier(List<EntryIngredient> inputs, List<EntryIngredient> outputs, ModifierRecipe recipe) {
            super(inputs, outputs, recipe);
        }

        public static Modifier of(ModifierRecipe recipe) {
            LensModifier modifier = recipe.getModifier();
            List<EntryIngredient> input = Collections.singletonList(EntryIngredient.of(Arrays.stream(EItems.LENSES).map(EntryStacks::of).toList()));
            List<EntryIngredient> output = Collections.singletonList(EntryIngredient.of(Arrays.stream(EItems.LENSES).map(item -> {
                ItemStack lensStack = item.getDefaultStack();
                ComponentTypes.LENS.apply(lensStack, LensComponent.EMPTY, component -> component.incrementLevel(modifier));
                return lensStack;
            }).map(EntryStacks::of).toList()));
            return new Modifier(input, output, recipe);
        }

        @Override
        public CategoryIdentifier<?> getCategoryIdentifier() {
            return EtherREIPlugin.JEWELRY_MODIFIER;
        }
    }
}
