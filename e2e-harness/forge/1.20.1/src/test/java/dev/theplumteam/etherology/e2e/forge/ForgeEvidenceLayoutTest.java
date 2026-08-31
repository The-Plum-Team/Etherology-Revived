package dev.theplumteam.etherology.e2e.forge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ForgeEvidenceLayoutTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesOnlyTheOwnedForgeProfileAndDeclaredScenario() throws IOException {
        Path gameDirectory = createValidLayout();

        ForgeEvidenceLayout layout = ForgeEvidenceLayout.resolve(
                gameDirectory,
                ScenarioSelection.ETHEREAL_STORAGE
        );

        assertEquals(
                gameDirectory.getParent()
                        .resolve("evidence")
                        .resolve(ScenarioSelection.ETHEREAL_STORAGE),
                layout.scenarioRoot()
        );
    }

    @Test
    void rejectsAProfileManagedByTheFabricLauncher() throws IOException {
        Path gameDirectory = createValidLayout();
        Path marker = gameDirectory.getParent().resolve(".etherology-forge-e2e-profile.json");
        Files.writeString(marker, profileMarker("scripts/e2e/client.py"));

        assertThrows(
                IOException.class,
                () -> ForgeEvidenceLayout.resolve(
                        gameDirectory,
                        ScenarioSelection.ETHEREAL_STORAGE
                )
        );
    }

    @Test
    void rejectsAForgeMarkerForAnotherProfileRevision() throws IOException {
        Path gameDirectory = createValidLayout();
        Path marker = gameDirectory.getParent().resolve(".etherology-forge-e2e-profile.json");
        Files.writeString(
                marker,
                profileMarker("scripts/e2e/forge_client.py")
                        .replace("etherology-e2e-forge-1.20.1-v7", "other-forge-profile")
        );

        assertThrows(
                IOException.class,
                () -> ForgeEvidenceLayout.resolve(
                        gameDirectory,
                        ScenarioSelection.ETHEREAL_STORAGE
                )
        );
    }

    @Test
    void refusesToReplaceExistingEvidenceTargets() throws IOException {
        Path gameDirectory = createValidLayout();
        ForgeEvidenceLayout layout = ForgeEvidenceLayout.resolve(
                gameDirectory,
                ScenarioSelection.ETHEREAL_STORAGE
        );
        Files.writeString(layout.screenshotPath("ethereal-storage-open.png"), "existing");

        assertThrows(
                IOException.class,
                () -> layout.requireFreshTargets("ethereal-storage-open.png")
        );
    }

    private Path createValidLayout() throws IOException {
        Path runtimeDirectory = temporaryDirectory.resolve("etherology-e2e-forge-1.20.1-v7");
        Path gameDirectory = runtimeDirectory.resolve("game");
        Path evidenceDirectory = runtimeDirectory.resolve("evidence");
        Path scenarioDirectory = evidenceDirectory.resolve(ScenarioSelection.ETHEREAL_STORAGE);
        Files.createDirectories(gameDirectory);
        Files.createDirectories(scenarioDirectory.resolve("reports"));
        Files.createDirectories(scenarioDirectory.resolve("screenshots"));
        Files.writeString(
                runtimeDirectory.resolve(".etherology-forge-e2e-profile.json"),
                profileMarker("scripts/e2e/forge_client.py")
        );
        Files.writeString(
                evidenceDirectory.resolve(".etherology-e2e-evidence.json"),
                """
                        {
                          "schema": 1,
                          "profile_id": "etherology-e2e-forge-1.20.1-v7",
                          "managed_by": "scripts/e2e/forge_client.py",
                          "artifact_node": "forge-1.20.1",
                          "loader": "forge",
                          "java": 17,
                          "scenarios": ["ethereal-storage"],
                          "capture": {
                            "kind": "composed-minecraft-framebuffer",
                            "width": 1920,
                            "height": 1080
                          }
                        }
                        """
        );
        return gameDirectory;
    }

    private static String profileMarker(String managedBy) {
        return """
                {
                  "schema": 1,
                  "profile_id": "etherology-e2e-forge-1.20.1-v7",
                  "managed_by": "%s",
                  "isolation": {
                    "scope": "repository-owned-ignored-state",
                    "source_profiles": []
                  },
                  "release": {
                    "artifact_node": "forge-1.20.1",
                    "minecraft_version": "1.20.1",
                    "loader": "forge",
                    "java": 17
                  }
                }
                """.formatted(managedBy);
    }
}
