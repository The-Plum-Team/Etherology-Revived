package ru.feytox.etherology.util.misc;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.JsonSerializer;

/**
 * Preserves the canonical JSON contract for Fortune-scaled random loot conditions.
 */
public final class RandomChanceWithFortuneConditionSerializer
        implements JsonSerializer<RandomChanceWithFortuneCondition> {

    /**
     * Creates the serializer used by the shared loot-condition type.
     */
    public RandomChanceWithFortuneConditionSerializer() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void toJson(
            JsonObject json,
            RandomChanceWithFortuneCondition condition,
            JsonSerializationContext context
    ) {
        json.addProperty("chance", condition.chance());
        json.addProperty("fortune_multiplier", condition.fortuneMultiplier());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RandomChanceWithFortuneCondition fromJson(
            JsonObject json,
            JsonDeserializationContext context
    ) {
        return new RandomChanceWithFortuneCondition(
                JsonHelper.getFloat(json, "chance"),
                JsonHelper.getFloat(json, "fortune_multiplier")
        );
    }
}
