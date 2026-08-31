package dev.theplumteam.etherology.e2e.fabric;

import net.fabricmc.api.ClientModInitializer;

/**
 * Selects one packaged scenario and connects it to Fabric client lifecycle events.
 */
public final class PhaseZeroHarness implements ClientModInitializer {

    private static volatile ScenarioController activeController;

    /**
     * Resolves the requested scenario before registering any client callbacks.
     */
    @Override
    public void onInitializeClient() {
        ScenarioController controller = ScenarioDispatcher.fromSystemProperty();
        activeController = controller;
        controller.registerLifecycleCallbacks();
    }

    public static void onGameRenderCompleted() {
        ScenarioController controller = activeController;
        if (controller != null) {
            controller.onGameRenderCompleted();
        }
    }
}
