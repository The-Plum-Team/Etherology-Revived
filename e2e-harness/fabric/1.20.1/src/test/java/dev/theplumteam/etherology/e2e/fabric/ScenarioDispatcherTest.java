package dev.theplumteam.etherology.e2e.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScenarioDispatcherTest {

    @Test
    void absentPropertySelectsPhaseZero() {
        assertEquals(
                PhaseZeroScenario.SCENARIO_ID,
                ScenarioDispatcher.resolveScenarioId(null)
        );
    }

    @Test
    void exactPhaseZeroIdIsAccepted() {
        assertEquals(
                PhaseZeroScenario.SCENARIO_ID,
                ScenarioDispatcher.resolveScenarioId(PhaseZeroScenario.SCENARIO_ID)
        );
    }

    @Test
    void exactStorageUtilitiesIdIsAccepted() {
        assertEquals(
                StorageUtilitiesScenario.SCENARIO_ID,
                ScenarioDispatcher.resolveScenarioId(StorageUtilitiesScenario.SCENARIO_ID)
        );
    }

    @Test
    void exactEtherNetworkIdIsAccepted() {
        assertEquals(
                EtherNetworkScenario.SCENARIO_ID,
                ScenarioDispatcher.resolveScenarioId(EtherNetworkScenario.SCENARIO_ID)
        );
    }

    @Test
    void exactMetalBlockRegistryIdIsAccepted() {
        assertEquals(
                MetalBlockRegistryScenario.SCENARIO_ID,
                ScenarioDispatcher.resolveScenarioId(MetalBlockRegistryScenario.SCENARIO_ID)
        );
    }

    @Test
    void exactForestLanternIdIsAccepted() {
        assertEquals(
                ForestLanternScenario.SCENARIO_ID,
                ScenarioDispatcher.resolveScenarioId(ForestLanternScenario.SCENARIO_ID)
        );
    }

    @Test
    void blankPropertyFailsClosed() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ScenarioDispatcher.resolveScenarioId("")
        );

        assertTrue(exception.getMessage().contains(ScenarioDispatcher.SCENARIO_PROPERTY_NAME));
        assertTrue(exception.getMessage().contains(PhaseZeroScenario.SCENARIO_ID));
        assertTrue(exception.getMessage().contains(StorageUtilitiesScenario.SCENARIO_ID));
        assertTrue(exception.getMessage().contains(EtherNetworkScenario.SCENARIO_ID));
        assertTrue(exception.getMessage().contains(MetalBlockRegistryScenario.SCENARIO_ID));
        assertTrue(exception.getMessage().contains(ForestLanternScenario.SCENARIO_ID));
    }

    @Test
    void unknownPropertyFailsClosedWithoutTrimming() {
        String unknownScenarioId = PhaseZeroScenario.SCENARIO_ID + " ";
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ScenarioDispatcher.resolveScenarioId(unknownScenarioId)
        );

        assertTrue(exception.getMessage().contains("'" + unknownScenarioId + "'"));
        assertTrue(exception.getMessage().contains(PhaseZeroScenario.SCENARIO_ID));
        assertTrue(exception.getMessage().contains(StorageUtilitiesScenario.SCENARIO_ID));
        assertTrue(exception.getMessage().contains(EtherNetworkScenario.SCENARIO_ID));
        assertTrue(exception.getMessage().contains(MetalBlockRegistryScenario.SCENARIO_ID));
        assertTrue(exception.getMessage().contains(ForestLanternScenario.SCENARIO_ID));
    }
}
