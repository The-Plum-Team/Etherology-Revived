package dev.theplumteam.etherology.baseline.fabric;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PedestalEvidenceWriterTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void screenshotsArePublishedExclusivelyWithoutReplacingEvidence()
            throws IOException {
        EvidenceLayout layout = layout("exclusive-screenshot");
        String fileName = PedestalBaselineContract.SCREENSHOT_FILE_NAMES.getFirst();
        Path temporary = PedestalEvidenceWriter.temporaryScreenshotPath(
                layout,
                fileName
        );
        Files.write(temporary, new byte[]{1, 2, 3});

        PedestalEvidenceWriter.publishScreenshot(layout, fileName);

        assertArrayEquals(
                new byte[]{1, 2, 3},
                Files.readAllBytes(layout.screenshotPath(fileName))
        );
        assertFalse(Files.exists(temporary));

        Files.write(temporary, new byte[]{9});
        IOException collision = assertThrows(
                IOException.class,
                () -> PedestalEvidenceWriter.publishScreenshot(layout, fileName)
        );
        assertTrue(collision.getMessage().contains("Refusing to replace"));
        assertEquals(3L, Files.size(layout.screenshotPath(fileName)));
        assertTrue(Files.exists(temporary));
    }

    @Test
    void reportAndCompletionMarkerAreHashBoundAndExclusive() throws IOException {
        EvidenceLayout layout = layout("exclusive-report");
        JsonObject report = new JsonObject();
        report.addProperty("status", "passed");

        PedestalEvidenceWriter.writeReportThenMarker(layout, report, true);

        assertEquals(
                "pedestal-baseline:passed\nreport_sha256:"
                        + ArtifactDigest.sha256(layout.reportPath()) + "\n",
                Files.readString(layout.completionMarkerPath())
        );
        assertThrows(
                IOException.class,
                () -> PedestalEvidenceWriter.writeReportThenMarker(
                        layout,
                        report,
                        true
                )
        );
    }

    @Test
    void freshnessRejectsAnyPreexistingEvidenceOrCaptureTemporary()
            throws IOException {
        EvidenceLayout layout = layout("freshness");
        PedestalEvidenceWriter.requireFreshLayout(
                layout,
                PedestalBaselineContract.SCREENSHOT_FILE_NAMES
        );
        Path temporary = PedestalEvidenceWriter.temporaryScreenshotPath(
                layout,
                PedestalBaselineContract.SCREENSHOT_FILE_NAMES.getFirst()
        );
        Files.write(temporary, new byte[]{1});

        assertThrows(
                IOException.class,
                () -> PedestalEvidenceWriter.requireFreshLayout(
                        layout,
                        PedestalBaselineContract.SCREENSHOT_FILE_NAMES
                )
        );
    }

    private EvidenceLayout layout(String name) throws IOException {
        Path scenarioRoot = temporaryDirectory.resolve(name);
        Path reports = scenarioRoot.resolve("reports");
        Path screenshots = scenarioRoot.resolve("screenshots");
        Files.createDirectories(reports);
        Files.createDirectories(screenshots);
        return new EvidenceLayout(
                PedestalBaselineScenario.DEFINITION,
                scenarioRoot,
                reports,
                screenshots,
                1920,
                1080
        );
    }
}
