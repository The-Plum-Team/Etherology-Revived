package dev.theplumteam.etherology.e2e.server;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RegistryFoundationServerProbeSlitheriteStateMachineTest {

    @Test
    void behaviorPhaseCatalogAndTickBudgetsArePinned() {
        assertEquals(
                List.of(
                        RegistryFoundationServerProbe.SlitheriteBehaviorPhase
                                .WAITING_FOR_ENTITY_TICKING,
                        RegistryFoundationServerProbe.SlitheriteBehaviorPhase
                                .WAITING_FOR_ITEM_TICK,
                        RegistryFoundationServerProbe.SlitheriteBehaviorPhase
                                .WAITING_FOR_LIVING_ACTIVATION,
                        RegistryFoundationServerProbe.SlitheriteBehaviorPhase
                                .WAITING_FOR_RESET,
                        RegistryFoundationServerProbe.SlitheriteBehaviorPhase.COMPLETE,
                        RegistryFoundationServerProbe.SlitheriteBehaviorPhase.FAILED
                ),
                Arrays.asList(
                        RegistryFoundationServerProbe.SlitheriteBehaviorPhase.values()
                )
        );
        assertEquals(
                100L,
                RegistryFoundationServerProbe
                        .MAXIMUM_SLITHERITE_ENTITY_TICK_GATE_TICKS
        );
        assertEquals(
                20L,
                RegistryFoundationServerProbe
                        .MAXIMUM_SLITHERITE_PROBE_ENTITY_TICK_TICKS
        );
        assertEquals(
                20L,
                RegistryFoundationServerProbe
                        .MAXIMUM_SLITHERITE_LIVING_ACTIVATION_TICKS
        );
        assertEquals(
                25L,
                RegistryFoundationServerProbe.SLITHERITE_PRESSURE_PLATE_RESET_TICKS
        );
    }

    @Test
    void behaviorStateMachineAdvancesMilestonesAndFailsBoundedWaits() {
        RegistryFoundationServerProbe.SlitheriteBehaviorPhase entityGate =
                RegistryFoundationServerProbe.SlitheriteBehaviorPhase
                        .WAITING_FOR_ENTITY_TICKING;
        RegistryFoundationServerProbe.SlitheriteBehaviorPhase itemTick =
                RegistryFoundationServerProbe.SlitheriteBehaviorPhase
                        .WAITING_FOR_ITEM_TICK;
        RegistryFoundationServerProbe.SlitheriteBehaviorPhase living =
                RegistryFoundationServerProbe.SlitheriteBehaviorPhase
                        .WAITING_FOR_LIVING_ACTIVATION;
        RegistryFoundationServerProbe.SlitheriteBehaviorPhase reset =
                RegistryFoundationServerProbe.SlitheriteBehaviorPhase
                        .WAITING_FOR_RESET;

        assertEquals(entityGate, advance(entityGate, false, false));
        assertEquals(itemTick, advance(entityGate, true, false));
        assertEquals(
                RegistryFoundationServerProbe.SlitheriteBehaviorPhase.FAILED,
                advance(entityGate, false, true)
        );
        assertEquals(itemTick, advance(itemTick, false, false));
        assertEquals(living, advance(itemTick, true, false));
        assertEquals(
                RegistryFoundationServerProbe.SlitheriteBehaviorPhase.FAILED,
                advance(itemTick, false, true)
        );
        assertEquals(living, advance(living, false, false));
        assertEquals(reset, advance(living, true, false));
        assertEquals(reset, advance(living, false, true));
        assertEquals(reset, advance(reset, false, false));
        assertEquals(
                RegistryFoundationServerProbe.SlitheriteBehaviorPhase.COMPLETE,
                advance(reset, false, true)
        );
        assertEquals(
                RegistryFoundationServerProbe.SlitheriteBehaviorPhase.COMPLETE,
                advance(
                        RegistryFoundationServerProbe.SlitheriteBehaviorPhase.COMPLETE,
                        true,
                        true
                )
        );
        assertEquals(
                RegistryFoundationServerProbe.SlitheriteBehaviorPhase.FAILED,
                advance(
                        RegistryFoundationServerProbe.SlitheriteBehaviorPhase.FAILED,
                        true,
                        true
                )
        );
    }

    @Test
    void entityAgeAndDeadlinePredicatesAreBoundaryExact() {
        assertFalse(RegistryFoundationServerProbe.entityTickAdvanced(false, 4, 5));
        assertFalse(RegistryFoundationServerProbe.entityTickAdvanced(true, 4, 4));
        assertTrue(RegistryFoundationServerProbe.entityTickAdvanced(true, 4, 5));
        assertFalse(RegistryFoundationServerProbe.deadlineReached(19L, 20L));
        assertTrue(RegistryFoundationServerProbe.deadlineReached(20L, 20L));
        assertTrue(RegistryFoundationServerProbe.deadlineReached(21L, 20L));
    }

    @Test
    void nativePlacementInventoryRequiresAllSeventeenAcceptedOneToZeroUses() {
        Map<String, RegistryFoundationServerProbe.SlitheriteNativePlacementEntry>
                entries = exactNativeEntries();
        RegistryFoundationServerProbe.SlitheriteNativePlacementState state =
                new RegistryFoundationServerProbe.SlitheriteNativePlacementState(
                        "",
                        entries
                );

        assertTrue(state.hasExactPlacement());
        assertEquals(
                RegistryFoundationServerProbe.SlitheriteNativePlacementState
                        .expectedCanonical(),
                state.canonical()
        );
        entries.clear();
        assertEquals(17, state.entries().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> state.entries().clear()
        );

        Map<String, RegistryFoundationServerProbe.SlitheriteNativePlacementEntry>
                changed = exactNativeEntries();
        String firstId = SlitheriteBlockProbeState.EXPECTED_BLOCK_IDS.get(0);
        RegistryFoundationServerProbe.SlitheriteNativePlacementEntry first =
                changed.get(firstId);
        changed.put(
                firstId,
                new RegistryFoundationServerProbe.SlitheriteNativePlacementEntry(
                        first.actionResult(),
                        first.accepted(),
                        first.beforeCount(),
                        1,
                        first.blockItemMapping(),
                        first.placedId(),
                        first.placedState()
                )
        );
        assertFalse(
                new RegistryFoundationServerProbe.SlitheriteNativePlacementState(
                        "",
                        changed
                ).hasExactPlacement()
        );
    }

    @Test
    void behaviorContractRequiresBothEntityKindsAndTwentyTickButtonPulse() {
        RegistryFoundationServerProbe.SlitheriteBehaviorProbe exact = behavior(20L);

        assertTrue(exact.buttonExact());
        assertTrue(exact.pressurePlateExact());
        assertTrue(exact.exact());
        assertFalse(behavior(19L).buttonExact());
        assertFalse(
                new RegistryFoundationServerProbe.SlitheriteBehaviorProbe(
                        "",
                        true,
                        true,
                        true,
                        true,
                        true,
                        20L,
                        true,
                        false,
                        true,
                        true,
                        true,
                        true
                ).pressurePlateExact()
        );
        assertFalse(
                RegistryFoundationServerProbe.SlitheriteBehaviorProbe
                        .failed("timeout")
                        .exact()
        );
    }

    private static RegistryFoundationServerProbe.SlitheriteBehaviorPhase advance(
            RegistryFoundationServerProbe.SlitheriteBehaviorPhase phase,
            boolean milestoneReached,
            boolean deadlineReached
    ) {
        return RegistryFoundationServerProbe.advanceSlitheriteBehaviorPhase(
                phase,
                milestoneReached,
                deadlineReached
        );
    }

    private static Map<
            String,
            RegistryFoundationServerProbe.SlitheriteNativePlacementEntry
    > exactNativeEntries() {
        Map<String, RegistryFoundationServerProbe.SlitheriteNativePlacementEntry>
                entries = new LinkedHashMap<>();
        SlitheriteBlockProbeState.EXPECTED_BLOCKS.forEach((id, spec) -> entries.put(
                id,
                new RegistryFoundationServerProbe.SlitheriteNativePlacementEntry(
                        "CONSUME",
                        true,
                        1,
                        0,
                        true,
                        id,
                        spec.defaultState()
                )
        ));
        return entries;
    }

    private static RegistryFoundationServerProbe.SlitheriteBehaviorProbe behavior(
            long elapsedTicks
    ) {
        return new RegistryFoundationServerProbe.SlitheriteBehaviorProbe(
                "",
                true,
                true,
                true,
                true,
                true,
                elapsedTicks,
                true,
                true,
                true,
                true,
                true,
                true
        );
    }
}
