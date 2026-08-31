package dev.theplumteam.etherology.e2e.server;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ParticleProbeStateTest {

    @Test
    void canonicalInventoryCoversAllTwentyTwoIdsAndEightPayloadFamilies() {
        assertEquals(22, ParticleProbeState.EXPECTED_PARTICLES.size());
        assertEquals(
                ParticleProbeState.EXPECTED_PARTICLE_IDS.stream().sorted().toList(),
                ParticleProbeState.EXPECTED_PARTICLE_IDS
        );
        assertEquals(
                List.of(
                        "electricity",
                        "item",
                        "light",
                        "moving",
                        "scalable",
                        "seal",
                        "simple",
                        "spark"
                ),
                exactState(Map.of()).payloadFamilies()
        );
    }

    @Test
    void exactStateRecognizesRegistryTypeAndWireContracts() {
        ParticleProbeState state = exactState(Map.of());

        assertTrue(state.hasExactRegistry());
        assertTrue(state.hasExactTypeContract());
        assertTrue(state.hasExactWireContract());
        assertTrue(state.hasExactPacketRoundTrips());
        assertTrue(state.hasExactCodecRoundTrips());
    }

    @Test
    void canonicalSealTypesPreserveOrderCodecColorsAndTextures() {
        ParticleProbeState state = exactState(Map.of());

        assertEquals(
                List.of("EMPTY", "KETA", "RELLA", "VIA", "CLOS"),
                state.sealTypeOrder()
        );
        assertTrue(state.hasExactSealTypeOrder());
        assertTrue(state.hasExactSealTypeCodec());
        assertTrue(state.hasExactSealTypeColors());
        assertTrue(state.hasExactSealTypeTextures());
    }

    @Test
    void missingStateRejectsEveryExactMilestone() {
        ParticleProbeState state = ParticleProbeState.missing();

        assertFalse(state.hasExactRegistry());
        assertFalse(state.hasExactTypeContract());
        assertFalse(state.hasExactWireContract());
    }

    @Test
    void serverStartedStateRequiresTheSameRegistryObjectsAndContracts() {
        Map<String, Object> identities = identities();
        ParticleProbeState initial = exactState(identities);

        assertTrue(initial.sameStateAtServerStarted(exactState(identities)));
        assertFalse(initial.sameStateAtServerStarted(exactState(Map.of(
                "alchemy",
                new Object()
        ))));
    }

    @Test
    void reloadComparisonsSeparateRegistryTypeAndWireStability() {
        Map<String, Object> identities = identities();
        ParticleProbeState initial = exactState(identities);
        ParticleProbeState changedType = changedEntry(
                initial,
                "alchemy",
                entry -> new ParticleProbeState.ParticleEntry(
                        entry.typeIdentity(),
                        entry.id(),
                        entry.family(),
                        "wrong.Type",
                        entry.shouldAlwaysSpawn(),
                        entry.codecPresent(),
                        entry.parametersFactoryPresent(),
                        entry.factorySampleEffectClass(),
                        entry.factorySampleTypeMatches(),
                        entry.factorySampleAsString(),
                        entry.packetRoundTripExact(),
                        entry.codecRoundTripExact()
                )
        );
        ParticleProbeState changedWire = changedEntry(
                initial,
                "spark",
                entry -> new ParticleProbeState.ParticleEntry(
                        entry.typeIdentity(),
                        entry.id(),
                        entry.family(),
                        entry.typeClass(),
                        entry.shouldAlwaysSpawn(),
                        entry.codecPresent(),
                        entry.parametersFactoryPresent(),
                        entry.factorySampleEffectClass(),
                        entry.factorySampleTypeMatches(),
                        "wrong sample",
                        entry.packetRoundTripExact(),
                        entry.codecRoundTripExact()
                )
        );
        Map<String, ParticleProbeState.SealTypeEntry> changedSealTypes =
                new LinkedHashMap<>(initial.sealTypes());
        ParticleProbeState.SealTypeEntry keta = changedSealTypes.get("keta");
        changedSealTypes.put(
                "keta",
                new ParticleProbeState.SealTypeEntry(
                        keta.enumName(),
                        keta.asString(),
                        "0,0,0",
                        keta.endColor(),
                        keta.textureId(),
                        keta.textureLightId()
                )
        );
        ParticleProbeState changedSeal = new ParticleProbeState(
                initial.captureError(),
                initial.etherologyParticleIds(),
                initial.entries(),
                initial.sealTypeOrder(),
                Collections.unmodifiableMap(changedSealTypes),
                initial.sealTypeCodecRoundTripsExact()
        );

        assertTrue(initial.hasSameRegistry(changedType));
        assertFalse(initial.hasSameTypeContract(changedType));
        assertTrue(initial.hasSameWireContract(changedType));
        assertTrue(initial.hasSameRegistry(changedWire));
        assertTrue(initial.hasSameTypeContract(changedWire));
        assertFalse(initial.hasSameWireContract(changedWire));
        assertTrue(initial.hasSameRegistry(changedSeal));
        assertTrue(initial.hasSameTypeContract(changedSeal));
        assertFalse(initial.hasSameWireContract(changedSeal));
    }

    private static ParticleProbeState exactState(
            Map<String, Object> identityOverrides
    ) {
        Map<String, ParticleProbeState.ParticleEntry> entries = new LinkedHashMap<>();
        ParticleProbeState.EXPECTED_PARTICLES.forEach((path, spec) -> entries.put(
                path,
                new ParticleProbeState.ParticleEntry(
                        identityOverrides.getOrDefault(path, new Object()),
                        spec.id(),
                        spec.family(),
                        ParticleProbeState.FEY_PARTICLE_TYPE_CLASS,
                        false,
                        true,
                        true,
                        spec.effectClass(),
                        true,
                        spec.expectedAsString(),
                        true,
                        true
                )
        ));
        return new ParticleProbeState(
                "",
                ParticleProbeState.EXPECTED_PARTICLE_IDS,
                Collections.unmodifiableMap(entries),
                ParticleProbeState.EXPECTED_SEAL_TYPE_ORDER,
                exactSealTypes(),
                true
        );
    }

    private static Map<String, ParticleProbeState.SealTypeEntry> exactSealTypes() {
        Map<String, ParticleProbeState.SealTypeEntry> sealTypes = new LinkedHashMap<>();
        ParticleProbeState.EXPECTED_SEAL_TYPES.forEach((name, spec) -> sealTypes.put(
                name,
                new ParticleProbeState.SealTypeEntry(
                        spec.enumName(),
                        spec.asString(),
                        spec.startColor(),
                        spec.endColor(),
                        spec.textureId(),
                        spec.textureLightId()
                )
        ));
        return Collections.unmodifiableMap(sealTypes);
    }

    private static Map<String, Object> identities() {
        Map<String, Object> identities = new LinkedHashMap<>();
        ParticleProbeState.EXPECTED_PARTICLES.keySet().forEach(
                path -> identities.put(path, new Object())
        );
        return Collections.unmodifiableMap(identities);
    }

    private static ParticleProbeState changedEntry(
            ParticleProbeState state,
            String path,
            java.util.function.UnaryOperator<ParticleProbeState.ParticleEntry> change
    ) {
        Map<String, ParticleProbeState.ParticleEntry> entries = new LinkedHashMap<>(
                state.entries()
        );
        entries.computeIfPresent(path, (ignored, entry) -> change.apply(entry));
        return new ParticleProbeState(
                state.captureError(),
                state.etherologyParticleIds(),
                Collections.unmodifiableMap(entries),
                state.sealTypeOrder(),
                state.sealTypes(),
                state.sealTypeCodecRoundTripsExact()
        );
    }
}
