package ru.feytox.etherology.recipes.jewelry;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import ru.feytox.etherology.magic.lens.LensModifier;
import ru.feytox.etherology.recipes.FeyRecipeSerializer;

public class ModifierRecipeSerializer extends FeyRecipeSerializer<ModifierRecipe> {

    public static final ModifierRecipeSerializer INSTANCE = new ModifierRecipeSerializer();

    public ModifierRecipeSerializer() {
        super("modifier_recipe");
    }

    @Override
    public ModifierRecipe read(Identifier id, JsonObject json) {
        AbstractJewelryRecipe.Pattern pattern = LensRecipeSerializer.readPattern(json);
        Identifier modifierId = new Identifier(JsonHelper.getString(json, "outputModifier"));
        LensModifier modifier = LensModifier.get(modifierId);
        if (modifier == null) throw new JsonSyntaxException("No such lens modifier " + modifierId);
        int ether = JsonHelper.getInt(json, "ether");
        return new ModifierRecipe(pattern, modifier, ether, id);
    }

    @Override
    public ModifierRecipe read(Identifier id, PacketByteBuf buf) {
        AbstractJewelryRecipe.Pattern pattern = AbstractJewelryRecipe.Pattern.read(buf);
        Identifier modifierId = buf.readIdentifier();
        LensModifier modifier = LensModifier.get(modifierId);
        if (modifier == null) throw new JsonSyntaxException("No such lens modifier " + modifierId);
        int ether = buf.readVarInt();
        return new ModifierRecipe(pattern, modifier, ether, id);
    }

    @Override
    public void write(PacketByteBuf buf, ModifierRecipe recipe) {
        recipe.getPattern().write(buf);
        buf.writeIdentifier(recipe.getModifier().modifierId());
        buf.writeVarInt(recipe.getEther());
    }

    @Override
    protected void writeJson(JsonObject json, ModifierRecipe recipe) {
        LensRecipeSerializer.writePattern(json, recipe.getPattern());
        json.addProperty("outputModifier", recipe.getModifier().modifierId().toString());
        json.addProperty("ether", recipe.getEther());
    }
}
