package dev.theplumteam.etherology.e2e.forge;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AtomicEvidenceWriterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void publishesCompleteReportBeforeCompletionMarker() throws IOException {
        JsonObject report = new JsonObject();
        report.addProperty("status", "passed");

        AtomicEvidenceWriter.writeReportThenMarker(temporaryDirectory, report);

        assertEquals("{\n  \"status\": \"passed\"\n}\n", Files.readString(
                temporaryDirectory.resolve("report.json")
        ));
        assertEquals("complete\n", Files.readString(
                temporaryDirectory.resolve("done.marker")
        ));
    }

    @Test
    void refusesToReplaceAnExistingReport() throws IOException {
        Path reportPath = temporaryDirectory.resolve("report.json");
        Files.writeString(reportPath, "immutable-report\n");

        assertThrows(
                IOException.class,
                () -> AtomicEvidenceWriter.writeReportThenMarker(
                        temporaryDirectory,
                        new JsonObject()
                )
        );

        assertEquals("immutable-report\n", Files.readString(reportPath));
        assertFalse(Files.exists(temporaryDirectory.resolve("done.marker")));
        assertNoTemporaryFiles();
    }

    @Test
    void refusesToReplaceAnExistingCompletionMarker() throws IOException {
        Path markerPath = temporaryDirectory.resolve("done.marker");
        Files.writeString(markerPath, "immutable-marker\n");

        assertThrows(
                IOException.class,
                () -> AtomicEvidenceWriter.writeReportThenMarker(
                        temporaryDirectory,
                        new JsonObject()
                )
        );

        assertTrue(Files.exists(temporaryDirectory.resolve("report.json")));
        assertEquals("immutable-marker\n", Files.readString(markerPath));
        assertNoTemporaryFiles();
    }

    private void assertNoTemporaryFiles() throws IOException {
        try (Stream<Path> files = Files.list(temporaryDirectory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }
}
