package dev.theplumteam.etherology.e2e.server;

import net.minecraft.block.Block;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.PressurePlateBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBlock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SlitheriteBlockProbeStateTest {

    @Test
    void canonicalInventoryMatchesTheAcceptedSeventeenBlockBaseline() {
        assertEquals(
                List.of(
                        "etherology:slitherite",
                        "etherology:slitherite_stairs",
                        "etherology:slitherite_slab",
                        "etherology:slitherite_wall",
                        "etherology:polished_slitherite",
                        "etherology:polished_slitherite_stairs",
                        "etherology:polished_slitherite_slab",
                        "etherology:polished_slitherite_wall",
                        "etherology:polished_slitherite_button",
                        "etherology:polished_slitherite_pressure_plate",
                        "etherology:polished_slitherite_bricks",
                        "etherology:polished_slitherite_brick_stairs",
                        "etherology:polished_slitherite_brick_slab",
                        "etherology:polished_slitherite_brick_wall",
                        "etherology:chiseled_polished_slitherite",
                        "etherology:chiseled_polished_slitherite_bricks",
                        "etherology:cracked_polished_slitherite_bricks"
                ),
                SlitheriteBlockProbeState.EXPECTED_BLOCK_IDS
        );
        assertEquals(
                List.of(
                        Block.class.getName(),
                        StairsBlock.class.getName(),
                        SlabBlock.class.getName(),
                        WallBlock.class.getName(),
                        Block.class.getName(),
                        StairsBlock.class.getName(),
                        SlabBlock.class.getName(),
                        WallBlock.class.getName(),
                        ButtonBlock.class.getName(),
                        PressurePlateBlock.class.getName(),
                        Block.class.getName(),
                        StairsBlock.class.getName(),
                        SlabBlock.class.getName(),
                        WallBlock.class.getName(),
                        Block.class.getName(),
                        Block.class.getName(),
                        Block.class.getName()
                ),
                SlitheriteBlockProbeState.EXPECTED_BLOCKS.values().stream()
                        .map(SlitheriteBlockProbeState.SlitheriteBlockSpec::blockClass)
                        .toList()
        );
        List<Integer> stateCounts = SlitheriteBlockProbeState.EXPECTED_BLOCKS
                .values()
                .stream()
                .map(SlitheriteBlockProbeState.SlitheriteBlockSpec::stateCount)
                .toList();
        assertEquals(
                List.of(1, 80, 6, 324, 1, 80, 6, 324, 24, 2,
                        1, 80, 6, 324, 1, 1, 1),
                stateCounts
        );
        assertEquals(
                SlitheriteBlockProbeState.EXPECTED_AGGREGATE_STATE_COUNT,
                stateCounts.stream().mapToInt(Integer::intValue).sum()
        );
        assertEquals(
                Map.of(
                        "facing", "north",
                        "half", "bottom",
                        "shape", "straight",
                        "waterlogged", "false"
                ),
                SlitheriteBlockProbeState.EXPECTED_BLOCKS.get(
                        "etherology:slitherite_stairs"
                ).defaultProperties()
        );
        assertEquals(
                "etherology:polished_slitherite_button"
                        + "[face=wall,facing=north,powered=false]",
                SlitheriteBlockProbeState.EXPECTED_BLOCKS.get(
                        "etherology:polished_slitherite_button"
                ).defaultState()
        );
        assertEquals(
                List.of(
                        "etherology:slitherite_slab",
                        "etherology:polished_slitherite_slab",
                        "etherology:polished_slitherite_brick_slab"
                ),
                expectedIdsWith(
                        SlitheriteBlockProbeState.SlitheriteBlockSpec::slab
                )
        );
        assertEquals(
                List.of(
                        "etherology:slitherite_stairs",
                        "etherology:polished_slitherite_stairs",
                        "etherology:polished_slitherite_brick_stairs"
                ),
                expectedIdsWith(
                        SlitheriteBlockProbeState.SlitheriteBlockSpec::stairs
                )
        );
        assertEquals(
                List.of(
                        "etherology:slitherite_wall",
                        "etherology:polished_slitherite_wall",
                        "etherology:polished_slitherite_brick_wall"
                ),
                expectedIdsWith(
                        SlitheriteBlockProbeState.SlitheriteBlockSpec::wall
                )
        );
        assertEquals(
                List.of(
                        "etherology:polished_slitherite_bricks",
                        "etherology:chiseled_polished_slitherite_bricks",
                        "etherology:cracked_polished_slitherite_bricks"
                ),
                expectedIdsWith(
                        SlitheriteBlockProbeState.SlitheriteBlockSpec::stoneBrick
                )
        );
        assertEquals(
                List.of("etherology:polished_slitherite_pressure_plate"),
                expectedIdsWith(
                        SlitheriteBlockProbeState.SlitheriteBlockSpec::pressurePlate
                )
        );
    }

    @Test
    void canonicalDataInventoryMatchesLootRecipesAndAdvancements() {
        assertEquals(
                17,
                SlitheriteBlockProbeState.LoadedData.EXPECTED_LOOT_TABLE_IDS.size()
        );
        assertEquals(
                17,
                SlitheriteBlockProbeState.LoadedData.EXPECTED_SELF_DROPS.size()
        );
        assertEquals(
                Map.of(
                        "etherology:polished_slitherite_brick_slab",
                        "etherology:polished_slitherite_brick_slabx1",
                        "etherology:polished_slitherite_slab",
                        "etherology:polished_slitherite_slabx1",
                        "etherology:slitherite_slab",
                        "etherology:slitherite_slabx1"
                ),
                SlitheriteBlockProbeState.LoadedData.EXPECTED_DOUBLE_SLAB_DROPS
        );
        assertEquals(
                29,
                SlitheriteBlockProbeState.LoadedData.EXPECTED_RECIPE_IDS.size()
        );
        assertEquals(
                29,
                SlitheriteBlockProbeState.LoadedData.EXPECTED_ADVANCEMENT_IDS.size()
        );
        assertEquals(
                5,
                SlitheriteBlockProbeState.LoadedData
                        .EXPECTED_RELATED_RECIPE_IDS.size()
        );
        assertEquals(
                "etherology:polished_slitherite=minecraft:crafting"
                        + "->etherology:polished_slitheritex4",
                SlitheriteBlockProbeState.LoadedData.EXPECTED_RECIPES.get(
                        "etherology:polished_slitherite"
                )
        );
        assertTrue(SlitheriteBlockProbeState.LoadedData.EXPECTED_ADVANCEMENT_IDS
                .contains(
                        "etherology:recipes/redstone/"
                                + "polished_slitherite_pressure_plate"
                ));
        assertEquals(
                "etherology:unadjusted_lens=etherology:alchemy_recipe"
                        + "->etherology:unadjusted_lensx1",
                SlitheriteBlockProbeState.LoadedData.EXPECTED_RELATED_RECIPES.get(
                        "etherology:unadjusted_lens"
                )
        );
        assertEquals(
                "etherology:comparator=minecraft:crafting->minecraft:comparatorx1,"
                        + "etherology:repeater=minecraft:crafting->minecraft:repeaterx1,"
                        + "etherology:stonecutter=minecraft:crafting->minecraft:stonecutterx1,"
                        + "etherology:pedestal=minecraft:crafting->etherology:pedestalx2,"
                        + "etherology:unadjusted_lens=etherology:alchemy_recipe"
                        + "->etherology:unadjusted_lensx1",
                SlitheriteBlockProbeState.LoadedData
                        .expectedCanonicalRelatedRecipes()
        );
        assertTrue(SlitheriteBlockProbeState.LoadedData.expectedCanonicalRecipes()
                .startsWith(
                        "etherology:chiseled_polished_slitherite="
                                + "minecraft:crafting->"
                ));
    }

    @Test
    void loadedDataCatalogsAreExactAndImmutable() {
        assertEquals(
                etherologyIds(
                        "chiseled_polished_slitherite",
                        "chiseled_polished_slitherite_bricks",
                        "chiseled_polished_slitherite_bricks_from_"
                                + "polished_slitherite_bricks_stonecutting",
                        "chiseled_polished_slitherite_from_"
                                + "polished_slitherite_stonecutting",
                        "cracked_polished_slitherite_bricks",
                        "polished_slitherite",
                        "polished_slitherite_brick_slab",
                        "polished_slitherite_brick_slab_from_"
                                + "polished_slitherite_bricks_stonecutting",
                        "polished_slitherite_brick_stairs",
                        "polished_slitherite_brick_stairs_from_"
                                + "polished_slitherite_bricks_stonecutting",
                        "polished_slitherite_brick_wall",
                        "polished_slitherite_brick_wall_from_"
                                + "polished_slitherite_bricks_stonecutting",
                        "polished_slitherite_bricks",
                        "polished_slitherite_bricks_from_"
                                + "polished_slitherite_stonecutting",
                        "polished_slitherite_button",
                        "polished_slitherite_from_slitherite_stonecutting",
                        "polished_slitherite_pressure_plate",
                        "polished_slitherite_slab",
                        "polished_slitherite_slab_from_"
                                + "polished_slitherite_stonecutting",
                        "polished_slitherite_stairs",
                        "polished_slitherite_stairs_from_"
                                + "polished_slitherite_stonecutting",
                        "polished_slitherite_wall",
                        "polished_slitherite_wall_from_"
                                + "polished_slitherite_stonecutting",
                        "slitherite_slab",
                        "slitherite_slab_from_slitherite_stonecutting",
                        "slitherite_stairs",
                        "slitherite_stairs_from_slitherite_stonecutting",
                        "slitherite_wall",
                        "slitherite_wall_from_slitherite_stonecutting"
                ),
                SlitheriteBlockProbeState.LoadedData.EXPECTED_RECIPE_IDS
        );
        assertEquals(
                etherologyIds(
                        "recipes/building_blocks/chiseled_polished_slitherite",
                        "recipes/building_blocks/"
                                + "chiseled_polished_slitherite_bricks",
                        "recipes/building_blocks/"
                                + "chiseled_polished_slitherite_bricks_from_"
                                + "polished_slitherite_bricks_stonecutting",
                        "recipes/building_blocks/"
                                + "chiseled_polished_slitherite_from_"
                                + "polished_slitherite_stonecutting",
                        "recipes/building_blocks/"
                                + "cracked_polished_slitherite_bricks",
                        "recipes/building_blocks/polished_slitherite",
                        "recipes/building_blocks/polished_slitherite_brick_slab",
                        "recipes/building_blocks/"
                                + "polished_slitherite_brick_slab_from_"
                                + "polished_slitherite_bricks_stonecutting",
                        "recipes/building_blocks/"
                                + "polished_slitherite_brick_stairs",
                        "recipes/building_blocks/"
                                + "polished_slitherite_brick_stairs_from_"
                                + "polished_slitherite_bricks_stonecutting",
                        "recipes/building_blocks/polished_slitherite_bricks",
                        "recipes/building_blocks/"
                                + "polished_slitherite_bricks_from_"
                                + "polished_slitherite_stonecutting",
                        "recipes/building_blocks/"
                                + "polished_slitherite_from_slitherite_stonecutting",
                        "recipes/building_blocks/polished_slitherite_slab",
                        "recipes/building_blocks/"
                                + "polished_slitherite_slab_from_"
                                + "polished_slitherite_stonecutting",
                        "recipes/building_blocks/polished_slitherite_stairs",
                        "recipes/building_blocks/"
                                + "polished_slitherite_stairs_from_"
                                + "polished_slitherite_stonecutting",
                        "recipes/building_blocks/slitherite_slab",
                        "recipes/building_blocks/"
                                + "slitherite_slab_from_slitherite_stonecutting",
                        "recipes/building_blocks/slitherite_stairs",
                        "recipes/building_blocks/"
                                + "slitherite_stairs_from_slitherite_stonecutting",
                        "recipes/decorations/polished_slitherite_brick_wall",
                        "recipes/decorations/"
                                + "polished_slitherite_brick_wall_from_"
                                + "polished_slitherite_bricks_stonecutting",
                        "recipes/decorations/polished_slitherite_wall",
                        "recipes/decorations/polished_slitherite_wall_from_"
                                + "polished_slitherite_stonecutting",
                        "recipes/decorations/slitherite_wall",
                        "recipes/decorations/"
                                + "slitherite_wall_from_slitherite_stonecutting",
                        "recipes/redstone/polished_slitherite_button",
                        "recipes/redstone/polished_slitherite_pressure_plate"
                ),
                SlitheriteBlockProbeState.LoadedData.EXPECTED_ADVANCEMENT_IDS
        );
        assertEquals(
                etherologyIds(
                        "comparator",
                        "repeater",
                        "stonecutter",
                        "pedestal",
                        "unadjusted_lens"
                ),
                SlitheriteBlockProbeState.LoadedData.EXPECTED_RELATED_RECIPE_IDS
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> SlitheriteBlockProbeState.EXPECTED_BLOCKS.clear()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> SlitheriteBlockProbeState.EXPECTED_BLOCK_IDS.clear()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> SlitheriteBlockProbeState.LoadedData
                        .EXPECTED_RECIPE_SPECS.clear()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> SlitheriteBlockProbeState.LoadedData
                        .EXPECTED_RELATED_RECIPE_SPECS.clear()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> SlitheriteBlockProbeState.LoadedData
                        .EXPECTED_ADVANCEMENT_IDS.clear()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> SlitheriteBlockProbeState.LoadedData
                        .EXPECTED_LOOT_TABLE_IDS.clear()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> SlitheriteBlockProbeState.LoadedData
                        .EXPECTED_SELF_DROPS.clear()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> SlitheriteBlockProbeState.LoadedData.EXPECTED_RECIPES.clear()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> SlitheriteBlockProbeState.LoadedData
                        .EXPECTED_RELATED_RECIPES.clear()
        );
    }

    @Test
    void exactStateRecognizesRegistryStatesRawIdsTagsAndLoadedData() {
        SlitheriteBlockProbeState state = exactState(Map.of(), Map.of(), exactLoadedData());

        assertTrue(state.hasExactRegistry());
        assertTrue(state.hasExactRuntimeClasses());
        assertTrue(state.hasExactBlockItemMappings());
        assertTrue(state.hasExactDefaultStates());
        assertTrue(state.hasExactRawIds());
        assertTrue(state.hasExactTagMemberships());
        assertTrue(state.hasExactCoreContract());
        assertTrue(state.hasExactContract());
        assertEquals(
                SlitheriteBlockProbeState.expectedCanonicalProperties(),
                state.canonicalProperties()
        );
        assertEquals(
                SlitheriteBlockProbeState.expectedCanonicalTags(),
                state.canonicalTags()
        );
        assertTrue(state.canonicalRegistry().contains("|default_raw_id="));
    }

    @Test
    void missingStateRejectsEveryExactMilestone() {
        SlitheriteBlockProbeState state = SlitheriteBlockProbeState.missing();

        assertFalse(state.hasExactRegistry());
        assertFalse(state.hasExactRuntimeClasses());
        assertFalse(state.hasExactBlockItemMappings());
        assertFalse(state.hasExactDefaultStates());
        assertFalse(state.hasExactRawIds());
        assertFalse(state.hasExactTagMemberships());
        assertFalse(state.hasExactCoreContract());
        assertFalse(state.hasExactContract());
    }

    @Test
    void registryComparisonRequiresStableRuntimeObjects() {
        Map<String, Object> blockIdentities = identities(
                SlitheriteBlockProbeState.EXPECTED_BLOCK_IDS,
                ignored -> new Object()
        );
        Map<String, Object> itemIdentities = identities(
                SlitheriteBlockProbeState.EXPECTED_BLOCK_IDS,
                ignored -> new Object()
        );
        SlitheriteBlockProbeState initial = exactState(
                blockIdentities,
                itemIdentities,
                exactLoadedData()
        );

        assertTrue(initial.hasSameRegistry(exactState(
                blockIdentities,
                itemIdentities,
                initial.loadedData()
        )));
        assertFalse(initial.hasSameRegistry(exactState(
                Map.of("etherology:slitherite", new Object()),
                itemIdentities,
                initial.loadedData()
        )));
    }

    @Test
    void rawIdContractRejectsAggregateAndPerBlockDrift() {
        SlitheriteBlockProbeState exact = exactState(Map.of(), Map.of(), exactLoadedData());
        SlitheriteBlockProbeState wrongAggregate = new SlitheriteBlockProbeState(
                exact.captureError(),
                exact.blockIds(),
                exact.blockItemIds(),
                exact.entries(),
                exact.aggregateStateCount() - 1,
                exact.aggregateUniqueRawIdCount(),
                exact.loadedData()
        );
        Map<String, SlitheriteBlockProbeState.SlitheriteBlockEntry> entries =
                new LinkedHashMap<>(exact.entries());
        SlitheriteBlockProbeState.SlitheriteBlockEntry slitherite = entries.get(
                "etherology:slitherite"
        );
        entries.put(
                "etherology:slitherite",
                copyEntryWithRawIds(slitherite, -1, List.of(-1))
        );
        SlitheriteBlockProbeState wrongPerBlock = new SlitheriteBlockProbeState(
                exact.captureError(),
                exact.blockIds(),
                exact.blockItemIds(),
                Collections.unmodifiableMap(entries),
                exact.aggregateStateCount(),
                exact.aggregateUniqueRawIdCount(),
                exact.loadedData()
        );
        Map<String, SlitheriteBlockProbeState.SlitheriteBlockEntry> duplicateEntries =
                new LinkedHashMap<>(exact.entries());
        SlitheriteBlockProbeState.SlitheriteBlockEntry polished = duplicateEntries.get(
                "etherology:polished_slitherite"
        );
        duplicateEntries.put(
                "etherology:polished_slitherite",
                copyEntryWithRawIds(
                        polished,
                        slitherite.defaultStateRawId(),
                        slitherite.stateRawIds()
                )
        );
        SlitheriteBlockProbeState duplicateAcrossBlocks = new SlitheriteBlockProbeState(
                exact.captureError(),
                exact.blockIds(),
                exact.blockItemIds(),
                Collections.unmodifiableMap(duplicateEntries),
                exact.aggregateStateCount(),
                exact.aggregateUniqueRawIdCount(),
                exact.loadedData()
        );
        Map<String, SlitheriteBlockProbeState.SlitheriteBlockEntry> unsortedEntries =
                new LinkedHashMap<>(exact.entries());
        SlitheriteBlockProbeState.SlitheriteBlockEntry stairs = unsortedEntries.get(
                "etherology:slitherite_stairs"
        );
        List<Integer> unsortedRawIds = new ArrayList<>(stairs.stateRawIds());
        Collections.reverse(unsortedRawIds);
        unsortedEntries.put(
                "etherology:slitherite_stairs",
                copyEntryWithRawIds(
                        stairs,
                        stairs.defaultStateRawId(),
                        Collections.unmodifiableList(unsortedRawIds)
                )
        );
        SlitheriteBlockProbeState unsortedPerBlock = new SlitheriteBlockProbeState(
                exact.captureError(),
                exact.blockIds(),
                exact.blockItemIds(),
                Collections.unmodifiableMap(unsortedEntries),
                exact.aggregateStateCount(),
                exact.aggregateUniqueRawIdCount(),
                exact.loadedData()
        );

        assertFalse(wrongAggregate.hasExactRawIds());
        assertFalse(wrongPerBlock.hasExactRawIds());
        assertFalse(duplicateAcrossBlocks.hasExactRawIds());
        assertFalse(unsortedPerBlock.hasExactRawIds());
        assertFalse(exact.hasSameDefaultStatesAndRawIds(wrongPerBlock));
    }

    @Test
    void placementSnapshotRequiresExactPositionsStatesAndSupports() {
        SlitheriteBlockProbeState.PlacementState exact =
                new SlitheriteBlockProbeState.PlacementState(
                        "",
                        SlitheriteBlockProbeState.PlacementState.expectedPositions(),
                        SlitheriteBlockProbeState.PlacementState.expectedSupports(),
                        SlitheriteBlockProbeState.PlacementState.expectedBlockIds(),
                        SlitheriteBlockProbeState.PlacementState.expectedStates(),
                        SlitheriteBlockProbeState.PlacementState.expectedSupportIds()
                );
        Map<String, String> changedStates = new LinkedHashMap<>(exact.placedStates());
        changedStates.put(
                "etherology:polished_slitherite_button",
                "etherology:polished_slitherite_button"
                        + "[face=wall,facing=north,powered=true]"
        );
        SlitheriteBlockProbeState.PlacementState changed =
                new SlitheriteBlockProbeState.PlacementState(
                        "",
                        exact.positions(),
                        exact.supportPositions(),
                        exact.placedBlockIds(),
                        Collections.unmodifiableMap(changedStates),
                        exact.supportBlockIds()
                );

        assertTrue(exact.hasExactPlacement());
        assertTrue(exact.samePlacement(exact));
        assertFalse(changed.hasExactPlacement());
        assertFalse(exact.samePlacement(changed));
        assertTrue(exact.canonicalSupportBlockIds().contains(
                "etherology:polished_slitherite_button=minecraft:smooth_stone"
        ));
    }

    @Test
    void loadedDataSeparatesStableOutcomesFromReloadedInstances() {
        Map<String, Object> lootIdentities = identities(
                SlitheriteBlockProbeState.LoadedData.EXPECTED_LOOT_TABLE_IDS,
                ignored -> new Object()
        );
        List<String> allRecipeIds = allRecipeIds();
        Map<String, Object> recipeIdentities = identities(
                allRecipeIds,
                ignored -> new Object()
        );
        Map<String, Object> advancementIdentities = identities(
                SlitheriteBlockProbeState.LoadedData.EXPECTED_ADVANCEMENT_IDS,
                ignored -> new Object()
        );
        SlitheriteBlockProbeState.LoadedData initial = exactLoadedData(
                lootIdentities,
                recipeIdentities,
                advancementIdentities
        );
        SlitheriteBlockProbeState.LoadedData sameInstances = exactLoadedData(
                lootIdentities,
                recipeIdentities,
                advancementIdentities
        );
        SlitheriteBlockProbeState.LoadedData freshInstances = exactLoadedData();
        List<String> extraAdvancementIds = new ArrayList<>(
                initial.advancementIds()
        );
        extraAdvancementIds.add(
                "etherology:recipes/building_blocks/unexpected_slitherite"
        );
        Map<String, Object> extraAdvancementIdentities = new LinkedHashMap<>(
                initial.advancementIdentities()
        );
        extraAdvancementIdentities.put(
                "etherology:recipes/building_blocks/unexpected_slitherite",
                new Object()
        );
        SlitheriteBlockProbeState.LoadedData unexpectedAdvancement =
                new SlitheriteBlockProbeState.LoadedData(
                        initial.lootTableIdentities(),
                        initial.recipeIdentities(),
                        Collections.unmodifiableMap(extraAdvancementIdentities),
                        initial.captureError(),
                        initial.lootTableIds(),
                        initial.selfDrops(),
                        initial.doubleSlabDrops(),
                        initial.recipeIds(),
                        initial.recipes(),
                        List.copyOf(extraAdvancementIds),
                        initial.relatedRecipeIds(),
                        initial.relatedRecipes()
                );

        assertTrue(initial.hasExactLoot());
        assertTrue(initial.hasExactRecipes());
        assertTrue(initial.hasExactAdvancements());
        assertTrue(initial.hasExactContract());
        assertTrue(initial.sameOutcome(freshInstances));
        assertTrue(initial.sameInstances(sameInstances));
        assertFalse(initial.sameInstances(freshInstances));
        assertTrue(initial.hasFreshInstances(freshInstances));
        assertFalse(initial.hasFreshInstances(sameInstances));
        assertFalse(unexpectedAdvancement.hasExactAdvancements());
        assertEquals(
                SlitheriteBlockProbeState.LoadedData.expectedCanonicalSelfDrops(),
                initial.canonicalSelfDrops()
        );
        assertEquals(
                SlitheriteBlockProbeState.LoadedData
                        .expectedCanonicalDoubleSlabDrops(),
                initial.canonicalDoubleSlabDrops()
        );
        assertEquals(
                SlitheriteBlockProbeState.LoadedData.expectedCanonicalRecipes(),
                initial.canonicalRecipes()
        );
        assertEquals(
                SlitheriteBlockProbeState.LoadedData
                        .expectedCanonicalRelatedRecipes(),
                initial.canonicalRelatedRecipes()
        );
    }

    @Test
    void missingLoadedDataRejectsAllDataMilestones() {
        SlitheriteBlockProbeState.LoadedData missing =
                SlitheriteBlockProbeState.LoadedData.missing();

        assertFalse(missing.hasExactLoot());
        assertFalse(missing.hasExactRecipes());
        assertFalse(missing.hasExactAdvancements());
        assertFalse(missing.hasExactContract());
        assertFalse(missing.sameInstances(missing));
        assertFalse(missing.hasFreshInstances(missing));
    }

    @Test
    void relevantDataInventoryPredicatesExposeUnexpectedSlitheriteEntries() {
        assertTrue(
                SlitheriteBlockProbeState.LoadedData.EXPECTED_LOOT_TABLE_IDS
                        .stream()
                        .allMatch(
                                SlitheriteBlockProbeState.LoadedData
                                        ::isOwnedSlitheriteLootTableId
                        )
        );
        assertTrue(
                SlitheriteBlockProbeState.LoadedData.EXPECTED_RECIPE_IDS
                        .stream()
                        .allMatch(
                                SlitheriteBlockProbeState.LoadedData
                                        ::isOwnedSlitheriteRecipeId
                        )
        );
        assertTrue(
                SlitheriteBlockProbeState.LoadedData.isOwnedSlitheriteLootTableId(
                        "etherology:blocks/unexpected_slitherite"
                )
        );
        assertTrue(
                SlitheriteBlockProbeState.LoadedData.isOwnedSlitheriteRecipeId(
                        "etherology:unexpected_slitherite_conversion"
                )
        );
        assertFalse(
                SlitheriteBlockProbeState.LoadedData.isOwnedSlitheriteLootTableId(
                        "other:blocks/slitherite"
                )
        );
        assertFalse(
                SlitheriteBlockProbeState.LoadedData.isOwnedSlitheriteRecipeId(
                        "etherology:comparator"
                )
        );
    }

    private static SlitheriteBlockProbeState exactState(
            Map<String, Object> blockIdentityOverrides,
            Map<String, Object> itemIdentityOverrides,
            SlitheriteBlockProbeState.LoadedData loadedData
    ) {
        Map<String, SlitheriteBlockProbeState.SlitheriteBlockEntry> entries =
                new LinkedHashMap<>();
        int nextRawId = 1000;
        for (Map.Entry<String, SlitheriteBlockProbeState.SlitheriteBlockSpec> expected :
                SlitheriteBlockProbeState.EXPECTED_BLOCKS.entrySet()) {
            String id = expected.getKey();
            SlitheriteBlockProbeState.SlitheriteBlockSpec spec = expected.getValue();
            List<Integer> rawIds = IntStream.range(
                    nextRawId,
                    nextRawId + spec.stateCount()
            ).boxed().toList();
            entries.put(
                    id,
                    exactEntry(
                            blockIdentityOverrides.getOrDefault(id, new Object()),
                            itemIdentityOverrides.getOrDefault(id, new Object()),
                            id,
                            spec,
                            rawIds
                    )
            );
            nextRawId += spec.stateCount();
        }
        return new SlitheriteBlockProbeState(
                "",
                SlitheriteBlockProbeState.EXPECTED_BLOCK_IDS,
                SlitheriteBlockProbeState.EXPECTED_BLOCK_IDS,
                Collections.unmodifiableMap(entries),
                SlitheriteBlockProbeState.EXPECTED_AGGREGATE_STATE_COUNT,
                SlitheriteBlockProbeState.EXPECTED_AGGREGATE_STATE_COUNT,
                loadedData
        );
    }

    private static SlitheriteBlockProbeState.SlitheriteBlockEntry exactEntry(
            Object blockIdentity,
            Object itemIdentity,
            String id,
            SlitheriteBlockProbeState.SlitheriteBlockSpec spec,
            List<Integer> rawIds
    ) {
        return new SlitheriteBlockProbeState.SlitheriteBlockEntry(
                blockIdentity,
                itemIdentity,
                id,
                id,
                spec.blockClass(),
                SlitheriteBlockProbeState.BLOCK_ITEM_CLASS,
                true,
                true,
                true,
                spec.defaultProperties(),
                spec.defaultState(),
                spec.stateCount(),
                rawIds.get(0),
                rawIds,
                true,
                false,
                spec.slab(),
                spec.slab(),
                spec.stairs(),
                spec.stairs(),
                spec.wall(),
                spec.wall(),
                spec.stoneBrick(),
                spec.pressurePlate(),
                false
        );
    }

    private static SlitheriteBlockProbeState.SlitheriteBlockEntry copyEntryWithRawIds(
            SlitheriteBlockProbeState.SlitheriteBlockEntry entry,
            int defaultRawId,
            List<Integer> rawIds
    ) {
        return new SlitheriteBlockProbeState.SlitheriteBlockEntry(
                entry.blockIdentity(),
                entry.itemIdentity(),
                entry.blockId(),
                entry.itemId(),
                entry.blockClass(),
                entry.itemClass(),
                entry.blockItem(),
                entry.blockItemMapsToBlock(),
                entry.blockAsItemMatches(),
                entry.defaultProperties(),
                entry.defaultState(),
                entry.stateCount(),
                defaultRawId,
                rawIds,
                entry.pickaxeMineable(),
                entry.needsStoneTool(),
                entry.blockSlab(),
                entry.itemSlab(),
                entry.blockStairs(),
                entry.itemStairs(),
                entry.blockWall(),
                entry.itemWall(),
                entry.blockStoneBrick(),
                entry.blockStonePressurePlate(),
                entry.itemButton()
        );
    }

    private static SlitheriteBlockProbeState.LoadedData exactLoadedData() {
        return exactLoadedData(
                identities(
                        SlitheriteBlockProbeState.LoadedData.EXPECTED_LOOT_TABLE_IDS,
                        ignored -> new Object()
                ),
                identities(allRecipeIds(), ignored -> new Object()),
                identities(
                        SlitheriteBlockProbeState.LoadedData.EXPECTED_ADVANCEMENT_IDS,
                        ignored -> new Object()
                )
        );
    }

    private static SlitheriteBlockProbeState.LoadedData exactLoadedData(
            Map<String, Object> lootIdentities,
            Map<String, Object> recipeIdentities,
            Map<String, Object> advancementIdentities
    ) {
        return new SlitheriteBlockProbeState.LoadedData(
                lootIdentities,
                recipeIdentities,
                advancementIdentities,
                "",
                SlitheriteBlockProbeState.LoadedData.EXPECTED_LOOT_TABLE_IDS,
                SlitheriteBlockProbeState.LoadedData.EXPECTED_SELF_DROPS,
                SlitheriteBlockProbeState.LoadedData.EXPECTED_DOUBLE_SLAB_DROPS,
                SlitheriteBlockProbeState.LoadedData.EXPECTED_RECIPE_IDS,
                SlitheriteBlockProbeState.LoadedData.EXPECTED_RECIPES,
                SlitheriteBlockProbeState.LoadedData.EXPECTED_ADVANCEMENT_IDS,
                SlitheriteBlockProbeState.LoadedData.EXPECTED_RELATED_RECIPE_IDS,
                SlitheriteBlockProbeState.LoadedData.EXPECTED_RELATED_RECIPES
        );
    }

    private static List<String> allRecipeIds() {
        List<String> ids = new ArrayList<>(
                SlitheriteBlockProbeState.LoadedData.EXPECTED_RECIPE_IDS
        );
        ids.addAll(
                SlitheriteBlockProbeState.LoadedData.EXPECTED_RELATED_RECIPE_IDS
        );
        return List.copyOf(ids);
    }

    private static List<String> etherologyIds(String... paths) {
        return java.util.Arrays.stream(paths)
                .map(path -> "etherology:" + path)
                .toList();
    }

    private static List<String> expectedIdsWith(
            Predicate<SlitheriteBlockProbeState.SlitheriteBlockSpec> selector
    ) {
        return SlitheriteBlockProbeState.EXPECTED_BLOCKS.entrySet()
                .stream()
                .filter(entry -> selector.test(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static Map<String, Object> identities(
            List<String> ids,
            Function<String, Object> factory
    ) {
        Map<String, Object> identities = new LinkedHashMap<>();
        ids.forEach(id -> identities.put(id, factory.apply(id)));
        return Collections.unmodifiableMap(identities);
    }
}
