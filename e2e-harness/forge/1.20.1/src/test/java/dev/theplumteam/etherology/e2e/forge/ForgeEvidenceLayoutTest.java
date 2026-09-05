package dev.theplumteam.etherology.e2e.forge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.theplumteam.etherology.e2e.shared.SlitheriteBlockRegistryScenario;
import dev.theplumteam.etherology.e2e.shared.SlitheriteEvidenceTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ForgeEvidenceLayoutTest {

    private static final String PROFILE_MANIFEST_SHA256 = "a".repeat(64);

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
        assertEquals("etherology-e2e-forge-1.20.1-v19", layout.profileId());
        assertEquals(4096L, layout.profileManifestSize());
        assertEquals(PROFILE_MANIFEST_SHA256, layout.profileManifestSha256());

        ForgeEvidenceLayout channelLayout = ForgeEvidenceLayout.resolve(
                gameDirectory,
                ScenarioSelection.ETHEREAL_CHANNEL
        );
        assertEquals(
                gameDirectory.getParent()
                        .resolve("evidence")
                        .resolve(ScenarioSelection.ETHEREAL_CHANNEL),
                channelLayout.scenarioRoot()
        );

        ForgeEvidenceLayout forestLanternLayout = ForgeEvidenceLayout.resolve(
                gameDirectory,
                ScenarioSelection.FOREST_LANTERN
        );
        assertEquals(
                gameDirectory.getParent()
                        .resolve("evidence")
                        .resolve(ScenarioSelection.FOREST_LANTERN),
                forestLanternLayout.scenarioRoot()
        );

        ForgeEvidenceLayout attrahiteLayout = ForgeEvidenceLayout.resolve(
                gameDirectory,
                ScenarioSelection.ATTRAHITE_BLOCK_REGISTRY
        );
        assertEquals(
                gameDirectory.getParent()
                        .resolve("evidence")
                        .resolve(ScenarioSelection.ATTRAHITE_BLOCK_REGISTRY),
                attrahiteLayout.scenarioRoot()
        );

        ForgeEvidenceLayout slitheriteLayout = ForgeEvidenceLayout.resolve(
                gameDirectory,
                ScenarioSelection.SLITHERITE_BLOCK_REGISTRY
        );
        assertEquals(
                gameDirectory.getParent()
                        .resolve("evidence")
                        .resolve(ScenarioSelection.SLITHERITE_BLOCK_REGISTRY),
                slitheriteLayout.scenarioRoot()
        );

        SlitheriteEvidenceTarget target =
                new ForgeSlitheriteBlockRegistryScenario().resolveEvidence(
                        gameDirectory,
                        SlitheriteBlockRegistryScenario.definition()
                );
        assertEquals(slitheriteLayout.scenarioRoot(), target.scenarioRoot());
        assertEquals(1920, target.framebufferWidth());
        assertEquals(1080, target.framebufferHeight());
        assertEquals(
                ForgeEvidenceLayout.PROFILE_ID,
                target.provenanceFields().get("profile_id").getAsString()
        );
        assertEquals(
                4096L,
                target.provenanceFields().get("profile_manifest_size").getAsLong()
        );
        assertEquals(
                PROFILE_MANIFEST_SHA256,
                target.provenanceFields().get("profile_manifest_sha256").getAsString()
        );
    }

    @Test
    void packagedProfileIdMatchesActiveProfileManifest() throws IOException {
        Path manifestPath = Path.of(
                System.getProperty("etherology.e2e.forge.activeProfile")
        );
        try (Reader reader = Files.newBufferedReader(manifestPath)) {
            JsonObject manifest = JsonParser.parseReader(reader).getAsJsonObject();
            assertEquals(
                    manifest.getAsJsonObject("profile").get("id").getAsString(),
                    ForgeEvidenceLayout.PROFILE_ID
            );
        }
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
                        .replace("etherology-e2e-forge-1.20.1-v19", "other-forge-profile")
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
    void rejectsAForgeMarkerWithoutCaptureTimeProfileProvenance() throws IOException {
        Path gameDirectory = createValidLayout();
        Path marker = gameDirectory.getParent().resolve(".etherology-forge-e2e-profile.json");
        Files.writeString(
                marker,
                profileMarker("scripts/e2e/forge_client.py")
                        .replace("\"profile_manifest\"", "\"wrong_profile_manifest\"")
        );

        assertThrows(
                IOException.class,
                () -> ForgeEvidenceLayout.resolve(
                        gameDirectory,
                        ScenarioSelection.ETHEREAL_CHANNEL
                )
        );
    }

    @Test
    void rejectsAnUnsafeCaptureTimeProfileDigest() throws IOException {
        Path gameDirectory = createValidLayout();
        Path marker = gameDirectory.getParent().resolve(".etherology-forge-e2e-profile.json");
        Files.writeString(
                marker,
                profileMarker("scripts/e2e/forge_client.py")
                        .replace(PROFILE_MANIFEST_SHA256, "A".repeat(64))
        );

        assertThrows(
                IOException.class,
                () -> ForgeEvidenceLayout.resolve(
                        gameDirectory,
                        ScenarioSelection.ETHEREAL_CHANNEL
                )
        );
    }

    @Test
    void rejectsForgeLoaderVersionDrift() throws IOException {
        Path gameDirectory = createValidLayout();
        Path marker = gameDirectory.getParent()
                .resolve(".etherology-forge-e2e-profile.json");
        Files.writeString(
                marker,
                Files.readString(marker).replace("1.20.1-47.4.9", "1.20.1-47.4.8")
        );

        assertThrows(
                IOException.class,
                () -> ForgeEvidenceLayout.resolve(
                        gameDirectory,
                        ScenarioSelection.SLITHERITE_BLOCK_REGISTRY
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

    @Test
    void rejectsAReorderedScenarioInventory() throws IOException {
        Path gameDirectory = createValidLayout();
        Path marker = gameDirectory.getParent()
                .resolve("evidence")
                .resolve(".etherology-e2e-evidence.json");
        Files.writeString(
                marker,
                Files.readString(marker).replace(
                        "[\"ethereal-storage\", \"ethereal-channel\", "
                                + "\"forest-lantern\", \"attrahite-block-registry\", "
                                + "\"slitherite-block-registry\"]",
                        "[\"ethereal-channel\", \"ethereal-storage\", "
                                + "\"forest-lantern\", \"attrahite-block-registry\", "
                                + "\"slitherite-block-registry\"]"
                )
        );

        assertThrows(
                IOException.class,
                () -> ForgeEvidenceLayout.resolve(
                        gameDirectory,
                        ScenarioSelection.ETHEREAL_CHANNEL
                )
        );
    }

    private Path createValidLayout() throws IOException {
        Path runtimeDirectory = temporaryDirectory.resolve("etherology-e2e-forge-1.20.1-v19");
        Path gameDirectory = runtimeDirectory.resolve("game");
        Path evidenceDirectory = runtimeDirectory.resolve("evidence");
        Path scenarioDirectory = evidenceDirectory.resolve(ScenarioSelection.ETHEREAL_STORAGE);
        Path channelScenarioDirectory = evidenceDirectory.resolve(
                ScenarioSelection.ETHEREAL_CHANNEL
        );
        Path forestLanternScenarioDirectory = evidenceDirectory.resolve(
                ScenarioSelection.FOREST_LANTERN
        );
        Path attrahiteScenarioDirectory = evidenceDirectory.resolve(
                ScenarioSelection.ATTRAHITE_BLOCK_REGISTRY
        );
        Path slitheriteScenarioDirectory = evidenceDirectory.resolve(
                ScenarioSelection.SLITHERITE_BLOCK_REGISTRY
        );
        Files.createDirectories(gameDirectory);
        Files.createDirectories(scenarioDirectory.resolve("reports"));
        Files.createDirectories(scenarioDirectory.resolve("screenshots"));
        Files.createDirectories(channelScenarioDirectory.resolve("reports"));
        Files.createDirectories(channelScenarioDirectory.resolve("screenshots"));
        Files.createDirectories(forestLanternScenarioDirectory.resolve("reports"));
        Files.createDirectories(forestLanternScenarioDirectory.resolve("screenshots"));
        Files.createDirectories(attrahiteScenarioDirectory.resolve("reports"));
        Files.createDirectories(attrahiteScenarioDirectory.resolve("screenshots"));
        Files.createDirectories(slitheriteScenarioDirectory.resolve("reports"));
        Files.createDirectories(slitheriteScenarioDirectory.resolve("screenshots"));
        Files.writeString(
                runtimeDirectory.resolve(".etherology-forge-e2e-profile.json"),
                profileMarker("scripts/e2e/forge_client.py")
        );
        Files.writeString(
                evidenceDirectory.resolve(".etherology-e2e-evidence.json"),
                """
                        {
                          "schema": 1,
                          "profile_id": "etherology-e2e-forge-1.20.1-v19",
                          "managed_by": "scripts/e2e/forge_client.py",
                          "artifact_node": "forge-1.20.1",
                          "loader": "forge",
                          "java": 17,
                          "scenarios": ["ethereal-storage", "ethereal-channel", "forest-lantern", "attrahite-block-registry", "slitherite-block-registry"],
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
                  "profile_id": "etherology-e2e-forge-1.20.1-v19",
                  "managed_by": "%s",
                  "profile_manifest": {
                    "path": "scripts/e2e/forge-1.20.1-profile.json",
                    "size": 4096,
                    "sha256": "%s"
                  },
                  "isolation": {
                    "scope": "repository-owned-ignored-state",
                    "source_profiles": []
                  },
                  "release": {
                    "artifact_node": "forge-1.20.1",
                    "minecraft_version": "1.20.1",
                    "loader": "forge",
                    "loader_version": "1.20.1-47.4.9",
                    "java": 17
                  }
                }
                """.formatted(managedBy, PROFILE_MANIFEST_SHA256);
    }
}
