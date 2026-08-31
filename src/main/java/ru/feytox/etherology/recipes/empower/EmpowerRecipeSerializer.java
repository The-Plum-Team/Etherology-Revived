package ru.feytox.etherology.recipes.empower;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.Ingredient;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.collection.DefaultedList;
import ru.feytox.etherology.recipes.FeyRecipeSerializer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EmpowerRecipeSerializer extends FeyRecipeSerializer<EmpowerRecipe> {

    public static final EmpowerRecipeSerializer INSTANCE = new EmpowerRecipeSerializer();

    public EmpowerRecipeSerializer() {
        super("empower_recipe");
    }

    @Override
    public EmpowerRecipe read(Identifier id, JsonObject json) {
        Map<Character, Ingredient> key = readKey(JsonHelper.getObject(json, "key"));
        List<String> patternRows = readPattern(JsonHelper.getArray(json, "pattern"));
        EmpowerRecipe.Pattern pattern = EmpowerRecipe.Pattern.create(key, patternRows);
        int rellaCount = JsonHelper.getInt(json, "rellaCount", 0);
        int viaCount = JsonHelper.getInt(json, "viaCount", 0);
        int closCount = JsonHelper.getInt(json, "closCount", 0);
        int ketaCount = JsonHelper.getInt(json, "ketaCount", 0);
        return new EmpowerRecipe(pattern, rellaCount, viaCount, closCount, ketaCount, readItemStack(json, "result"), id);
    }

    @Override
    public EmpowerRecipe read(Identifier id, PacketByteBuf buf) {
        List<Ingredient> ingredients = buf.readList(Ingredient::fromPacket);
        DefaultedList<Ingredient> ingredientList = DefaultedList.copyOf(
                Ingredient.EMPTY,
                ingredients.toArray(new Ingredient[0]));
        EmpowerRecipe.Pattern pattern = new EmpowerRecipe.Pattern(ingredientList, java.util.Optional.empty());
        int rellaCount = buf.readVarInt();
        int viaCount = buf.readVarInt();
        int closCount = buf.readVarInt();
        int ketaCount = buf.readVarInt();
        return new EmpowerRecipe(pattern, rellaCount, viaCount, closCount, ketaCount, buf.readItemStack(), id);
    }

    @Override
    public void write(PacketByteBuf buf, EmpowerRecipe recipe) {
        buf.writeCollection(recipe.getPattern().ingredients(), (packetBuf, ingredient) -> ingredient.write(packetBuf));
        buf.writeVarInt(recipe.getRellaCount());
        buf.writeVarInt(recipe.getViaCount());
        buf.writeVarInt(recipe.getClosCount());
        buf.writeVarInt(recipe.getKetaCount());
        buf.writeItemStack(recipe.getOutput());
    }

    @Override
    protected void writeJson(JsonObject json, EmpowerRecipe recipe) {
        EmpowerRecipe.Pattern.Data data = recipe.getPattern().data()
                .orElseThrow(() -> new IllegalStateException("Cannot encode unpacked empowerment recipe"));
        JsonObject key = new JsonObject();
        data.key().forEach((symbol, ingredient) -> key.add(symbol.toString(), ingredient.toJson()));
        json.add("key", key);

        JsonArray pattern = new JsonArray();
        data.pattern().forEach(pattern::add);
        json.add("pattern", pattern);
        json.addProperty("rellaCount", recipe.getRellaCount());
        json.addProperty("viaCount", recipe.getViaCount());
        json.addProperty("closCount", recipe.getClosCount());
        json.addProperty("ketaCount", recipe.getKetaCount());
        writeItemStack(json, "result", recipe.getOutput());
    }

    private static Map<Character, Ingredient> readKey(JsonObject json) {
        Map<Character, Ingredient> key = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String symbol = entry.getKey();
            if (symbol.length() != 1) {
                throw new JsonSyntaxException("Invalid key entry: '" + symbol + "' must be one character");
            }
            if (symbol.charAt(0) == ' ') {
                throw new JsonSyntaxException("Invalid key entry: ' ' is reserved");
            }
            key.put(symbol.charAt(0), Ingredient.fromJson(entry.getValue(), false));
        }
        return key;
    }

    private static List<String> readPattern(JsonArray json) {
        if (json.isEmpty() || json.size() > 3) {
            throw new JsonSyntaxException("Empowerment pattern must contain between one and three rows");
        }

        java.util.ArrayList<String> rows = new java.util.ArrayList<>(json.size());
        int width = -1;
        for (int i = 0; i < json.size(); i++) {
            String row = JsonHelper.asString(json.get(i), "pattern[" + i + "]");
            if (row.isEmpty() || row.length() > 3) {
                throw new JsonSyntaxException("Empowerment pattern rows must contain between one and three columns");
            }
            if (width >= 0 && row.length() != width) {
                throw new JsonSyntaxException("Empowerment pattern rows must have the same width");
            }
            width = row.length();
            rows.add(row);
        }
        return rows;
    }
}
