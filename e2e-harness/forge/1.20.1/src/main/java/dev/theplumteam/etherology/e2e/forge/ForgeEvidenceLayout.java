package dev.theplumteam.etherology.e2e.forge;

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

record ForgeEvidenceLayout(Path scenarioRoot, Path reportsDirectory, Path screenshotsDirectory) {

    private static final String ARTIFACT_NODE = "forge-1.20.1";
    private static final String PROFILE_ID = "etherology-e2e-forge-1.20.1-v7";
    private static final Pattern SCENARIO_ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]*");

    static ForgeEvidenceLayout resolve(Path gameDirectory, String scenarioId) throws IOException {
        if (!SCENARIO_ID_PATTERN.matcher(scenarioId).matches()) {
            throw new IOException("The scenario id is unsafe: " + scenarioId);
        }

        Path normalizedGameDirectory = gameDirectory.toAbsolutePath().normalize();
        Path runtimeRoot = normalizedGameDirectory.getParent();
        if (runtimeRoot == null) {
            throw new IOException("The game directory has no isolated runtime parent");
        }

        requireDirectory(normalizedGameDirectory, "game directory");
        requireDirectory(runtimeRoot, "runtime root");
        String profileId = validateProfileMarker(
                runtimeRoot.resolve(".etherology-forge-e2e-profile.json")
        );

        Path evidenceRoot = runtimeRoot.resolve("evidence");
        requireDirectory(evidenceRoot, "evidence root");
        validateEvidenceMarker(
                evidenceRoot.resolve(".etherology-e2e-evidence.json"),
                profileId,
                scenarioId
        );

        Path scenarioRoot = evidenceRoot.resolve(scenarioId);
        Path reportsDirectory = scenarioRoot.resolve("reports");
        Path screenshotsDirectory = scenarioRoot.resolve("screenshots");
        requireDirectory(scenarioRoot, scenarioId + " scenario root");
        requireDirectory(reportsDirectory, scenarioId + " reports directory");
        requireDirectory(screenshotsDirectory, scenarioId + " screenshots directory");
        return new ForgeEvidenceLayout(scenarioRoot, reportsDirectory, screenshotsDirectory);
    }

    Path screenshotPath(String fileName) {
        return screenshotsDirectory.resolve(fileName);
    }

    void requireFreshTargets(String... screenshotFileNames) throws IOException {
        for (String screenshotFileName : screenshotFileNames) {
            Path screenshotPath = Path.of(screenshotFileName);
            if (screenshotPath.getNameCount() != 1 || !screenshotFileName.endsWith(".png")) {
                throw new IOException("The screenshot file name is unsafe: " + screenshotFileName);
            }
            requireMissing(screenshotPath(screenshotFileName), "scenario screenshot");
        }
        requireMissing(reportsDirectory.resolve("report.json"), "scenario report");
        requireMissing(reportsDirectory.resolve("done.marker"), "scenario completion marker");
    }

    private static String validateProfileMarker(Path path) throws IOException {
        JsonObject marker = readObject(path, "isolated profile marker");
        requireInteger(marker, "schema", 1, "isolated profile marker");
        requireString(marker, "profile_id", PROFILE_ID, "isolated profile marker");
        requireString(
                marker,
                "managed_by",
                "scripts/e2e/forge_client.py",
                "isolated profile marker"
        );

        JsonObject isolation = requireObject(marker, "isolation", "isolated profile marker");
        requireString(isolation, "scope", "repository-owned-ignored-state", "profile isolation");
        JsonArray sourceProfiles = requireArray(isolation, "source_profiles", "profile isolation");
        if (!sourceProfiles.isEmpty()) {
            throw new IOException("The isolated profile marker names source profiles");
        }

        JsonObject release = requireObject(marker, "release", "isolated profile marker");
        requireString(release, "artifact_node", ARTIFACT_NODE, "profile release");
        requireString(release, "minecraft_version", "1.20.1", "profile release");
        requireString(release, "loader", "forge", "profile release");
        requireInteger(release, "java", 17, "profile release");
        return PROFILE_ID;
    }

    private static void validateEvidenceMarker(
            Path path,
            String profileId,
            String scenarioId
    ) throws IOException {
        JsonObject marker = readObject(path, "evidence marker");
        requireInteger(marker, "schema", 1, "evidence marker");
        requireString(marker, "profile_id", profileId, "evidence marker");
        requireString(
                marker,
                "managed_by",
                "scripts/e2e/forge_client.py",
                "evidence marker"
        );
        requireString(marker, "artifact_node", ARTIFACT_NODE, "evidence marker");
        requireString(marker, "loader", "forge", "evidence marker");
        requireInteger(marker, "java", 17, "evidence marker");

        JsonArray scenarios = requireArray(marker, "scenarios", "evidence marker");
        boolean scenarioDeclared = false;
        for (JsonElement scenario : scenarios) {
            if (scenario.isJsonPrimitive() && scenarioId.equals(scenario.getAsString())) {
                scenarioDeclared = true;
                break;
            }
        }
        if (!scenarioDeclared) {
            throw new IOException("The evidence marker does not declare " + scenarioId);
        }

        JsonObject capture = requireObject(marker, "capture", "evidence marker");
        requireString(capture, "kind", "composed-minecraft-framebuffer", "evidence capture");
        requireInteger(capture, "width", 1920, "evidence capture");
        requireInteger(capture, "height", 1080, "evidence capture");
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

    private static JsonObject requireObject(
            JsonObject object,
            String name,
            String label
    ) throws IOException {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonObject()) {
            throw new IOException("The " + label + " has no object named " + name);
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(
            JsonObject object,
            String name,
            String label
    ) throws IOException {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonArray()) {
            throw new IOException("The " + label + " has no array named " + name);
        }
        return element.getAsJsonArray();
    }

    private static void requireString(
            JsonObject object,
            String name,
            String expected,
            String label
    ) throws IOException {
        JsonElement element = object.get(name);
        if (element == null
                || !element.isJsonPrimitive()
                || !expected.equals(element.getAsString())) {
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

    private static void requireInteger(
            JsonObject object,
            String name,
            int expected,
            String label
    ) throws IOException {
        JsonElement element = object.get(name);
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()
                || element.getAsInt() != expected) {
            throw new IOException("The " + label + " has an unexpected " + name);
        }
    }

    private static void requireDirectory(Path path, String label) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The " + label + " is missing or linked: " + path);
        }
    }

    private static void requireRegularFile(Path path, String label) throws IOException {
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The " + label + " is missing or linked: " + path);
        }
    }

    private static void requireMissing(Path path, String label) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("Refusing to replace an existing " + label + ": " + path);
        }
    }
}
