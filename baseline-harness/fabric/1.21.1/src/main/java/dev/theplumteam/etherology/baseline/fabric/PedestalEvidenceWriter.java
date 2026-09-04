package dev.theplumteam.etherology.baseline.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Exclusively publishes Pedestal screenshots, then the hash-bound final report.
 */
final class PedestalEvidenceWriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PedestalEvidenceWriter() {
    }

    static void requireFreshLayout(
            EvidenceLayout layout,
            Iterable<String> screenshotFileNames
    ) throws IOException {
        requireDirectory(layout.reportsDirectory(), "Pedestal reports directory");
        requireDirectory(layout.screenshotsDirectory(), "Pedestal screenshots directory");
        requireEmpty(layout.reportsDirectory(), "Pedestal reports directory");
        requireEmpty(layout.screenshotsDirectory(), "Pedestal screenshots directory");
        Set<String> names = new HashSet<>();
        for (String fileName : screenshotFileNames) {
            Path target = layout.screenshotPath(fileName);
            if (!names.add(fileName)) {
                throw new IOException("The Pedestal screenshot inventory is duplicated");
            }
            requireMissing(target, "Pedestal screenshot");
            requireMissing(temporaryScreenshotPath(layout, fileName),
                    "Pedestal temporary screenshot");
        }
        if (names.isEmpty()) {
            throw new IOException("The Pedestal screenshot inventory is empty");
        }
        requireMissing(layout.reportPath(), "Pedestal report");
        requireMissing(layout.completionMarkerPath(), "Pedestal completion marker");
    }

    static Path temporaryScreenshotPath(EvidenceLayout layout, String fileName) {
        if (fileName == null
                || fileName.isEmpty()
                || fileName.contains("/")
                || fileName.contains("\\")
                || !fileName.endsWith(".png")) {
            throw new IllegalArgumentException("The Pedestal screenshot file name is unsafe");
        }
        return layout.screenshotsDirectory().resolve("." + fileName + ".capture.png");
    }

    static void publishScreenshot(EvidenceLayout layout, String fileName)
            throws IOException {
        Path temporary = temporaryScreenshotPath(layout, fileName);
        Path target = layout.screenshotPath(fileName);
        requireRegularFile(temporary, "Pedestal temporary screenshot");
        publishExistingFileExclusive(temporary, target, "Pedestal screenshot");
        Files.delete(temporary);
    }

    static void writeReportThenMarker(
            EvidenceLayout layout,
            JsonObject report,
            boolean passed
    ) throws IOException {
        String reportContent = GSON.toJson(report) + "\n";
        publishBytesExclusive(
                layout.reportPath(),
                reportContent.getBytes(StandardCharsets.UTF_8),
                "Pedestal report"
        );
        String reportSha256 = ArtifactDigest.sha256(layout.reportPath());
        String status = passed ? "passed" : "failed";
        String markerContent = layout.scenario().id() + ":" + status + "\n"
                + "report_sha256:" + reportSha256 + "\n";
        publishBytesExclusive(
                layout.completionMarkerPath(),
                markerContent.getBytes(StandardCharsets.UTF_8),
                "Pedestal completion marker"
        );
    }

    private static void publishBytesExclusive(
            Path target,
            byte[] content,
            String label
    ) throws IOException {
        Path directory = target.getParent();
        requireDirectory(directory, label + " directory");
        requireMissing(target, label);
        Path temporary = Files.createTempFile(
                directory,
                "." + target.getFileName() + ".",
                ".tmp"
        );
        try {
            Files.write(temporary, content);
            publishExistingFileExclusive(temporary, target, label);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void publishExistingFileExclusive(
            Path source,
            Path target,
            String label
    ) throws IOException {
        try {
            Files.createLink(target, source);
        } catch (FileAlreadyExistsException exception) {
            throw new IOException("Refusing to replace an existing " + label + ": "
                    + target, exception);
        } catch (UnsupportedOperationException exception) {
            throw new IOException("Atomic exclusive " + label
                    + " publication is unsupported for " + target, exception);
        }
    }

    private static void requireEmpty(Path directory, String label) throws IOException {
        try (var entries = Files.list(directory)) {
            if (entries.findAny().isPresent()) {
                throw new IOException("The " + label + " is not fresh: " + directory);
            }
        }
    }

    private static void requireDirectory(Path path, String label) throws IOException {
        if (Files.isSymbolicLink(path)
                || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The " + label + " is missing or linked: " + path);
        }
    }

    private static void requireRegularFile(Path path, String label) throws IOException {
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) <= 0L) {
            throw new IOException("The " + label + " is missing, empty, or linked: "
                    + path);
        }
    }

    private static void requireMissing(Path path, String label) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)) {
            throw new IOException("Refusing to replace an existing " + label + ": "
                    + path);
        }
    }
}
