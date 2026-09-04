package dev.theplumteam.etherology.e2e.forge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class AtomicEvidenceWriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private AtomicEvidenceWriter() {
    }

    static void writeReportThenMarker(
            ForgeEvidenceLayout layout,
            JsonObject report
    ) throws IOException {
        writeReportThenMarker(layout.reportsDirectory(), report);
    }

    static void writeReportThenMarker(
            Path reportsDirectory,
            JsonObject report
    ) throws IOException {
        String reportJson = GSON.toJson(report) + "\n";
        writeAtomically(reportsDirectory.resolve("report.json"), reportJson);
        writeAtomically(reportsDirectory.resolve("done.marker"), "complete\n");
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path directory = target.getParent();
        Path temporary = Files.createTempFile(directory, "." + target.getFileName() + ".", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.createLink(target, temporary);
            } catch (UnsupportedOperationException exception) {
                throw new IOException(
                        "Atomic exclusive evidence publication is unsupported for " + target,
                        exception
                );
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
