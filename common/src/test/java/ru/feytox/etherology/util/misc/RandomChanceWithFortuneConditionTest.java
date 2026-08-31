package ru.feytox.etherology.util.misc;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameter;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.util.math.random.Random;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RandomChanceWithFortuneConditionTest {

    @Test
    void declaresTheToolAsItsOnlyRequiredParameter() {
        RandomChanceWithFortuneCondition condition = condition(0.05F, 0.05F);

        assertEquals(Set.of(LootContextParameters.TOOL), condition.getRequiredParameters());
    }

    @Test
    void comparesTheRandomSampleAgainstAStrictBaseChance() throws Exception {
        long seed = 725_423L;
        float sample = Random.create(seed).nextFloat();

        assertFalse(condition(sample, 0.0F).test(context(seed)));
        assertTrue(condition(Math.nextUp(sample), 0.0F).test(context(seed)));
    }

    @Test
    void addsTheFortuneLevelTimesTheConfiguredMultiplier() throws Exception {
        RandomChanceWithFortuneCondition condition = condition(0.0F, 1.0F / 3.0F);

        assertFalse(condition.passes(0.75F, 0));
        assertTrue(condition.passes(0.75F, 3));
    }

    @Test
    void addsTheNonzeroBaseAndFortuneTermsBeforeTheStrictComparison() {
        RandomChanceWithFortuneCondition condition = condition(0.125F, 0.25F);
        float combinedThreshold = 0.625F;

        assertFalse(condition.passes(combinedThreshold, 2));
        assertTrue(condition.passes(Math.nextDown(combinedThreshold), 2));
    }

    @Test
    void preservesTheExactJsonFieldContract() {
        RandomChanceWithFortuneConditionSerializer serializer =
                new RandomChanceWithFortuneConditionSerializer();
        JsonObject json = new JsonObject();

        serializer.toJson(json, condition(0.05F, 0.075F), null);

        assertEquals(Set.of("chance", "fortune_multiplier"), json.keySet());
        assertEquals(0.05F, json.get("chance").getAsFloat());
        assertEquals(0.075F, json.get("fortune_multiplier").getAsFloat());

        JsonObject roundTrip = new JsonObject();
        serializer.toJson(roundTrip, serializer.fromJson(json, null), null);
        assertEquals(json, roundTrip);
    }

    @Test
    void rejectsJsonMissingEitherRequiredNumber() {
        RandomChanceWithFortuneConditionSerializer serializer =
                new RandomChanceWithFortuneConditionSerializer();
        JsonObject missingChance = new JsonObject();
        missingChance.addProperty("fortune_multiplier", 0.05F);
        JsonObject missingMultiplier = new JsonObject();
        missingMultiplier.addProperty("chance", 0.05F);

        assertThrows(JsonSyntaxException.class, () -> serializer.fromJson(missingChance, null));
        assertThrows(
                JsonSyntaxException.class,
                () -> serializer.fromJson(missingMultiplier, null)
        );
    }

    private static RandomChanceWithFortuneCondition condition(
            float chance,
            float fortuneMultiplier
    ) {
        LootCondition builtCondition = RandomChanceWithFortuneCondition.builder(
                chance,
                fortuneMultiplier
        ).build();
        return (RandomChanceWithFortuneCondition) builtCondition;
    }

    private static LootContext context(long seed) throws Exception {
        Map<LootContextParameter<?>, Object> parameters = new LinkedHashMap<>();
        LootContextParameterSet parameterSet = new LootContextParameterSet(
                null,
                parameters,
                Map.of(),
                0.0F
        );
        Constructor<LootContext> constructor = LootContext.class.getDeclaredConstructor(
                LootContextParameterSet.class,
                Random.class,
                net.minecraft.loot.LootDataLookup.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(parameterSet, Random.create(seed), null);
    }
}
