package dev.theplumteam.etherology.e2e.server;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ServerProbeReportWriterTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void publishesOnlyTheRequestedReport() throws IOException {
        Path evidenceRoot = createEvidenceRoot();
        JsonObject report = new JsonObject();
        report.addProperty("status", "passed");

        ServerProbeReportWriter.write(evidenceRoot, report);

        Path reportsDirectory = evidenceRoot.resolve("reports");
        List<String> reportFileNames;
        try (Stream<Path> reportFiles = Files.list(reportsDirectory)) {
            reportFileNames = reportFiles
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }
        assertEquals(
                List.of("report.json"),
                reportFileNames
        );
        assertEquals(
                "{\n  \"status\": \"passed\"\n}\n",
                Files.readString(reportsDirectory.resolve("report.json"))
        );
    }

    @Test
    void refusesToOverwriteAPriorReport() throws IOException {
        Path evidenceRoot = createEvidenceRoot();
        Path reportPath = evidenceRoot.resolve("reports/report.json");
        Files.writeString(reportPath, "prior\n");

        IOException exception = assertThrows(
                IOException.class,
                () -> ServerProbeReportWriter.write(evidenceRoot, new JsonObject())
        );

        assertTrue(exception.getMessage().contains("already exists"));
        assertEquals("prior\n", Files.readString(reportPath));
        try (Stream<Path> reportFiles = Files.list(reportPath.getParent())) {
            assertEquals(
                    List.of("report.json"),
                    reportFiles
                            .map(path -> path.getFileName().toString())
                            .sorted()
                            .toList()
            );
        }
    }

    @Test
    void rejectsRelativeAndLinkedEvidenceRoots() throws IOException {
        IOException relativeException = assertThrows(
                IOException.class,
                () -> ServerProbeReportWriter.resolveEvidenceRoot(Path.of("evidence").toString())
        );
        assertTrue(relativeException.getMessage().contains("not absolute"));

        Path realRoot = createEvidenceRoot();
        Path linkedRoot = temporaryDirectory.resolve("linked-evidence");
        Files.createSymbolicLink(linkedRoot, realRoot);
        IOException linkedException = assertThrows(
                IOException.class,
                () -> ServerProbeReportWriter.resolveEvidenceRoot(linkedRoot.toString())
        );
        assertTrue(linkedException.getMessage().contains("missing or linked"));
    }

    private Path createEvidenceRoot() throws IOException {
        Path evidenceRoot = temporaryDirectory.resolve("evidence-" + System.nanoTime());
        Files.createDirectories(evidenceRoot.resolve("reports"));
        return evidenceRoot;
    }
}
