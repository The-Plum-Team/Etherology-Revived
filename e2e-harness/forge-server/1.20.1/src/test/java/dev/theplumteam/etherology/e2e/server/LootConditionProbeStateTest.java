package dev.theplumteam.etherology.e2e.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LootConditionProbeStateTest {

    @Test
    void separatesServerStartIdentityFromReloadedTableReplacement() {
        Object conditionType = new Object();
        Object originalTable = new Object();
        LootConditionProbeState initial = state(
                conditionType,
                originalTable,
                LootConditionProbeState.EXPECTED_FORTUNE_ONE_ITEMS
        );
        LootConditionProbeState serverStarted = state(
                conditionType,
                originalTable,
                LootConditionProbeState.EXPECTED_FORTUNE_ONE_ITEMS
        );
        LootConditionProbeState reloaded = state(
                conditionType,
                new Object(),
                LootConditionProbeState.EXPECTED_FORTUNE_ONE_ITEMS
        );

        assertTrue(initial.sameStateAtServerStarted(serverStarted));
        assertFalse(initial.hasReplacedProbeTableInstanceAfterReload(serverStarted));
        assertFalse(initial.sameStateAtServerStarted(reloaded));
        assertTrue(initial.hasSameRegistryAndBehavior(reloaded));
        assertTrue(initial.hasReplacedProbeTableInstanceAfterReload(reloaded));
    }

    @Test
    void rejectsChangedLootBehaviorEvenWhenReloadReplacesTheTable() {
        Object conditionType = new Object();
        LootConditionProbeState initial = state(
                conditionType,
                new Object(),
                LootConditionProbeState.EXPECTED_FORTUNE_ONE_ITEMS
        );
        LootConditionProbeState changed = state(
                conditionType,
                new Object(),
                List.of("minecraft:diamond")
        );

        assertFalse(initial.hasSameRegistryAndBehavior(changed));
        assertTrue(initial.hasReplacedProbeTableInstanceAfterReload(changed));
    }

    private static LootConditionProbeState state(
            Object conditionType,
            Object probeTable,
            List<String> fortuneOneItems
    ) {
        return new LootConditionProbeState(
                conditionType,
                probeTable,
                LootConditionProbeState.CONDITION_ID.toString(),
                List.of(LootConditionProbeState.CONDITION_ID.toString()),
                LootConditionProbeState.EXPECTED_SERIALIZER_CLASS,
                LootConditionProbeState.PROBE_TABLE_ID.toString(),
                LootConditionProbeState.EXPECTED_EMPTY_TOOL_ITEMS,
                fortuneOneItems
        );
    }
}
