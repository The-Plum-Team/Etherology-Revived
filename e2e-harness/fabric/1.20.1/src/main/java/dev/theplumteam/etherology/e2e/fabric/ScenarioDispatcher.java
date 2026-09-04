package dev.theplumteam.etherology.e2e.fabric;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Resolves the requested scenario once and constructs its isolated controller.
 */
final class ScenarioDispatcher {

    static final String SCENARIO_PROPERTY_NAME = "etherology.e2e.scenario";

    private static final Map<String, Supplier<ClientScenario>> SCENARIO_FACTORIES = Map.of(
            PhaseZeroScenario.SCENARIO_ID,
            PhaseZeroScenario::new,
            StorageUtilitiesScenario.SCENARIO_ID,
            StorageUtilitiesScenario::new,
            EtherNetworkScenario.SCENARIO_ID,
            EtherNetworkScenario::new,
            MetalBlockRegistryScenario.SCENARIO_ID,
            MetalBlockRegistryScenario::new,
            ForestLanternScenario.SCENARIO_ID,
            ForestLanternScenario::new,
            AttrahiteBlockRegistryScenario.SCENARIO_ID,
            AttrahiteBlockRegistryScenario::new,
            FabricSlitheriteBlockRegistryScenario.SCENARIO_ID,
            FabricSlitheriteBlockRegistryScenario::new
    );

    private ScenarioDispatcher() {
    }

    static ScenarioController fromSystemProperty() {
        return dispatch(System.getProperty(SCENARIO_PROPERTY_NAME));
    }

    static ScenarioController dispatch(String configuredScenarioId) {
        String scenarioId = resolveScenarioId(configuredScenarioId);
        return new ScenarioController(SCENARIO_FACTORIES.get(scenarioId).get());
    }

    static String resolveScenarioId(String configuredScenarioId) {
        String scenarioId = configuredScenarioId == null
                ? PhaseZeroScenario.SCENARIO_ID
                : configuredScenarioId;
        if (SCENARIO_FACTORIES.containsKey(scenarioId)) {
            return scenarioId;
        }

        throw new IllegalArgumentException(
                "Unsupported -D" + SCENARIO_PROPERTY_NAME + " value '" + scenarioId
                        + "'; supported scenarios: " + SCENARIO_FACTORIES.keySet()
        );
    }
}
