package dev.theplumteam.etherology.baseline.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;

/**
 * Connects the separately packaged published-0.1.7 baseline harness to Fabric.
 */
public final class OriginalPhaseZeroHarness implements ClientModInitializer {

    private static volatile ScenarioController activeController;

    @Override
    public void onInitializeClient() {
        ScenarioController controller = ScenarioSelection.fromSystemProperty();
        activeController = controller;
        controller.registerLifecycleCallbacks();
    }

    public static void onGameRenderStarting() {
        ScenarioController controller = activeController;
        if (controller != null) {
            controller.onGameRenderStarting(MinecraftClient.getInstance());
        }
    }

    public static void onGameRenderCompleted() {
        ScenarioController controller = activeController;
        if (controller != null) {
            controller.onGameRenderCompleted();
        }
    }
}
