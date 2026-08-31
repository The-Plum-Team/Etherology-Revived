package dev.theplumteam.etherology.e2e.server;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MaterialItemProbeStateTest {

    @Test
    void canonicalInventoryContainsTheExactFourteenMaterialItems() {
        assertEquals(14, MaterialItemProbeState.EXPECTED_ITEM_IDS.size());
        assertEquals(
                MaterialItemProbeState.EXPECTED_ITEM_IDS.stream().sorted().toList(),
                MaterialItemProbeState.EXPECTED_ITEM_IDS
        );
        assertEquals(
                16,
                MaterialItemProbeState.EXPECTED_MAX_COUNTS.get(
                        "etherology:enriched_attrahite"
                )
        );
        assertTrue(MaterialItemProbeState.EXPECTED_MAX_COUNTS.entrySet().stream()
                .filter(entry -> !"etherology:enriched_attrahite".equals(entry.getKey()))
                .allMatch(entry -> entry.getValue() == 64));
    }

    @Test
    void exactStateRecognizesRegistryPropertiesAndStackNbt() {
        MaterialItemProbeState state = exactState(Map.of());

        assertTrue(state.hasExactRegistry());
        assertTrue(state.hasExactRuntimeClass());
        assertTrue(state.hasExactMaxCounts());
        assertTrue(state.hasExactStackNbtRoundTrips());
        assertTrue(state.hasExactSaveRepresentations());
        assertTrue(state.hasExactContract());
        assertEquals(
                MaterialItemProbeState.VANILLA_ITEM_CLASS,
                state.runtimeClassSummary()
        );
        assertEquals(
                MaterialItemProbeState.expectedCanonicalMaxCounts(),
                state.canonicalMaxCounts()
        );
    }

    @Test
    void missingStateRejectsEveryExactMilestone() {
        MaterialItemProbeState state = MaterialItemProbeState.missing();

        assertFalse(state.hasExactRegistry());
        assertFalse(state.hasExactRuntimeClass());
        assertFalse(state.hasExactMaxCounts());
        assertFalse(state.hasExactStackNbtRoundTrips());
        assertFalse(state.hasExactSaveRepresentations());
        assertFalse(state.hasExactContract());
    }

    @Test
    void registryComparisonRequiresTheSameRuntimeObjects() {
        Map<String, Object> identities = identities();
        MaterialItemProbeState initial = exactState(identities);

        assertTrue(initial.sameStateAtServerStarted(exactState(identities)));
        assertFalse(initial.sameStateAtServerStarted(exactState(Map.of(
                "etherology:etheroscope",
                new Object()
        ))));
    }

    @Test
    void reloadComparisonsSeparateRegistryPropertiesAndStackNbt() {
        Map<String, Object> identities = identities();
        MaterialItemProbeState initial = exactState(identities);
        MaterialItemProbeState changedClass = changedEntry(
                initial,
                "etherology:etheroscope",
                entry -> new MaterialItemProbeState.MaterialItemEntry(
                        entry.itemIdentity(),
                        entry.id(),
                        "wrong.Item",
                        entry.maxCount(),
                        entry.serializedId(),
                        entry.serializedCount(),
                        entry.serializedKeys(),
                        entry.roundTripExact(),
                        entry.saveRepresentation()
                )
        );
        MaterialItemProbeState changedMaxCount = changedEntry(
                initial,
                "etherology:enriched_attrahite",
                entry -> new MaterialItemProbeState.MaterialItemEntry(
                        entry.itemIdentity(),
                        entry.id(),
                        entry.runtimeClass(),
                        64,
                        entry.serializedId(),
                        entry.serializedCount(),
                        entry.serializedKeys(),
                        entry.roundTripExact(),
                        entry.saveRepresentation()
                )
        );
        MaterialItemProbeState changedNbt = changedEntry(
                initial,
                "etherology:resonating_wand",
                entry -> new MaterialItemProbeState.MaterialItemEntry(
                        entry.itemIdentity(),
                        entry.id(),
                        entry.runtimeClass(),
                        entry.maxCount(),
                        entry.serializedId(),
                        1,
                        entry.serializedKeys(),
                        false,
                        "wrong"
                )
        );

        assertTrue(initial.hasSameRegistry(changedClass));
        assertFalse(initial.hasSameProperties(changedClass));
        assertTrue(initial.hasSameStackNbt(changedClass));
        assertTrue(initial.hasSameRegistry(changedMaxCount));
        assertFalse(initial.hasSameProperties(changedMaxCount));
        assertTrue(initial.hasSameStackNbt(changedMaxCount));
        assertTrue(initial.hasSameRegistry(changedNbt));
        assertTrue(initial.hasSameProperties(changedNbt));
        assertFalse(initial.hasSameStackNbt(changedNbt));
    }

    private static MaterialItemProbeState exactState(
            Map<String, Object> identityOverrides
    ) {
        Map<String, MaterialItemProbeState.MaterialItemEntry> entries =
                new LinkedHashMap<>();
        MaterialItemProbeState.EXPECTED_MAX_COUNTS.forEach((id, maxCount) ->
                entries.put(
                        id,
                        exactEntry(
                                identityOverrides.getOrDefault(id, new Object()),
                                id,
                                maxCount
                        )
                )
        );
        return new MaterialItemProbeState(
                "",
                MaterialItemProbeState.EXPECTED_ITEM_IDS,
                Collections.unmodifiableMap(entries)
        );
    }

    private static MaterialItemProbeState.MaterialItemEntry exactEntry(
            Object identity,
            String id,
            int maxCount
    ) {
        String representation = id
                + "|class=" + MaterialItemProbeState.VANILLA_ITEM_CLASS
                + "|max=" + maxCount
                + "|nbt_id=" + id
                + "|nbt_count=" + maxCount
                + "|nbt_keys=Count+id";
        return new MaterialItemProbeState.MaterialItemEntry(
                identity,
                id,
                MaterialItemProbeState.VANILLA_ITEM_CLASS,
                maxCount,
                id,
                maxCount,
                List.of("Count", "id"),
                true,
                representation
        );
    }

    private static Map<String, Object> identities() {
        Map<String, Object> identities = new LinkedHashMap<>();
        MaterialItemProbeState.EXPECTED_ITEM_IDS.forEach(
                id -> identities.put(id, new Object())
        );
        return Collections.unmodifiableMap(identities);
    }

    private static MaterialItemProbeState changedEntry(
            MaterialItemProbeState state,
            String id,
            java.util.function.UnaryOperator<MaterialItemProbeState.MaterialItemEntry> change
    ) {
        Map<String, MaterialItemProbeState.MaterialItemEntry> entries =
                new LinkedHashMap<>(state.entries());
        entries.computeIfPresent(id, (ignored, entry) -> change.apply(entry));
        return new MaterialItemProbeState(
                state.captureError(),
                state.materialItemIds(),
                Collections.unmodifiableMap(entries)
        );
    }
}
