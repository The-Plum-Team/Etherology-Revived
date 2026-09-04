package ru.feytox.etherology.recipes.alchemy;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.Ingredient;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import ru.feytox.etherology.magic.aspects.Aspect;
import ru.feytox.etherology.magic.aspects.AspectContainer;
import ru.feytox.etherology.recipes.FeyRecipeSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Preserves the 1.20.1 JSON and packet contract for alchemy recipes.
 */
public class AlchemyRecipeSerializer extends FeyRecipeSerializer<AlchemyRecipe> {

    public static final AlchemyRecipeSerializer INSTANCE =
            new AlchemyRecipeSerializer();

    /**
     * Creates the canonical {@code etherology:alchemy_recipe} serializer.
     */
    public AlchemyRecipeSerializer() {
        super("alchemy_recipe");
    }

    @Override
    public AlchemyRecipe read(Identifier id, JsonObject json) {
        Ingredient inputItem = Ingredient.fromJson(
                JsonHelper.getElement(json, "inputItem"),
                false
        );
        int inputAmount = JsonHelper.getInt(json, "inputAmount", 1);
        JsonObject aspectsJson = JsonHelper.getObject(json, "inputAspects");
        Map<Aspect, Integer> aspects = new LinkedHashMap<>();
        for (String aspectName : aspectsJson.keySet()) {
            Aspect aspect = Aspect.get(new Identifier(aspectName));
            if (aspect == null) {
                throw new JsonSyntaxException("No such aspect " + aspectName);
            }
            aspects.put(
                    aspect,
                    JsonHelper.asInt(
                            aspectsJson.get(aspectName),
                            "inputAspects." + aspectName
                    )
            );
        }
        return new AlchemyRecipe(
                inputItem,
                inputAmount,
                new AspectContainer(aspects),
                readItemStack(json, "result"),
                id
        );
    }

    @Override
    public AlchemyRecipe read(Identifier id, PacketByteBuf buffer) {
        Ingredient inputItem = Ingredient.fromPacket(buffer);
        int inputAmount = buffer.readVarInt();
        Map<Aspect, Integer> aspects = buffer.readMap(
                packetBuffer -> packetBuffer.readEnumConstant(Aspect.class),
                PacketByteBuf::readVarInt
        );
        return new AlchemyRecipe(
                inputItem,
                inputAmount,
                new AspectContainer(aspects),
                buffer.readItemStack(),
                id
        );
    }

    @Override
    public void write(PacketByteBuf buffer, AlchemyRecipe recipe) {
        recipe.getInputItem().write(buffer);
        buffer.writeVarInt(recipe.getInputAmount());
        buffer.writeMap(
                recipe.getInputAspects().getAspects(),
                PacketByteBuf::writeEnumConstant,
                PacketByteBuf::writeVarInt
        );
        buffer.writeItemStack(recipe.getOutput());
    }

    @Override
    protected void writeJson(JsonObject json, AlchemyRecipe recipe) {
        json.add("inputItem", recipe.getInputItem().toJson());
        json.addProperty("inputAmount", recipe.getInputAmount());
        JsonObject aspects = new JsonObject();
        recipe.getInputAspects().getAspects().forEach((aspect, value) ->
                aspects.addProperty(aspect.asString(), value));
        json.add("inputAspects", aspects);
        writeItemStack(json, "result", recipe.getOutput());
    }
}
