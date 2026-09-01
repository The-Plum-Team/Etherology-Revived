package dev.theplumteam.etherology.e2e.fabric;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;

/**
 * Owns Fabric callback registration and forwards each observation to one scenario.
 */
final class ScenarioController {

    private final ClientScenario scenario;
    private boolean registered;

    ScenarioController(ClientScenario scenario) {
        this.scenario = Objects.requireNonNull(scenario, "scenario");
    }

    void registerLifecycleCallbacks() {
        if (registered) {
            throw new IllegalStateException("E2E scenario callbacks are already registered");
        }

        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
        ServerTickEvents.END_SERVER_TICK.register(this::onEndServerTick);
        ScreenEvents.AFTER_INIT.register(this::onScreenInitialized);
    }

    void onEndClientTick(MinecraftClient client) {
        scenario.onEndClientTick(client);
    }

    void onScreenInitialized(
            MinecraftClient client,
            Screen screen,
            int scaledWidth,
            int scaledHeight
    ) {
        scenario.onScreenInitialized(client, screen, scaledWidth, scaledHeight);
    }

    void onGameRenderCompleted() {
        scenario.onGameRenderCompleted();
    }

    void onEndServerTick(MinecraftServer server) {
        scenario.onEndServerTick(server);
    }
}
