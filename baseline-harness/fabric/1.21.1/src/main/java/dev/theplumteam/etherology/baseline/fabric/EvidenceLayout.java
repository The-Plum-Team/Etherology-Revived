package dev.theplumteam.etherology.baseline.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.regex.Pattern;

record EvidenceLayout(
        ScenarioDefinition scenario,
        Path scenarioRoot,
        Path reportsDirectory,
        Path screenshotsDirectory,
        int framebufferWidth,
        int framebufferHeight
) {

    private static final String PROFILE_MARKER_FILE = ".etherology-original-profile.json";
    private static final String EVIDENCE_MARKER_FILE = ".etherology-original-evidence.json";
    private static final String MANAGED_BY = "scripts/baseline/original_client.py";
    private static final String REFERENCE_ID = "published-0.1.7";
    private static final Pattern PROFILE_ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9.-]+");
    private static final Pattern SCENARIO_ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]*");

    static EvidenceLayout resolve(
            Path gameDirectory,
            ScenarioDefinition scenario
    ) throws IOException {
        if (!SCENARIO_ID_PATTERN.matcher(scenario.id()).matches()) {
            throw new IOException("The scenario id is unsafe: " + scenario.id());
        }

        Path normalizedGameDirectory = gameDirectory.toAbsolutePath().normalize();
        Path runtimeRoot = normalizedGameDirectory.getParent();
        if (runtimeRoot == null) {
            throw new IOException("The game directory has no isolated runtime parent");
        }

        requireDirectory(normalizedGameDirectory, "game directory");
        requireDirectory(runtimeRoot, "runtime root");
        String profileId = validateProfileMarker(runtimeRoot.resolve(PROFILE_MARKER_FILE));

        Path evidenceRoot = runtimeRoot.resolve("evidence");
        requireDirectory(evidenceRoot, "evidence root");
        CaptureDimensions dimensions = validateEvidenceMarker(
                evidenceRoot.resolve(EVIDENCE_MARKER_FILE),
                profileId,
                scenario
        );

        Path scenarioRoot = evidenceRoot.resolve(scenario.id());
        Path reportsDirectory = scenarioRoot.resolve("reports");
        Path screenshotsDirectory = scenarioRoot.resolve("screenshots");
        requireDirectory(scenarioRoot, scenario.id() + " scenario root");
        requireDirectory(reportsDirectory, scenario.id() + " reports directory");
        requireDirectory(screenshotsDirectory, scenario.id() + " screenshots directory");
        return new EvidenceLayout(
                scenario,
                scenarioRoot,
                reportsDirectory,
                screenshotsDirectory,
                dimensions.width(),
                dimensions.height()
        );
    }

    Path reportPath() {
        return reportsDirectory.resolve("report.json");
    }

    Path completionMarkerPath() {
        return reportsDirectory.resolve("done.marker");
    }

    Path screenshotPath() {
        return screenshotsDirectory.resolve(scenario.screenshotFileName());
    }

    Path screenshotPath(String fileName) {
        if (fileName == null
                || fileName.isEmpty()
                || fileName.contains("/")
                || fileName.contains("\\")
                || !fileName.endsWith(".png")) {
            throw new IllegalArgumentException("The screenshot file name is unsafe");
        }
        return screenshotsDirectory.resolve(fileName);
    }

    void requireFreshTargets() throws IOException {
        requireMissing(screenshotPath(), "scenario screenshot");
        requireMissing(reportPath(), "scenario report");
        requireMissing(completionMarkerPath(), "scenario completion marker");
    }

    void requireFreshTargets(String... screenshotFileNames) throws IOException {
        if (screenshotFileNames == null || screenshotFileNames.length == 0) {
            throw new IOException("The screenshot inventory is empty");
        }
        for (String fileName : screenshotFileNames) {
            try {
                requireMissing(screenshotPath(fileName), "scenario screenshot");
            } catch (IllegalArgumentException exception) {
                throw new IOException(exception.getMessage(), exception);
            }
        }
        requireMissing(reportPath(), "scenario report");
        requireMissing(completionMarkerPath(), "scenario completion marker");
    }

    private static String validateProfileMarker(Path path) throws IOException {
        JsonObject marker = readObject(path, "isolated profile marker");
        requireInteger(marker, "schema", 1, "isolated profile marker");
        String profileId = requirePattern(
                marker,
                "profile_id",
                PROFILE_ID_PATTERN,
                "isolated profile marker"
        );
        requireString(marker, "managed_by", MANAGED_BY, "isolated profile marker");

        JsonObject isolation = requireObject(marker, "isolation", "isolated profile marker");
        requireString(isolation, "scope", "repository-owned-ignored-state", "profile isolation");
        JsonArray sourceProfiles = requireArray(isolation, "source_profiles", "profile isolation");
        if (!sourceProfiles.isEmpty()) {
            throw new IOException("The isolated profile marker names source profiles");
        }

        JsonObject reference = requireObject(marker, "reference", "isolated profile marker");
        requireString(reference, "reference_id", REFERENCE_ID, "profile reference");
        JsonObject runtime = requireObject(marker, "runtime", "isolated profile marker");
        requireString(runtime, "minecraft_version", "1.21.1", "profile runtime");
        requireString(runtime, "loader", "fabric", "profile runtime");
        requireString(runtime, "loader_version", "0.17.3", "profile runtime");
        requireInteger(runtime, "java_major", 21, "profile runtime");
        return profileId;
    }

    private static CaptureDimensions validateEvidenceMarker(
            Path path,
            String profileId,
            ScenarioDefinition expectedScenario
    ) throws IOException {
        JsonObject marker = readObject(path, "evidence marker");
        requireInteger(marker, "schema", 1, "evidence marker");
        requireString(marker, "profile_id", profileId, "evidence marker");
        requireString(marker, "reference_id", REFERENCE_ID, "evidence marker");

        JsonObject scenario = requireObject(marker, "scenario", "evidence marker");
        requireString(scenario, "id", expectedScenario.id(), "evidence scenario");
        requireString(scenario, "report_file", "report.json", "evidence scenario");
        requireString(scenario, "completion_marker_file", "done.marker", "evidence scenario");
        requireString(
                scenario,
                "screenshot_file",
                expectedScenario.screenshotFileName(),
                "evidence scenario"
        );
        requireString(
                scenario,
                "world_directory_name",
                expectedScenario.worldDirectoryName(),
                "evidence scenario"
        );
        requireString(
                scenario,
                "world_display_name",
                expectedScenario.worldDisplayName(),
                "evidence scenario"
        );
        requireLong(
                scenario,
                "world_seed",
                expectedScenario.worldSeed(),
                "evidence scenario"
        );

        JsonObject capture = requireObject(marker, "capture", "evidence marker");
        requireString(capture, "kind", "composed-minecraft-framebuffer", "evidence capture");
        int width = requirePositiveInteger(capture, "width", "evidence capture");
        int height = requirePositiveInteger(capture, "height", "evidence capture");
        return new CaptureDimensions(width, height);
    }

    private static JsonObject readObject(Path path, String label) throws IOException {
        requireRegularFile(path, label);
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                throw new IOException("The " + label + " is not a JSON object");
            }
            return element.getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("The " + label + " is not valid JSON", exception);
        }
    }

    private static JsonObject requireObject(JsonObject object, String name, String label) throws IOException {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonObject()) {
            throw new IOException("The " + label + " has no object named " + name);
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject object, String name, String label) throws IOException {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonArray()) {
            throw new IOException("The " + label + " has no array named " + name);
        }
        return element.getAsJsonArray();
    }

    private static void requireString(JsonObject object, String name, String expected, String label) throws IOException {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive() || !expected.equals(element.getAsString())) {
            throw new IOException("The " + label + " has an unexpected " + name);
        }
    }

    private static String requirePattern(
            JsonObject object,
            String name,
            Pattern pattern,
            String label
    ) throws IOException {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IOException("The " + label + " has no string named " + name);
        }

        String value = element.getAsString();
        if (!pattern.matcher(value).matches()) {
            throw new IOException("The " + label + " has an unsafe " + name);
        }
        return value;
    }

    private static void requireInteger(JsonObject object, String name, int expected, String label) throws IOException {
        int actual = requirePositiveInteger(object, name, label);
        if (actual != expected) {
            throw new IOException("The " + label + " has an unexpected " + name);
        }
    }

    private static void requireLong(
            JsonObject object,
            String name,
            long expected,
            String label
    ) throws IOException {
        JsonElement element = object.get(name);
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()
                || element.getAsLong() != expected) {
            throw new IOException("The " + label + " has an unexpected " + name);
        }
    }

    private static int requirePositiveInteger(JsonObject object, String name, String label) throws IOException {
        JsonElement element = object.get(name);
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()
                || element.getAsInt() <= 0) {
            throw new IOException("The " + label + " has an invalid " + name);
        }
        return element.getAsInt();
    }

    private static void requireDirectory(Path path, String label) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The " + label + " is missing or linked: " + path);
        }
    }

    private static void requireRegularFile(Path path, String label) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The " + label + " is missing or linked: " + path);
        }
    }

    private static void requireMissing(Path path, String label) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("Refusing to replace an existing " + label + ": " + path);
        }
    }

    private record CaptureDimensions(int width, int height) {
    }
}
