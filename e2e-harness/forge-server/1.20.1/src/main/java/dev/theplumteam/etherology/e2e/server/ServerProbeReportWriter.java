package dev.theplumteam.etherology.e2e.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

final class ServerProbeReportWriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ServerProbeReportWriter() {
    }

    static Path resolveEvidenceRoot(String rawEvidenceRoot) throws IOException {
        if (rawEvidenceRoot == null || rawEvidenceRoot.isBlank()) {
            throw new IOException("The server-probe evidence root property is missing");
        }

        Path evidenceRoot = Path.of(rawEvidenceRoot);
        if (!evidenceRoot.isAbsolute()) {
            throw new IOException("The server-probe evidence root is not absolute");
        }

        Path normalizedEvidenceRoot = evidenceRoot.normalize();
        requireDirectory(normalizedEvidenceRoot, "server-probe evidence root");
        requireDirectory(
                normalizedEvidenceRoot.resolve("reports"),
                "server-probe reports directory"
        );
        return normalizedEvidenceRoot;
    }

    static void write(Path evidenceRoot, JsonObject report) throws IOException {
        Path reportsDirectory = evidenceRoot.resolve("reports");
        requireDirectory(reportsDirectory, "server-probe reports directory");
        Path reportPath = reportsDirectory.resolve("report.json");
        if (Files.exists(reportPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The server-probe report already exists: " + reportPath);
        }

        String reportJson = GSON.toJson(report) + "\n";
        Path temporaryPath = Files.createTempFile(reportsDirectory, ".report.json.", ".tmp");
        try {
            Files.writeString(temporaryPath, reportJson, StandardCharsets.UTF_8);
            try {
                Files.createLink(reportPath, temporaryPath);
            } catch (FileAlreadyExistsException exception) {
                throw new IOException(
                        "The server-probe report already exists: " + reportPath,
                        exception
                );
            } catch (UnsupportedOperationException exception) {
                throw new IOException(
                        "Atomic exclusive server-probe report publication is unsupported for "
                                + reportPath,
                        exception
                );
            }
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    private static void requireDirectory(Path path, String label) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)) {
            throw new IOException("The " + label + " is missing or linked: " + path);
        }
    }
}
