package ru.feytox.etherology.recipes.matrix;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.Ingredient;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import ru.feytox.etherology.magic.aspects.Aspect;
import ru.feytox.etherology.recipes.FeyRecipeSerializer;

import java.util.ArrayList;
import java.util.List;

public class MatrixRecipeSerializer extends FeyRecipeSerializer<MatrixRecipe> {

    public static final MatrixRecipeSerializer INSTANCE = new MatrixRecipeSerializer();

    public MatrixRecipeSerializer() {
        super("matrix_recipe");
    }

    @Override
    public MatrixRecipe read(Identifier id, JsonObject json) {
        Ingredient centerInput = Ingredient.fromJson(JsonHelper.getElement(json, "center_input"), false);
        JsonArray aspectsJson = JsonHelper.getArray(json, "aspects");
        List<Aspect> aspects = new ArrayList<>(aspectsJson.size());
        for (JsonElement element : aspectsJson) {
            String aspectName = JsonHelper.asString(element, "aspects");
            Aspect aspect = Aspect.get(new Identifier(aspectName));
            if (aspect == null) throw new JsonSyntaxException("No such aspect " + aspectName);
            aspects.add(aspect);
        }
        float etherPoints = JsonHelper.getFloat(json, "ether_points");
        return new MatrixRecipe(centerInput, aspects, etherPoints, readItemStack(json, "result"), id);
    }

    @Override
    public MatrixRecipe read(Identifier id, PacketByteBuf buf) {
        Ingredient centerInput = Ingredient.fromPacket(buf);
        List<Aspect> aspects = buf.readList(packetBuf -> packetBuf.readEnumConstant(Aspect.class));
        float etherPoints = buf.readFloat();
        return new MatrixRecipe(centerInput, aspects, etherPoints, buf.readItemStack(), id);
    }

    @Override
    public void write(PacketByteBuf buf, MatrixRecipe recipe) {
        recipe.getCenterInput().write(buf);
        buf.writeCollection(recipe.getAspects(), PacketByteBuf::writeEnumConstant);
        buf.writeFloat(recipe.getEtherPoints());
        buf.writeItemStack(recipe.getOutput());
    }

    @Override
    protected void writeJson(JsonObject json, MatrixRecipe recipe) {
        json.add("center_input", recipe.getCenterInput().toJson());
        JsonArray aspects = new JsonArray();
        recipe.getAspects().forEach(aspect -> aspects.add(aspect.asString()));
        json.add("aspects", aspects);
        json.addProperty("ether_points", recipe.getEtherPoints());
        writeItemStack(json, "result", recipe.getOutput());
    }
}
