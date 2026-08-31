package ru.feytox.etherology.recipes.empower;

import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.chars.CharArraySet;
import it.unimi.dsi.fastutil.chars.CharSet;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import ru.feytox.etherology.recipes.FeyRecipe;
import ru.feytox.etherology.recipes.FeyRecipeSerializer;
import ru.feytox.etherology.util.inventory.ListBackedInventory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public class EmpowerRecipe implements FeyRecipe<ListBackedInventory> {

    private final Pattern pattern;
    private final int rellaCount;
    private final int viaCount;
    private final int closCount;
    private final int ketaCount;
    @Getter(value = AccessLevel.PRIVATE)
    private final ItemStack outputStack;
    private final Identifier id;

    @Override
    public boolean matches(ListBackedInventory inventory, World world) {
        if (inventory.size() != 10) return false;

        int invSlot = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if ((i == 0 || i == 2) && (j == 0 || j == 2)) continue;

                Ingredient ingredient = pattern.ingredients.get(j + i * 3);
                if (!ingredient.test(inventory.getStack(invSlot))) {
                    return false;
                }

                invSlot++;
            }
        }

        return true;
    }

    public boolean checkShards(ListBackedInventory inventory) {
        return inventory.getStack(5).getCount() >= rellaCount
                && inventory.getStack(6).getCount() >= viaCount
                && inventory.getStack(7).getCount() >= closCount
                && inventory.getStack(8).getCount() >= ketaCount;
    }

    @Override
    public ItemStack craft(ListBackedInventory inventory, DynamicRegistryManager registryManager) {
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

    public ItemStack getOutput() {
        return outputStack.copy();
    }

    @Override
    public FeyRecipeSerializer<?> getSerializer() {
        return EmpowerRecipeSerializer.INSTANCE;
    }

    public record Pattern(DefaultedList<Ingredient> ingredients, Optional<Data> data) {

        public static Pattern create(Map<Character, Ingredient> key, List<String> pattern) {
            return fromData(new Data(key, pattern)).getOrThrow(false, message -> {
                throw new IllegalArgumentException(message);
            });
        }

        private static DataResult<Pattern> fromData(Data data) {
            List<String> pattern = data.pattern();
            DefaultedList<Ingredient> ingredients = DefaultedList.ofSize(3 * 3, Ingredient.EMPTY);
            CharSet charSet = new CharArraySet(data.key().keySet());
            charSet.remove(' ');

            for(int k = 0; k < pattern.size(); ++k) {
                String string = pattern.get(k);

                for(int l = 0; l < string.length(); ++l) {
                    char c = string.charAt(l);
                    Ingredient ingredient = c == ' ' ? Ingredient.EMPTY : data.key().get(c);
                    if (ingredient == null) return DataResult.error(() -> "Pattern references symbol '" + c + "' but it's not defined in the key");

                    charSet.remove(c);
                    ingredients.set(l + 3 * k, ingredient);
                }
            }

            if (charSet.isEmpty()) return DataResult.success(new Pattern(ingredients, Optional.of(data)));
            return DataResult.error(() -> "Key defines symbols that aren't used in pattern: " + charSet);
        }

        public record Data(Map<Character, Ingredient> key, List<String> pattern) {
        }
    }
}
