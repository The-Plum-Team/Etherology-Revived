package ru.feytox.etherology.magic.lens;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LensModeAndModifierContractTest {

    @Test
    void lensModesKeepTheirNamesOrdinalsAndCodecIdentity() {
        assertEquals(
                List.of("STREAM", "CHARGE"),
                Arrays.stream(LensMode.values()).map(Enum::name).toList()
        );

        for (LensMode mode : LensMode.values()) {
            assertEquals(mode.name(), mode.asString());
            assertEquals(
                    new JsonPrimitive(mode.name()),
                    LensMode.CODEC.encodeStart(JsonOps.INSTANCE, mode).result().orElseThrow()
            );
            assertSame(
                    mode,
                    LensMode.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(mode.name()))
                            .result()
                            .orElseThrow()
            );
        }
    }

    @Test
    void modifierIdsLookupsAndCodecInstancesRemainCanonical() {
        Map<String, LensModifier> modifiers = new LinkedHashMap<>();
        modifiers.put("stream", LensModifier.STREAM);
        modifiers.put("charge", LensModifier.CHARGE);
        modifiers.put("filtering", LensModifier.FILTERING);
        modifiers.put("concentration", LensModifier.CONCENTRATION);
        modifiers.put("reinforcement", LensModifier.REINFORCEMENT);
        modifiers.put("area", LensModifier.AREA);
        modifiers.put("saving", LensModifier.SAVING);

        for (Map.Entry<String, LensModifier> entry : modifiers.entrySet()) {
            Identifier id = new Identifier("etherology", entry.getKey());
            LensModifier modifier = entry.getValue();
            assertEquals(id, modifier.modifierId());
            assertSame(modifier, LensModifier.get(id));
            assertEquals(
                    new JsonPrimitive(id.toString()),
                    LensModifier.CODEC.encodeStart(JsonOps.INSTANCE, modifier)
                            .result()
                            .orElseThrow()
            );
            assertSame(
                    modifier,
                    LensModifier.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(id.toString()))
                            .result()
                            .orElseThrow()
            );
        }

        assertNull(LensModifier.get(new Identifier("etherology", "unknown")));
        LensModifier.registerAll();
        assertSame(LensModifier.STREAM, LensModifier.get(LensModifier.STREAM.modifierId()));
    }

    @Test
    void modifierKeepsItsPublicRecordConstructor() throws NoSuchMethodException {
        assertTrue(java.lang.reflect.Modifier.isPublic(
                LensModifier.class.getDeclaredConstructor(Identifier.class).getModifiers()
        ));
    }
}
