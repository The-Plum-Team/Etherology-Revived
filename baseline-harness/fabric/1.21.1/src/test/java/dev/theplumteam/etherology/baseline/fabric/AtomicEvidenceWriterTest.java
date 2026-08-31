package dev.theplumteam.etherology.baseline.fabric;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AtomicEvidenceWriterTest {

    @TempDir
    private Path temporaryDirectory;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void completionMarkerBindsTheExactPublishedReport(boolean passed) throws IOException {
        Path scenarioRoot = temporaryDirectory.resolve(Boolean.toString(passed));
        Path reports = scenarioRoot.resolve("reports");
        Path screenshots = scenarioRoot.resolve("screenshots");
        Files.createDirectories(reports);
        Files.createDirectories(screenshots);
        EvidenceLayout layout = new EvidenceLayout(
                scenarioRoot,
                reports,
                screenshots,
                1920,
                1080
        );
        JsonObject report = new JsonObject();
        report.addProperty("status", passed ? "passed" : "failed");

        AtomicEvidenceWriter.writeReportThenMarker(layout, report, passed);

        String status = passed ? "passed" : "failed";
        assertEquals(
                PhaseZeroScenario.SCENARIO_ID + ":" + status + "\n"
                        + "report_sha256:" + ArtifactDigest.sha256(layout.reportPath()) + "\n",
                Files.readString(layout.completionMarkerPath())
        );
    }
}
