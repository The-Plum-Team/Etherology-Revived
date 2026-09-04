package dev.theplumteam.etherology.e2e.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

record EvidenceLayout(
        Path scenarioRoot,
        Path reportsDirectory,
        Path screenshotsDirectory,
        String profileId
) {

    private static final String ARTIFACT_NODE = "fabric-1.20.1";
    static final String PROFILE_ID = "etherology-e2e-fabric-1.20.1-v31";
    private static final List<String> SCENARIO_IDS = List.of(
            "phase0-smoke",
            "progression-oculus",
            "seals-aspects",
            "golden-forest",
            "alchemy",
            "ether-network",
            "staff-lenses",
            "spiritual-energy",
            "armillary",
            "storage-utilities",
            "combat-equipment",
            "persistence",
            "multiplayer-sync",
            "metal-block-registry",
            "forest-lantern",
            "attrahite-block-registry",
            "slitherite-block-registry"
    );
    private static final Pattern PROFILE_ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9.-]+");
    private static final Pattern SCENARIO_ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]*");

    static EvidenceLayout resolve(Path gameDirectory, String scenarioId) throws IOException {
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
        String profileId = validateProfileMarker(runtimeRoot.resolve(".etherology-e2e-profile.json"));

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
        return new EvidenceLayout(
                scenarioRoot,
                reportsDirectory,
                screenshotsDirectory,
                profileId
        );
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
        String profileId = requirePattern(
                marker,
                "profile_id",
                PROFILE_ID_PATTERN,
                "isolated profile marker"
        );
        if (!PROFILE_ID.equals(profileId)) {
            throw new IOException("The isolated profile marker has the wrong profile_id");
        }
        requireString(marker, "managed_by", "scripts/e2e/client.py", "isolated profile marker");

        JsonObject isolation = requireObject(marker, "isolation", "isolated profile marker");
        requireString(isolation, "scope", "repository-owned-ignored-state", "profile isolation");
        JsonArray sourceProfiles = requireArray(isolation, "source_profiles", "profile isolation");
        if (!sourceProfiles.isEmpty()) {
            throw new IOException("The isolated profile marker names source profiles");
        }

        JsonObject release = requireObject(marker, "release", "isolated profile marker");
        requireString(release, "artifact_node", ARTIFACT_NODE, "profile release");
        requireString(release, "minecraft_version", "1.20.1", "profile release");
        requireString(release, "loader", "fabric", "profile release");
        requireString(release, "loader_version", "0.17.3", "profile release");
        requireInteger(release, "java", 17, "profile release");
        return profileId;
    }

    private static void validateEvidenceMarker(Path path, String profileId, String scenarioId) throws IOException {
        JsonObject marker = readObject(path, "evidence marker");
        requireInteger(marker, "schema", 1, "evidence marker");
        requireString(marker, "profile_id", profileId, "evidence marker");
        requireString(marker, "artifact_node", ARTIFACT_NODE, "evidence marker");

        JsonArray scenarios = requireArray(marker, "scenarios", "evidence marker");
        if (scenarios.size() != SCENARIO_IDS.size()) {
            throw new IOException("The evidence marker scenario inventory changed");
        }
        for (int index = 0; index < SCENARIO_IDS.size(); index++) {
            JsonElement scenario = scenarios.get(index);
            if (!scenario.isJsonPrimitive()
                    || !SCENARIO_IDS.get(index).equals(scenario.getAsString())) {
                throw new IOException("The evidence marker scenario order changed");
            }
        }
        if (!SCENARIO_IDS.contains(scenarioId)) {
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
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The " + label + " is missing or linked: " + path);
        }
    }

    private static void requireMissing(Path path, String label) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("Refusing to replace an existing " + label + ": " + path);
        }
    }
}
