package dev.theplumteam.etherology.baseline.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class AtomicEvidenceWriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private AtomicEvidenceWriter() {
    }

    static void writeReportThenMarker(
            EvidenceLayout layout,
            JsonObject report,
            boolean passed
    ) throws IOException {
        String reportContent = GSON.toJson(report) + "\n";
        writeAtomically(layout.reportPath(), reportContent);
        String reportSha256 = ArtifactDigest.sha256(layout.reportPath());
        String status = passed ? "passed" : "failed";
        String markerContent = PhaseZeroScenario.SCENARIO_ID + ":" + status + "\n"
                + "report_sha256:" + reportSha256 + "\n";
        writeAtomically(layout.completionMarkerPath(), markerContent);
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path directory = target.getParent();
        Path temporary = Files.createTempFile(directory, "." + target.getFileName() + ".", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("Atomic evidence publication is unsupported for " + target, exception);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
