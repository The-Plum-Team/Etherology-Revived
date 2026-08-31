package dev.theplumteam.etherology.e2e.forge;

import java.util.Set;

final class ScenarioSelection {

    static final String PROPERTY_NAME = "etherology.e2e.scenario";
    static final String ETHEREAL_STORAGE = "ethereal-storage";
    static final String ETHEREAL_CHANNEL = "ethereal-channel";
    private static final Set<String> SCENARIO_IDS = Set.of(
            ETHEREAL_STORAGE,
            ETHEREAL_CHANNEL
    );

    private ScenarioSelection() {
    }

    static String fromSystemProperty() {
        return parse(System.getProperty(PROPERTY_NAME));
    }

    static String parse(String rawValue) {
        if (rawValue == null) {
            return ETHEREAL_STORAGE;
        }
        if (!rawValue.equals(rawValue.trim()) || !SCENARIO_IDS.contains(rawValue)) {
            throw new IllegalArgumentException("Unsupported Forge E2E scenario: " + rawValue);
        }
        return rawValue;
    }
}
