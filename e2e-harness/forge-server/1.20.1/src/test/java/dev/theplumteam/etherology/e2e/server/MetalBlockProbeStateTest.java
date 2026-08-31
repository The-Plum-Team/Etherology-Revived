package dev.theplumteam.etherology.e2e.server;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MetalBlockProbeStateTest {

    @Test
    void canonicalInventoryContainsTheExactThreeMetalBlocks() {
        assertEquals(
                List.of(
                        "etherology:azel_block",
                        "etherology:ebony_block",
                        "etherology:ethril_block"
                ),
                MetalBlockProbeState.EXPECTED_BLOCK_IDS
        );
        assertEquals(
                new MetalBlockProbeState.MetalBlockSpec(5.0F, 6.0F, 32, false),
                MetalBlockProbeState.EXPECTED_METAL_BLOCKS.get(
                        "etherology:azel_block"
                )
        );
        assertEquals(
                new MetalBlockProbeState.MetalBlockSpec(5.0F, 6.0F, 15, true),
                MetalBlockProbeState.EXPECTED_METAL_BLOCKS.get(
                        "etherology:ebony_block"
                )
        );
        assertEquals(
                new MetalBlockProbeState.MetalBlockSpec(3.0F, 6.0F, 30, true),
                MetalBlockProbeState.EXPECTED_METAL_BLOCKS.get(
                        "etherology:ethril_block"
                )
        );
    }

    @Test
    void exactStateRecognizesRegistryMappingsPropertiesTagsAndStackNbt() {
        MetalBlockProbeState state = exactState(Map.of(), Map.of());

        assertTrue(state.hasExactRegistry());
        assertTrue(state.hasExactRuntimeClasses());
        assertTrue(state.hasExactBlockItemMappings());
        assertTrue(state.hasExactProperties());
        assertTrue(state.hasExactTagMemberships());
        assertTrue(state.hasExactStackNbtRoundTrips());
        assertTrue(state.hasExactContract());
        assertEquals(
                MetalBlockProbeState.expectedCanonicalProperties(),
                state.canonicalProperties()
        );
        assertEquals(
                MetalBlockProbeState.expectedCanonicalSaveRepresentations(),
                state.canonicalSaveRepresentations()
        );
    }

    @Test
    void missingStateRejectsEveryExactMilestone() {
        MetalBlockProbeState state = MetalBlockProbeState.missing();

        assertFalse(state.hasExactRegistry());
        assertFalse(state.hasExactRuntimeClasses());
        assertFalse(state.hasExactBlockItemMappings());
        assertFalse(state.hasExactProperties());
        assertFalse(state.hasExactTagMemberships());
        assertFalse(state.hasExactStackNbtRoundTrips());
        assertFalse(state.hasExactContract());
    }

    @Test
    void registryComparisonRequiresTheSameRuntimeObjectsAndKeyedIds() {
        Map<String, Object> blockIdentities = identities();
        Map<String, Object> itemIdentities = identities();
        MetalBlockProbeState initial = exactState(blockIdentities, itemIdentities);

        assertTrue(initial.sameStateAtServerStarted(
                exactState(blockIdentities, itemIdentities)
        ));
        assertFalse(initial.sameStateAtServerStarted(exactState(
                Map.of("etherology:azel_block", new Object()),
                itemIdentities
        )));
        assertFalse(changedEntry(
                initial,
                "etherology:azel_block",
                entry -> copyEntry(
                        entry,
                        "etherology:ebony_block",
                        entry.itemId(),
                        entry.blockClass(),
                        entry.blockItemMapsToBlock(),
                        entry.hardness(),
                        entry.pickaxeMineable(),
                        entry.serializedCount(),
                        entry.roundTripExact(),
                        entry.saveRepresentation()
                )
        ).hasExactRegistry());
    }

    @Test
    void reloadComparisonsSeparateRegistryPropertiesTagsAndStackNbt() {
        Map<String, Object> blockIdentities = identities();
        Map<String, Object> itemIdentities = identities();
        MetalBlockProbeState initial = exactState(blockIdentities, itemIdentities);
        MetalBlockProbeState changedProperties = changedEntry(
                initial,
                "etherology:ethril_block",
                entry -> copyEntry(
                        entry,
                        entry.blockId(),
                        entry.itemId(),
                        "wrong.Block",
                        false,
                        4.0F,
                        entry.pickaxeMineable(),
                        entry.serializedCount(),
                        entry.roundTripExact(),
                        entry.saveRepresentation()
                )
        );
        MetalBlockProbeState changedTags = changedEntry(
                initial,
                "etherology:ebony_block",
                entry -> copyEntry(
                        entry,
                        entry.blockId(),
                        entry.itemId(),
                        entry.blockClass(),
                        entry.blockItemMapsToBlock(),
                        entry.hardness(),
                        false,
                        entry.serializedCount(),
                        entry.roundTripExact(),
                        entry.saveRepresentation()
                )
        );
        MetalBlockProbeState changedNbt = changedEntry(
                initial,
                "etherology:azel_block",
                entry -> copyEntry(
                        entry,
                        entry.blockId(),
                        entry.itemId(),
                        entry.blockClass(),
                        entry.blockItemMapsToBlock(),
                        entry.hardness(),
                        entry.pickaxeMineable(),
                        1,
                        false,
                        "wrong"
                )
        );

        assertTrue(initial.hasSameRegistry(changedProperties));
        assertFalse(initial.hasSameProperties(changedProperties));
        assertTrue(initial.hasSameTags(changedProperties));
        assertTrue(initial.hasSameStackNbt(changedProperties));
        assertTrue(initial.hasSameRegistry(changedTags));
        assertTrue(initial.hasSameProperties(changedTags));
        assertFalse(initial.hasSameTags(changedTags));
        assertTrue(initial.hasSameStackNbt(changedTags));
        assertTrue(initial.hasSameRegistry(changedNbt));
        assertTrue(initial.hasSameProperties(changedNbt));
        assertTrue(initial.hasSameTags(changedNbt));
        assertFalse(initial.hasSameStackNbt(changedNbt));
    }

    @Test
    void placementStateRequiresExactBoundedPositionsAndIds() {
        Map<String, String> positions = new LinkedHashMap<>();
        positions.put("etherology:azel_block", "8,200,8");
        positions.put("etherology:ebony_block", "9,200,8");
        positions.put("etherology:ethril_block", "10,200,8");
        Map<String, String> placedIds = new LinkedHashMap<>();
        MetalBlockProbeState.EXPECTED_BLOCK_IDS.forEach(
                id -> placedIds.put(id, id)
        );
        MetalBlockProbeState.MetalBlockPlacementState exact =
                new MetalBlockProbeState.MetalBlockPlacementState(
                        "",
                        Collections.unmodifiableMap(positions),
                        Collections.unmodifiableMap(placedIds)
                );
        Map<String, String> changedIds = new LinkedHashMap<>(placedIds);
        changedIds.put("etherology:ethril_block", "minecraft:air");
        MetalBlockProbeState.MetalBlockPlacementState changed =
                new MetalBlockProbeState.MetalBlockPlacementState(
                        "",
                        Collections.unmodifiableMap(positions),
                        Collections.unmodifiableMap(changedIds)
                );

        assertTrue(exact.hasExactPlacement());
        assertTrue(exact.samePlacement(exact));
        assertFalse(changed.hasExactPlacement());
        assertFalse(exact.samePlacement(changed));
        assertEquals(
                MetalBlockProbeState.MetalBlockPlacementState
                        .expectedCanonicalPositions(),
                exact.canonicalPositions()
        );
        assertEquals(
                MetalBlockProbeState.MetalBlockPlacementState
                        .expectedCanonicalPlacedBlockIds(),
                exact.canonicalPlacedBlockIds()
        );
    }

    private static MetalBlockProbeState exactState(
            Map<String, Object> blockIdentityOverrides,
            Map<String, Object> itemIdentityOverrides
    ) {
        Map<String, MetalBlockProbeState.MetalBlockEntry> entries =
                new LinkedHashMap<>();
        MetalBlockProbeState.EXPECTED_METAL_BLOCKS.forEach((id, spec) ->
                entries.put(
                        id,
                        exactEntry(
                                blockIdentityOverrides.getOrDefault(id, new Object()),
                                itemIdentityOverrides.getOrDefault(id, new Object()),
                                id,
                                spec
                        )
                )
        );
        return new MetalBlockProbeState(
                "",
                MetalBlockProbeState.EXPECTED_BLOCK_IDS,
                MetalBlockProbeState.EXPECTED_BLOCK_IDS,
                Collections.unmodifiableMap(entries)
        );
    }

    private static MetalBlockProbeState.MetalBlockEntry exactEntry(
            Object blockIdentity,
            Object itemIdentity,
            String id,
            MetalBlockProbeState.MetalBlockSpec spec
    ) {
        String representation = id
                + "|item_class=" + MetalBlockProbeState.BLOCK_ITEM_CLASS
                + "|max=64|nbt_id=" + id
                + "|nbt_count=64|nbt_keys=Count+id";
        return new MetalBlockProbeState.MetalBlockEntry(
                blockIdentity,
                itemIdentity,
                id,
                id,
                MetalBlockProbeState.VANILLA_BLOCK_CLASS,
                MetalBlockProbeState.BLOCK_ITEM_CLASS,
                true,
                true,
                true,
                spec.hardness(),
                spec.blastResistance(),
                spec.mapColorId(),
                true,
                true,
                0,
                true,
                true,
                true,
                true,
                spec.beaconBase(),
                64,
                id,
                64,
                List.of("Count", "id"),
                true,
                representation
        );
    }

    private static Map<String, Object> identities() {
        Map<String, Object> identities = new LinkedHashMap<>();
        MetalBlockProbeState.EXPECTED_BLOCK_IDS.forEach(
                id -> identities.put(id, new Object())
        );
        return Collections.unmodifiableMap(identities);
    }

    private static MetalBlockProbeState changedEntry(
            MetalBlockProbeState state,
            String id,
            UnaryOperator<MetalBlockProbeState.MetalBlockEntry> change
    ) {
        Map<String, MetalBlockProbeState.MetalBlockEntry> entries =
                new LinkedHashMap<>(state.entries());
        entries.computeIfPresent(id, (ignored, entry) -> change.apply(entry));
        return new MetalBlockProbeState(
                state.captureError(),
                state.metalBlockIds(),
                state.metalBlockItemIds(),
                Collections.unmodifiableMap(entries)
        );
    }

    private static MetalBlockProbeState.MetalBlockEntry copyEntry(
            MetalBlockProbeState.MetalBlockEntry entry,
            String blockId,
            String itemId,
            String blockClass,
            boolean blockItemMapsToBlock,
            float hardness,
            boolean pickaxeMineable,
            int serializedCount,
            boolean roundTripExact,
            String saveRepresentation
    ) {
        return new MetalBlockProbeState.MetalBlockEntry(
                entry.blockIdentity(),
                entry.itemIdentity(),
                blockId,
                itemId,
                blockClass,
                entry.itemClass(),
                entry.blockItem(),
                blockItemMapsToBlock,
                entry.blockAsItemMatches(),
                hardness,
                entry.blastResistance(),
                entry.mapColorId(),
                entry.metalSoundGroup(),
                entry.toolRequired(),
                entry.luminance(),
                entry.opaque(),
                entry.fullCube(),
                pickaxeMineable,
                entry.needsIronTool(),
                entry.beaconBase(),
                entry.maxCount(),
                entry.serializedId(),
                serializedCount,
                entry.serializedKeys(),
                roundTripExact,
                saveRepresentation
        );
    }
}
