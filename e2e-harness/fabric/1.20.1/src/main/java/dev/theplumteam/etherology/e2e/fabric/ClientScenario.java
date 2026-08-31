package dev.theplumteam.etherology.e2e.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/**
 * Receives the client lifecycle observations needed by one packaged E2E scenario.
 */
interface ClientScenario {

    void onEndClientTick(MinecraftClient client);

    void onScreenInitialized(
            MinecraftClient client,
            Screen screen,
            int scaledWidth,
            int scaledHeight
    );

    void onGameRenderCompleted();
}
