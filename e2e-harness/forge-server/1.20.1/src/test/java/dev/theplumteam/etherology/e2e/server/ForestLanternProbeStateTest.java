package dev.theplumteam.etherology.e2e.server;

import net.minecraft.util.math.random.Random;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ForestLanternProbeStateTest {

    @Test
    void canonicalStateInventoryContainsFiveAgesAcrossFourFacings() {
        assertEquals(20, ForestLanternProbeState.EXPECTED_STATES.size());
        assertEquals(20, ForestLanternProbeState.EXPECTED_OUTLINE_SHAPES.size());
        assertEquals(
                List.of(
                        "age=0,facing=east",
                        "age=0,facing=north",
                        "age=0,facing=south",
                        "age=0,facing=west"
                ),
                ForestLanternProbeState.EXPECTED_STATES.subList(0, 4)
        );
        assertEquals(
                List.of(
                        "age=4,facing=east",
                        "age=4,facing=north",
                        "age=4,facing=south",
                        "age=4,facing=west"
                ),
                ForestLanternProbeState.EXPECTED_STATES.subList(16, 20)
        );
        assertEquals(
                "0.25,0.25,0.8125,0.75,0.75,1.0",
                ForestLanternProbeState.EXPECTED_OUTLINE_SHAPES.get(
                        "age=0,facing=north"
                )
        );
        assertEquals(
                "0.0,0.25,0.125,0.75,1.0,0.875",
                ForestLanternProbeState.EXPECTED_OUTLINE_SHAPES.get(
                        "age=4,facing=east"
                )
        );
    }

    @Test
    void loadedDataInventoryFreezesLootRecipesAndAdvancements() {
        assertEquals(
                Map.of(
                        "0", "",
                        "1", "",
                        "2", "",
                        "3", "",
                        "4", "etherology:forest_lanternx1"
                ),
                ForestLanternProbeState.LoadedData.EXPECTED_LOOT_BY_AGE
        );
        assertEquals(
                List.of(
                        "etherology:forest_lantern_crumb",
                        "etherology:forest_lantern_crumb_from_campfire",
                        "etherology:forest_lantern_crumb_from_smoking",
                        "etherology:leather"
                ),
                ForestLanternProbeState.LoadedData.EXPECTED_RECIPE_IDS
        );
        assertEquals(
                List.of(
                        "etherology:recipes/food/forest_lantern_crumb",
                        "etherology:recipes/food/forest_lantern_crumb_from_campfire",
                        "etherology:recipes/food/forest_lantern_crumb_from_smoking",
                        "etherology:recipes/misc/leather"
                ),
                ForestLanternProbeState.LoadedData.EXPECTED_ADVANCEMENT_IDS
        );
        assertEquals(4, ForestLanternProbeState.LoadedData.EXPECTED_RECIPES.size());
        assertEquals(
                4,
                ForestLanternProbeState.LoadedData.EXPECTED_ADVANCEMENTS.size()
        );
        assertTrue(ForestLanternProbeState.LoadedData.EXPECTED_RECIPES
                .get("etherology:leather")
                .contains("|matches=true|crafted=minecraft:leatherx1"));
    }

    @Test
    void placementAndShearsExpectationsRemainExact() {
        assertEquals(
                Map.of(
                        "east",
                        "CONSUME|age=4,facing=east|stack=0|support_removed=true",
                        "north",
                        "CONSUME|age=4,facing=north|stack=0|support_removed=true",
                        "south",
                        "CONSUME|age=4,facing=south|stack=0|support_removed=true",
                        "west",
                        "CONSUME|age=4,facing=west|stack=0|support_removed=true"
                ),
                ForestLanternProbeState.PlacementResult.EXPECTED_PLACEMENTS
        );
        Map<String, String> speeds =
                ForestLanternProbeState.ShearsResult.EXPECTED_SPEEDS;
        Map<String, String> deltas =
                ForestLanternProbeState.ShearsResult.EXPECTED_DELTAS;
        assertEquals(20, speeds.size());
        assertEquals(ForestLanternProbeState.EXPECTED_STATES, List.copyOf(speeds.keySet()));
        assertTrue(speeds.values().stream().allMatch("15.0"::equals));
        assertEquals(speeds.keySet(), deltas.keySet());
        deltas.forEach((state, delta) -> assertEquals(
                state.startsWith("age=0,") ? "1.0" : "2.5",
                delta
        ));
    }

    @Test
    void retainSeedDetectsASecondCallbackAndBreakSeedTriggersTheFirst() {
        Random retain = Random.create(
                ForestLanternProbeState.RETAIN_SINGLE_CALLBACK_SEED
        );
        assertEquals(
                ForestLanternProbeState.RETAIN_FIRST_ROLL,
                retain.nextFloat()
        );
        assertEquals(
                ForestLanternProbeState.RETAIN_SECOND_ROLL,
                retain.nextFloat()
        );
        assertTrue(ForestLanternProbeState.RETAIN_FIRST_ROLL > 0.4F);
        assertTrue(ForestLanternProbeState.RETAIN_SECOND_ROLL <= 0.4F);

        Random breaking = Random.create(ForestLanternProbeState.BREAK_SEED);
        assertEquals(
                ForestLanternProbeState.BREAK_FIRST_ROLL,
                breaking.nextFloat()
        );
        assertTrue(ForestLanternProbeState.BREAK_FIRST_ROLL <= 0.4F);
    }

    @Test
    void missingStateFailsEveryExactContract() {
        ForestLanternProbeState missing = ForestLanternProbeState.missing();
        assertFalse(missing.hasExactRegistry());
        assertFalse(missing.hasExactStatesAndProperties());
        assertFalse(missing.hasExactTags());
        assertFalse(missing.hasExactContract());
        assertFalse(missing.loadedData().hasExactContract());
        assertFalse(ForestLanternProbeState.WorldMechanics.missing(
                ForestLanternProbeState.MechanicsPhase.SERVER_STARTED
        ).hasExactContract());
    }
}
