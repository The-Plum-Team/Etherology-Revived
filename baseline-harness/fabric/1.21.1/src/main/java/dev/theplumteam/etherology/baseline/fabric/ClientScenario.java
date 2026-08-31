package dev.theplumteam.etherology.baseline.fabric;

import net.minecraft.client.MinecraftClient;

/**
 * Receives the deterministic client observations used by one original-baseline scenario.
 */
interface ClientScenario {

    void onEndClientTick(MinecraftClient client);

    void onGameRenderCompleted();
}
