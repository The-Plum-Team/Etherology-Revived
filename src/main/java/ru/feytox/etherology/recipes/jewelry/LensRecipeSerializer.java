package ru.feytox.etherology.recipes.jewelry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import ru.feytox.etherology.recipes.FeyRecipeSerializer;

import java.util.ArrayList;
import java.util.List;

public class LensRecipeSerializer extends FeyRecipeSerializer<LensRecipe> {

    public static final LensRecipeSerializer INSTANCE = new LensRecipeSerializer();

    public LensRecipeSerializer() {
        super("lens_recipe");
    }

    @Override
    public LensRecipe read(Identifier id, JsonObject json) {
        AbstractJewelryRecipe.Pattern pattern = readPattern(json);
        Item outputItem = readItem(json, "result");
        int ether = JsonHelper.getInt(json, "ether");
        return new LensRecipe(pattern, outputItem, ether, id);
    }

    @Override
    public LensRecipe read(Identifier id, PacketByteBuf buf) {
        AbstractJewelryRecipe.Pattern pattern = AbstractJewelryRecipe.Pattern.read(buf);
        Item outputItem = Registries.ITEM.get(buf.readIdentifier());
        int ether = buf.readVarInt();
        return new LensRecipe(pattern, outputItem, ether, id);
    }

    @Override
    public void write(PacketByteBuf buf, LensRecipe recipe) {
        recipe.getPattern().write(buf);
        buf.writeIdentifier(Registries.ITEM.getId(recipe.getOutputItem()));
        buf.writeVarInt(recipe.getEther());
    }

    @Override
    protected void writeJson(JsonObject json, LensRecipe recipe) {
        writePattern(json, recipe.getPattern());
        json.addProperty("result", Registries.ITEM.getId(recipe.getOutputItem()).toString());
        json.addProperty("ether", recipe.getEther());
    }

    static AbstractJewelryRecipe.Pattern readPattern(JsonObject json) {
        JsonArray patternJson = JsonHelper.getArray(json, "pattern");
        List<String> rows = new ArrayList<>(patternJson.size());
        for (int i = 0; i < patternJson.size(); i++) {
            rows.add(JsonHelper.asString(patternJson.get(i), "pattern[" + i + "]"));
        }
        return AbstractJewelryRecipe.Pattern.create(rows);
    }

    static void writePattern(JsonObject json, AbstractJewelryRecipe.Pattern pattern) {
        List<String> rows = pattern.data()
                .orElseThrow(() -> new IllegalStateException("Cannot encode unpacked jewelry recipe"));
        JsonArray patternJson = new JsonArray();
        rows.forEach(patternJson::add);
        json.add("pattern", patternJson);
    }
}
