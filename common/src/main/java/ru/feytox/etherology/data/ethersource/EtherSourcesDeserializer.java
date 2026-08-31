package ru.feytox.etherology.data.ethersource;

import com.google.gson.JsonElement;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

final class EtherSourcesDeserializer {

    private EtherSourcesDeserializer() {
    }

    static Map<Identifier, Float> deserialize(JsonElement json) {
        Map<Identifier, Float> result = new HashMap<>();
        json.getAsJsonObject().asMap().forEach((itemId, value) ->
                result.put(new Identifier(itemId), value.getAsFloat())
        );
        return result;
    }
}
