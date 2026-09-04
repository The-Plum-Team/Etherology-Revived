package dev.theplumteam.etherology.baseline.fabric;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

import java.util.Objects;

/**
 * Registers the minimum client callbacks and forwards them to one scenario.
 */
final class ScenarioController {

    private final ClientScenario scenario;
    private boolean registered;

    ScenarioController(ClientScenario scenario) {
        this.scenario = Objects.requireNonNull(scenario, "scenario");
    }

    void registerLifecycleCallbacks() {
        if (registered) {
            throw new IllegalStateException("Original-baseline callbacks are already registered");
        }

        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
    }

    void onEndClientTick(MinecraftClient client) {
        scenario.onEndClientTick(client);
    }

    void onGameRenderStarting(MinecraftClient client) {
        scenario.onGameRenderStarting(client);
    }

    void onGameRenderCompleted() {
        scenario.onGameRenderCompleted();
    }
}
