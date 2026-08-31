package dev.theplumteam.etherology.e2e.forge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

/**
 * Owns the Forge client lifecycle for one packaged Etherology E2E scenario.
 */
@Mod(ForgeE2eHarness.MOD_ID)
public final class ForgeE2eHarness {

    /**
     * Identifies the test-only Forge mod root.
     */
    public static final String MOD_ID = "etherology_e2e_harness";

    /**
     * Resolves the requested scenario before registering any client callbacks.
     */
    public ForgeE2eHarness() {
        String scenarioId = ScenarioSelection.fromSystemProperty();
        Object controller = switch (scenarioId) {
            case ScenarioSelection.ETHEREAL_STORAGE -> new EtherealStorageScenario();
            case ScenarioSelection.ETHEREAL_CHANNEL -> new EtherealChannelScenario();
            default -> throw new IllegalStateException(
                    "No Forge E2E controller for " + scenarioId
            );
        };
        MinecraftForge.EVENT_BUS.register(controller);
    }
}
