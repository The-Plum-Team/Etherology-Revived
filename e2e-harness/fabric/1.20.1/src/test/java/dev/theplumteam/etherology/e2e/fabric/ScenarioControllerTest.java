package dev.theplumteam.etherology.e2e.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ScenarioControllerTest {

    @Test
    void forwardsLifecycleObservationsToSelectedScenario() {
        RecordingScenario scenario = new RecordingScenario();
        ScenarioController controller = new ScenarioController(scenario);

        controller.onEndClientTick(null);
        controller.onScreenInitialized(null, null, 960, 540);
        controller.onGameRenderCompleted();

        assertEquals(1, scenario.endClientTicks);
        assertEquals(1, scenario.initializedScreens);
        assertEquals(1, scenario.completedGameRenders);
        assertEquals(960, scenario.scaledWidth);
        assertEquals(540, scenario.scaledHeight);
        assertNull(scenario.client);
        assertNull(scenario.screen);
    }

    private static final class RecordingScenario implements ClientScenario {

        private MinecraftClient client;
        private Screen screen;
        private int endClientTicks;
        private int initializedScreens;
        private int completedGameRenders;
        private int scaledWidth;
        private int scaledHeight;

        @Override
        public void onEndClientTick(MinecraftClient client) {
            this.client = client;
            endClientTicks++;
        }

        @Override
        public void onScreenInitialized(
                MinecraftClient client,
                Screen screen,
                int scaledWidth,
                int scaledHeight
        ) {
            this.client = client;
            this.screen = screen;
            this.scaledWidth = scaledWidth;
            this.scaledHeight = scaledHeight;
            initializedScreens++;
        }

        @Override
        public void onGameRenderCompleted() {
            completedGameRenders++;
        }
    }
}
