package ru.feytox.etherology.recipes.jewelry;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.minecraft.advancement.criterion.CriterionConditions;
import net.minecraft.data.server.recipe.CraftingRecipeJsonBuilder;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.Etherology;

import java.util.List;
import java.util.function.Consumer;

@RequiredArgsConstructor(staticName = "create")
public class LensRecipeBuilder implements CraftingRecipeJsonBuilder {

    private final List<String> pattern = new ObjectArrayList<>();
    @NonNull
    private final Item outputItem;
    private final int etherPoints;

    public LensRecipeBuilder pattern(String patternStr) {
        pattern.add(patternStr);
        return this;
    }

    @Override
    public CraftingRecipeJsonBuilder criterion(String name, CriterionConditions criterion) {
        Etherology.ELOGGER.warn("Criterion is not yet supported by Lens recipe type.");
        return null;
    }

    @Override
    public CraftingRecipeJsonBuilder group(@Nullable String group) {
        Etherology.ELOGGER.warn("Group is not yet supported by Lens recipe type.");
        return null;
    }

    @Override
    public Item getOutputItem() {
        return outputItem;
    }

    @Override
    public void offerTo(Consumer<RecipeJsonProvider> exporter, Identifier recipeId) {
        AbstractJewelryRecipe.Pattern pattern = AbstractJewelryRecipe.Pattern.create(this.pattern);
        LensRecipe recipe = new LensRecipe(pattern, outputItem, etherPoints, recipeId);
        exporter.accept(LensRecipeSerializer.INSTANCE.toProvider(recipe));
    }
}
