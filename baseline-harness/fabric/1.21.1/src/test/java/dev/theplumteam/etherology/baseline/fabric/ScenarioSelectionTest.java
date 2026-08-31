package dev.theplumteam.etherology.baseline.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ScenarioSelectionTest {

    @Test
    void acceptsOnlyTheExactPhaseZeroScenario() {
        assertEquals(
                PhaseZeroScenario.SCENARIO_ID,
                ScenarioSelection.resolveScenarioId(PhaseZeroScenario.SCENARIO_ID)
        );
    }

    @Test
    void rejectsMissingOrInexactScenarios() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ScenarioSelection.resolveScenarioId(null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ScenarioSelection.resolveScenarioId("")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ScenarioSelection.resolveScenarioId("phase0-smoke ")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ScenarioSelection.resolveScenarioId("../phase0-smoke")
        );
    }
}
