package dev.theplumteam.etherology.baseline.fabric;

import net.minecraft.client.MinecraftClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ScenarioControllerTest {

    @Test
    void renderCallbacksAreForwardedExactlyOnce() {
        RecordingScenario scenario = new RecordingScenario();
        ScenarioController controller = new ScenarioController(scenario);

        controller.onGameRenderStarting(null);
        controller.onGameRenderCompleted();

        assertEquals(1, scenario.renderStartingCalls);
        assertEquals(1, scenario.renderCompletedCalls);
    }

    private static final class RecordingScenario implements ClientScenario {

        private int renderStartingCalls;
        private int renderCompletedCalls;

        @Override
        public void onEndClientTick(MinecraftClient client) {
        }

        @Override
        public void onGameRenderStarting(MinecraftClient client) {
            renderStartingCalls++;
        }

        @Override
        public void onGameRenderCompleted() {
            renderCompletedCalls++;
        }
    }
}
