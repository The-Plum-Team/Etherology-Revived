package dev.theplumteam.etherology.e2e.fabric;

import com.google.gson.JsonObject;
import dev.theplumteam.etherology.e2e.shared.SlitheriteBlockRegistryScenario;
import dev.theplumteam.etherology.e2e.shared.SlitheriteEvidenceTarget;
import dev.theplumteam.etherology.e2e.shared.SlitheriteHarnessIdentity;
import dev.theplumteam.etherology.e2e.shared.SlitheriteScenarioAdapter;
import dev.theplumteam.etherology.e2e.shared.SlitheriteScenarioDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class FabricSlitheriteBlockRegistryScenarioTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void exposesTheExactFabricIdentity() {
        FabricSlitheriteBlockRegistryScenario adapter =
                new FabricSlitheriteBlockRegistryScenario();
        SlitheriteHarnessIdentity identity = adapter.identity();
        JsonObject fields = identity.reportFields();

        assertInstanceOf(ClientScenario.class, adapter);
        assertInstanceOf(SlitheriteScenarioAdapter.class, adapter);
        assertEquals("fabric", identity.loaderId());
        assertEquals("etherology_e2e_harness", identity.harnessModId());
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
        assertEquals("fabric-1.20.1", fields.get("artifact_node").getAsString());
        assertEquals("1.20.1", fields.get("minecraft").getAsString());
        assertEquals("fabric", fields.get("loader").getAsString());
        assertEquals("0.17.3", fields.get("loader_version").getAsString());
        assertEquals(17, fields.get("java").getAsInt());
        assertEquals("fabric-1.20.1", fields.get("lane").getAsString());
        assertEquals("host", fields.get("role").getAsString());
    }

    @Test
    void selectsTheSharedSlitheriteWorldIdentity() {
        SlitheriteScenarioDefinition definition =
                SlitheriteBlockRegistryScenario.definition();

        assertEquals("slitherite-block-registry", definition.id());
        assertEquals(
                "etherology-slitherite-block-registry-world",
                definition.worldDirectoryName()
        );
        assertEquals("Etherology Slitherite Blocks", definition.worldDisplayName());
        assertEquals(definition.id(), FabricSlitheriteBlockRegistryScenario.SCENARIO_ID);
    }

    @Test
    void resolvesOnlyFreshEvidenceUnderTheValidatedFabricProfile()
            throws IOException {
        SlitheriteScenarioDefinition definition =
                SlitheriteBlockRegistryScenario.definition();
        Path gameDirectory = createValidLayout();
        Path scenarioDirectory = gameDirectory.getParent()
                .resolve("evidence")
                .resolve(definition.id());
        FabricSlitheriteBlockRegistryScenario adapter =
                new FabricSlitheriteBlockRegistryScenario();

        SlitheriteEvidenceTarget target = adapter.resolveEvidence(
                gameDirectory,
                definition
        );

        assertEquals(scenarioDirectory, target.scenarioRoot());
        assertEquals(1920, target.framebufferWidth());
        assertEquals(1080, target.framebufferHeight());
        assertEquals(
                "etherology-e2e-fabric-1.20.1-v31",
                target.provenanceFields().get("profile_id").getAsString()
        );
        Files.writeString(
                target.screenshotPath(definition.initialScreenshotFileName()),
                "existing"
        );
        assertThrows(
                IOException.class,
                () -> adapter.resolveEvidence(gameDirectory, definition)
        );
    }

    @Test
    void rejectsFabricLoaderVersionDrift() throws IOException {
        Path gameDirectory = createValidLayout();
        Path marker = gameDirectory.getParent()
                .resolve(".etherology-e2e-profile.json");
        Files.writeString(
                marker,
                Files.readString(marker).replace("0.17.3", "0.17.2")
        );

        assertThrows(
                IOException.class,
                () -> new FabricSlitheriteBlockRegistryScenario().resolveEvidence(
                        gameDirectory,
                        SlitheriteBlockRegistryScenario.definition()
                )
        );
    }

    @Test
    void rejectsAnotherSafeFabricProfileIdentity() throws IOException {
        Path gameDirectory = createValidLayout();
        Path marker = gameDirectory.getParent()
                .resolve(".etherology-e2e-profile.json");
        Files.writeString(
                marker,
                Files.readString(marker).replace(
                        "etherology-e2e-fabric-1.20.1-v31",
                        "etherology-e2e-fabric-1.20.1-v32"
                )
        );

        assertThrows(
                IOException.class,
                () -> new FabricSlitheriteBlockRegistryScenario().resolveEvidence(
                        gameDirectory,
                        SlitheriteBlockRegistryScenario.definition()
                )
        );
    }

    @Test
    void rejectsAReorderedFabricScenarioInventory() throws IOException {
        Path gameDirectory = createValidLayout();
        Path marker = gameDirectory.getParent()
                .resolve("evidence")
                .resolve(".etherology-e2e-evidence.json");
        Files.writeString(
                marker,
                Files.readString(marker)
                        .replace("\"phase0-smoke\"", "\"temporary-scenario\"")
                        .replace("\"progression-oculus\"", "\"phase0-smoke\"")
                        .replace("\"temporary-scenario\"", "\"progression-oculus\"")
        );

        assertThrows(
                IOException.class,
                () -> new FabricSlitheriteBlockRegistryScenario().resolveEvidence(
                        gameDirectory,
                        SlitheriteBlockRegistryScenario.definition()
                )
        );
    }

    private Path createValidLayout() throws IOException {
        SlitheriteScenarioDefinition definition =
                SlitheriteBlockRegistryScenario.definition();
        Path runtimeDirectory = temporaryDirectory.resolve(
                "etherology-e2e-fabric-1.20.1-v31"
        );
        Path gameDirectory = runtimeDirectory.resolve("game");
        Path evidenceDirectory = runtimeDirectory.resolve("evidence");
        Path scenarioDirectory = evidenceDirectory.resolve(definition.id());
        Files.createDirectories(gameDirectory);
        Files.createDirectories(scenarioDirectory.resolve("reports"));
        Files.createDirectories(scenarioDirectory.resolve("screenshots"));
        Files.writeString(
                runtimeDirectory.resolve(".etherology-e2e-profile.json"),
                fabricProfileMarker()
        );
        Files.writeString(
                evidenceDirectory.resolve(".etherology-e2e-evidence.json"),
                fabricEvidenceMarker()
        );
        return gameDirectory;
    }

    private static String fabricProfileMarker() {
        return """
                {
                  "schema": 1,
                  "profile_id": "etherology-e2e-fabric-1.20.1-v31",
                  "managed_by": "scripts/e2e/client.py",
                  "isolation": {
                    "scope": "repository-owned-ignored-state",
                    "source_profiles": []
                  },
                  "release": {
                    "artifact_node": "fabric-1.20.1",
                    "minecraft_version": "1.20.1",
                    "loader": "fabric",
                    "loader_version": "0.17.3",
                    "java": 17
                  }
                }
                """;
    }

    private static String fabricEvidenceMarker() {
        return """
                {
                  "schema": 1,
                  "profile_id": "etherology-e2e-fabric-1.20.1-v31",
                  "artifact_node": "fabric-1.20.1",
                  "scenarios": [
                    "phase0-smoke",
                    "progression-oculus",
                    "seals-aspects",
                    "golden-forest",
                    "alchemy",
                    "ether-network",
                    "staff-lenses",
                    "spiritual-energy",
                    "armillary",
                    "storage-utilities",
                    "combat-equipment",
                    "persistence",
                    "multiplayer-sync",
                    "metal-block-registry",
                    "forest-lantern",
                    "attrahite-block-registry",
                    "slitherite-block-registry"
                  ],
                  "capture": {
                    "kind": "composed-minecraft-framebuffer",
                    "width": 1920,
                    "height": 1080
                  }
                }
                """;
    }
}
