package dev.theplumteam.etherology.baseline.fabric;

/**
 * Resolves the one allowlisted original-baseline scenario without a fallback.
 */
final class ScenarioSelection {

    static final String SCENARIO_PROPERTY_NAME = "etherology.original.e2e.scenario";

    private ScenarioSelection() {
    }

    static ScenarioController fromSystemProperty() {
        return dispatch(System.getProperty(SCENARIO_PROPERTY_NAME));
    }

    static ScenarioController dispatch(String configuredScenarioId) {
        return switch (resolveScenarioId(configuredScenarioId)) {
            case PhaseZeroScenario.SCENARIO_ID ->
                    new ScenarioController(new PhaseZeroScenario());
            case ForestLanternScenario.SCENARIO_ID ->
                    new ScenarioController(new ForestLanternScenario());
            case AttrahiteBlockRegistryScenario.SCENARIO_ID ->
                    new ScenarioController(new AttrahiteBlockRegistryScenario());
            case SlitheriteBlockRegistryScenario.SCENARIO_ID ->
                    new ScenarioController(new SlitheriteBlockRegistryScenario());
            default -> throw new IllegalStateException("Scenario resolution was not exhaustive");
        };
    }

    static String resolveScenarioId(String configuredScenarioId) {
        if (PhaseZeroScenario.SCENARIO_ID.equals(configuredScenarioId)
                || ForestLanternScenario.SCENARIO_ID.equals(configuredScenarioId)
                || AttrahiteBlockRegistryScenario.SCENARIO_ID.equals(configuredScenarioId)
                || SlitheriteBlockRegistryScenario.SCENARIO_ID.equals(
                        configuredScenarioId
                )) {
            return configuredScenarioId;
        }

        throw new IllegalArgumentException(
                "Unsupported or missing -D" + SCENARIO_PROPERTY_NAME + " value '"
                        + configuredScenarioId + "'; expected exactly "
                        + PhaseZeroScenario.SCENARIO_ID + " or "
                        + ForestLanternScenario.SCENARIO_ID + " or "
                        + AttrahiteBlockRegistryScenario.SCENARIO_ID + " or "
                        + SlitheriteBlockRegistryScenario.SCENARIO_ID
        );
    }
}
