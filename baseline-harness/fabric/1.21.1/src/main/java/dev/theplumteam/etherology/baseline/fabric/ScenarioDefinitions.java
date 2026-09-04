package dev.theplumteam.etherology.baseline.fabric;

final class ScenarioDefinitions {

    static final ScenarioDefinition PHASE_ZERO = new ScenarioDefinition(
            "phase0-smoke",
            "phase0-smoke.png",
            "etherology-original-phase0-smoke-world",
            "Etherology Original 0.1.7 Phase 0",
            0x4554484f303137L
    );
    static final ScenarioDefinition FOREST_LANTERN = new ScenarioDefinition(
            "forest-lantern",
            "forest-lantern.png",
            "etherology-original-forest-lantern-world",
            "Etherology Original 0.1.7 Forest Lantern",
            0x455448464c303137L
    );
    static final ScenarioDefinition ATTRAHITE_BLOCK_REGISTRY = new ScenarioDefinition(
            "attrahite-block-registry",
            "attrahite-block-registry.png",
            "etherology-original-attrahite-block-registry-world",
            "Etherology Original 0.1.7 Attrahite Blocks",
            0x4554484154543031L
    );
    static final ScenarioDefinition SLITHERITE_BLOCK_REGISTRY = new ScenarioDefinition(
            "slitherite-block-registry",
            "slitherite-block-registry-initial.png",
            "etherology-original-slitherite-block-registry-world",
            "Etherology Original 0.1.7 Slitherite Blocks",
            0x455448534c495430L
    );
    static final ScenarioDefinition PEDESTAL = PedestalBaselineScenario.DEFINITION;

    private ScenarioDefinitions() {
    }
}
