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
        resolveScenarioId(configuredScenarioId);
        return new ScenarioController(new PhaseZeroScenario());
    }

    static String resolveScenarioId(String configuredScenarioId) {
        if (PhaseZeroScenario.SCENARIO_ID.equals(configuredScenarioId)) {
            return configuredScenarioId;
        }

        throw new IllegalArgumentException(
                "Unsupported or missing -D" + SCENARIO_PROPERTY_NAME + " value '"
                        + configuredScenarioId + "'; expected exactly "
                        + PhaseZeroScenario.SCENARIO_ID
        );
    }
}
