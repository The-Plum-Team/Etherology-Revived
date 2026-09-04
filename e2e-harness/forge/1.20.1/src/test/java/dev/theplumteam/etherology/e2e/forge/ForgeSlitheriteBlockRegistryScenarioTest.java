package dev.theplumteam.etherology.e2e.forge;

import com.google.gson.JsonObject;
import dev.theplumteam.etherology.e2e.shared.SlitheriteBlockRegistryScenario;
import dev.theplumteam.etherology.e2e.shared.SlitheriteHarnessIdentity;
import dev.theplumteam.etherology.e2e.shared.SlitheriteScenarioAdapter;
import dev.theplumteam.etherology.e2e.shared.SlitheriteScenarioDefinition;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ForgeSlitheriteBlockRegistryScenarioTest {

    @Test
    void exposesTheExactForgeIdentity() {
        ForgeSlitheriteBlockRegistryScenario adapter =
                new ForgeSlitheriteBlockRegistryScenario();
        SlitheriteHarnessIdentity identity = adapter.identity();
        JsonObject fields = identity.reportFields();

        assertInstanceOf(SlitheriteScenarioAdapter.class, adapter);
        assertEquals("forge", identity.loaderId());
        assertEquals(ForgeE2eHarness.MOD_ID, identity.harnessModId());
        assertEquals(
                Set.of(
                        "artifact_node",
                        "minecraft",
                        "loader",
                        "loader_version",
                        "java",
                        "lane",
                        "role"
                ),
                fields.keySet()
        );
        assertEquals("forge-1.20.1", fields.get("artifact_node").getAsString());
        assertEquals("1.20.1", fields.get("minecraft").getAsString());
        assertEquals("forge", fields.get("loader").getAsString());
        assertEquals("47.4.9", fields.get("loader_version").getAsString());
        assertEquals(17, fields.get("java").getAsInt());
        assertEquals("forge-1.20.1", fields.get("lane").getAsString());
        assertEquals("host", fields.get("role").getAsString());
    }

    @Test
    void subscribesAllSharedLifecycleBoundaries() throws NoSuchMethodException {
        assertSubscribed("onClientTick", TickEvent.ClientTickEvent.class);
        assertSubscribed("onServerTick", TickEvent.ServerTickEvent.class);
        assertSubscribed("onWorldRendered", RenderGuiEvent.Post.class);
    }

    @Test
    void selectsTheSharedSlitheriteWorldIdentity() {
        SlitheriteScenarioDefinition definition =
                SlitheriteBlockRegistryScenario.definition();

        assertEquals(ScenarioSelection.SLITHERITE_BLOCK_REGISTRY, definition.id());
        assertEquals(
                "etherology-slitherite-block-registry-world",
                definition.worldDirectoryName()
        );
        assertEquals("Etherology Slitherite Blocks", definition.worldDisplayName());
    }

    private static void assertSubscribed(
            String methodName,
            Class<?> eventType
    ) throws NoSuchMethodException {
        Method method = ForgeSlitheriteBlockRegistryScenario.class
                .getDeclaredMethod(methodName, eventType);
        assertTrue(method.isAnnotationPresent(SubscribeEvent.class));
    }
}
