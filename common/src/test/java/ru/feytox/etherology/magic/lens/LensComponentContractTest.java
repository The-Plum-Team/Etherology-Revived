package ru.feytox.etherology.magic.lens;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LensComponentContractTest {

    @Test
    void codecKeepsTheExactFiveFieldSchemaAndRoundTripsState() {
        LensPattern.Mutable pattern = LensPattern.empty().asMutable();
        pattern.markHard(3);
        LensModifiersData.Mutable modifiers = LensModifiersData.empty().asMutable();
        modifiers.setLevel(LensModifier.CHARGE, 2);
        LensComponent source = new LensComponent(
                27,
                LensMode.CHARGE,
                pattern,
                modifiers,
                9123L
        );

        JsonElement encoded = LensComponent.CODEC.encodeStart(JsonOps.INSTANCE, source)
                .result()
                .orElseThrow();
        JsonObject object = encoded.getAsJsonObject();
        assertEquals(
                java.util.Set.of(
                        "charge",
                        "mode",
                        "pattern",
                        "modifiers",
                        "end_tick"
                ),
                object.keySet()
        );
        assertEquals(27, object.get("charge").getAsInt());
        assertEquals("CHARGE", object.get("mode").getAsString());
        assertEquals(9123L, object.get("end_tick").getAsLong());

        LensComponent decoded = LensComponent.CODEC.parse(JsonOps.INSTANCE, encoded)
                .result()
                .orElseThrow();
        assertEquals(source, decoded);
        assertNotSame(source.pattern(), decoded.pattern());
        assertNotSame(source.modifiers(), decoded.modifiers());
    }

    @Test
    void explicitWithersKeepTheFormerLombokIdentityContract() {
        LensPattern pattern = LensPattern.empty();
        LensModifiersData modifiers = LensModifiersData.empty();
        LensComponent source = new LensComponent(
                4,
                LensMode.STREAM,
                pattern,
                modifiers,
                12L
        );

        assertSame(source, source.withCharge(4));
        assertSame(source, source.withMode(LensMode.STREAM));
        assertSame(source, source.withPattern(pattern));
        assertSame(source, source.withModifiers(modifiers));
        assertSame(source, source.withEndTick(12L));

        LensPattern equalPattern = LensPattern.empty();
        LensModifiersData equalModifiers = LensModifiersData.empty();
        assertEquals(pattern, equalPattern);
        assertEquals(modifiers, equalModifiers);
        assertNotSame(source, source.withPattern(equalPattern));
        assertNotSame(source, source.withModifiers(equalModifiers));

        assertEquals(8, source.withCharge(8).charge());
        assertEquals(LensMode.CHARGE, source.withMode(LensMode.CHARGE).mode());
        assertEquals(99L, source.withEndTick(99L).endTick());
        assertNull(source.withMode(null).mode());
        assertNull(source.withPattern(null).pattern());
        assertNull(source.withModifiers(null).modifiers());
    }

    @Test
    void modifierMutationAndValueMathRemainCanonical() {
        LensModifiersData.Mutable modifiers = LensModifiersData.empty().asMutable();
        modifiers.setLevel(LensModifier.SAVING, 2);
        LensComponent source = LensComponent.EMPTY.withModifiers(modifiers);

        assertEquals(2, source.getLevel(LensModifier.SAVING));
        assertEquals(
                0.60625f,
                source.calcValue(LensModifier.SAVING, 1.0f, 0.1f, 0.75f)
        );
        assertEquals(
                8,
                source.calcRoundValue(LensModifier.SAVING, 16, 1, 0.67f)
        );

        LensComponent incremented = source.incrementLevel(LensModifier.SAVING);
        assertEquals(2, source.getLevel(LensModifier.SAVING));
        assertEquals(3, incremented.getLevel(LensModifier.SAVING));
        assertTrue(incremented.modifiers() instanceof LensModifiersData.Mutable);
        assertNotSame(source.modifiers(), incremented.modifiers());
        assertFalse(incremented.modifiers().isEmpty());
    }
}
