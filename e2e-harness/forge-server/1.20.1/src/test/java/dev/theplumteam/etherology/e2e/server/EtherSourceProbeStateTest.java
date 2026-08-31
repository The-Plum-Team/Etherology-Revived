package dev.theplumteam.etherology.e2e.server;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EtherSourceProbeStateTest {

    @Test
    void normalizesIdentifierKeysAndFiniteNumericValues() {
        Map<Identifier, Number> rawEntries = new LinkedHashMap<>();
        rawEntries.put(Identifier.of("minecraft", "redstone"), 2);
        rawEntries.put(Identifier.of("etherology", "primoshard_rella"), 4.0F);

        EtherSourceProbeState state = EtherSourceProbeState.fromRawMap(rawEntries);

        assertEquals("", state.captureError());
        assertEquals(Map.of(
                "etherology:primoshard_rella", 4.0F,
                "minecraft:redstone", 2.0F
        ), state.entries());
        assertEquals(
                "etherology:primoshard_rella=4.0,minecraft:redstone=2.0",
                state.canonicalEntries()
        );
    }

    @Test
    void freezesTheExactInitialAndReloadedContracts() {
        EtherSourceProbeState initial = EtherSourceProbeState.fromRawMap(
                toIdentifierMap(EtherSourceProbeState.EXPECTED_INITIAL_ENTRIES)
        );
        EtherSourceProbeState reloaded = EtherSourceProbeState.fromRawMap(
                toIdentifierMap(EtherSourceProbeState.EXPECTED_RELOADED_ENTRIES)
        );

        assertEquals(23, initial.entries().size());
        assertEquals("4.0", initial.value("etherology:primoshard_rella"));
        assertEquals("absent", initial.value("etherology:primoshard_rela"));
        assertEquals("2.0", initial.value("minecraft:redstone"));
        assertTrue(initial.hasExactInitialEntries());
        assertFalse(initial.sameEntries(reloaded));
        assertEquals(24, reloaded.entries().size());
        assertEquals("9.5", reloaded.value("minecraft:redstone"));
        assertEquals("13.0", reloaded.value("minecraft:diamond"));
        assertTrue(reloaded.hasExactReloadedEntries());
    }

    @Test
    void rejectsInvalidCaptureShapes() {
        assertEquals("not a map", EtherSourceProbeState.fromRawMap("wrong").captureError());
        assertEquals(
                "entry type mismatch",
                EtherSourceProbeState.fromRawMap(Map.of("minecraft:redstone", 2.0F))
                        .captureError()
        );
        assertEquals(
                "non-finite value",
                EtherSourceProbeState.fromRawMap(Map.of(
                        Identifier.of("minecraft", "redstone"),
                        Float.NaN
                )).captureError()
        );
    }

    private static Map<Identifier, Float> toIdentifierMap(Map<String, Float> entries) {
        Map<Identifier, Float> result = new LinkedHashMap<>();
        entries.forEach((identifier, value) -> result.put(new Identifier(identifier), value));
        return result;
    }
}
