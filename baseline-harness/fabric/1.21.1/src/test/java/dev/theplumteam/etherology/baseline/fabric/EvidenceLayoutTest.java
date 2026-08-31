package dev.theplumteam.etherology.baseline.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class EvidenceLayoutTest {

    @Test
    void resolvesOnlyTheRepositoryOwnedMarkerContract(@TempDir Path temporaryDirectory)
            throws Exception {
        Path gameDirectory = createOwnedLayout(temporaryDirectory);

        EvidenceLayout layout = EvidenceLayout.resolve(
                gameDirectory,
                ScenarioDefinitions.PHASE_ZERO
        );
        layout.requireFreshTargets();

        assertEquals(1920, layout.framebufferWidth());
        assertEquals(1080, layout.framebufferHeight());
        assertEquals(
                "evidence/phase0-smoke/screenshots/phase0-smoke.png",
                temporaryDirectory.relativize(layout.screenshotPath()).toString()
        );
    }

    @Test
    void refusesToOverwriteAnyEvidenceTarget(@TempDir Path temporaryDirectory)
            throws Exception {
        Path gameDirectory = createOwnedLayout(temporaryDirectory);
        EvidenceLayout layout = EvidenceLayout.resolve(
                gameDirectory,
                ScenarioDefinitions.PHASE_ZERO
        );
        Files.writeString(layout.reportPath(), "existing\n");

        assertThrows(IOException.class, layout::requireFreshTargets);
    }

    @Test
    void rejectsLinkedEvidenceDirectories(@TempDir Path temporaryDirectory)
            throws Exception {
        Path gameDirectory = createOwnedLayout(temporaryDirectory);
        Path scenarioRoot = temporaryDirectory.resolve("evidence/phase0-smoke");
        Path external = temporaryDirectory.resolve("external");
        Files.createDirectory(external);
        Files.delete(scenarioRoot.resolve("reports"));
        Files.createSymbolicLink(scenarioRoot.resolve("reports"), external);

        assertThrows(
                IOException.class,
                () -> EvidenceLayout.resolve(gameDirectory, ScenarioDefinitions.PHASE_ZERO)
        );
    }

    @Test
    void rejectsForeignProfileProvenance(@TempDir Path temporaryDirectory)
            throws Exception {
        Path gameDirectory = createOwnedLayout(temporaryDirectory);
        Files.writeString(
                temporaryDirectory.resolve(".etherology-original-profile.json"),
                profileMarker().replace("published-0.1.7", "source-0.1.8")
        );

        assertThrows(
                IOException.class,
                () -> EvidenceLayout.resolve(gameDirectory, ScenarioDefinitions.PHASE_ZERO)
        );
    }

    private static Path createOwnedLayout(Path runtimeRoot) throws IOException {
        Path gameDirectory = runtimeRoot.resolve("game");
        Path evidenceRoot = runtimeRoot.resolve("evidence");
        Path scenarioRoot = evidenceRoot.resolve("phase0-smoke");
        Files.createDirectory(gameDirectory);
        Files.createDirectories(scenarioRoot.resolve("reports"));
        Files.createDirectory(scenarioRoot.resolve("screenshots"));
        Files.writeString(
                runtimeRoot.resolve(".etherology-original-profile.json"),
                profileMarker()
        );
        Files.writeString(
                evidenceRoot.resolve(".etherology-original-evidence.json"),
                evidenceMarker()
        );
        return gameDirectory;
    }

    private static String profileMarker() {
        return """
                {
                  "schema": 1,
                  "profile_id": "etherology-original-fabric-1.21.1-published-0.1.7-v1",
                  "managed_by": "scripts/baseline/original_client.py",
                  "isolation": {
                    "scope": "repository-owned-ignored-state",
                    "source_profiles": []
                  },
                  "reference": {
                    "reference_id": "published-0.1.7"
                  },
                  "runtime": {
                    "minecraft_version": "1.21.1",
                    "loader": "fabric",
                    "loader_version": "0.17.3",
                    "java_major": 21
                  }
                }
                """;
    }

    private static String evidenceMarker() {
        return """
                {
                  "schema": 1,
                  "profile_id": "etherology-original-fabric-1.21.1-published-0.1.7-v1",
                  "reference_id": "published-0.1.7",
                  "scenario": {
                    "id": "phase0-smoke",
                    "report_file": "report.json",
                    "completion_marker_file": "done.marker",
                    "screenshot_file": "phase0-smoke.png",
                    "world_directory_name": "etherology-original-phase0-smoke-world",
                    "world_display_name": "Etherology Original 0.1.7 Phase 0",
                    "world_seed": 19514442935972151
                  },
                  "capture": {
                    "kind": "composed-minecraft-framebuffer",
                    "width": 1920,
                    "height": 1080
                  }
                }
                """;
    }
}
