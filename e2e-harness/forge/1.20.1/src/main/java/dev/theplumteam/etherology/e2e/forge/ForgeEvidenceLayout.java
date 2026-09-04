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

record ForgeEvidenceLayout(
        Path scenarioRoot,
        Path reportsDirectory,
        Path screenshotsDirectory,
        String profileId,
        long profileManifestSize,
        String profileManifestSha256
) {

    private static final String ARTIFACT_NODE = "forge-1.20.1";
    static final String PROFILE_ID = "etherology-e2e-forge-1.20.1-v18";
    private static final String PROFILE_MANIFEST_PATH =
            "scripts/e2e/forge-1.20.1-profile.json";
    private static final Pattern SCENARIO_ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]*");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern POSITIVE_INTEGER_PATTERN = Pattern.compile("[1-9][0-9]*");

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
        ProfileProvenance profile = validateProfileMarker(
                runtimeRoot.resolve(".etherology-forge-e2e-profile.json")
        );

        Path evidenceRoot = runtimeRoot.resolve("evidence");
        requireDirectory(evidenceRoot, "evidence root");
        validateEvidenceMarker(
                evidenceRoot.resolve(".etherology-e2e-evidence.json"),
                profile.profileId(),
                scenarioId
        );

        Path scenarioRoot = evidenceRoot.resolve(scenarioId);
        Path reportsDirectory = scenarioRoot.resolve("reports");
        Path screenshotsDirectory = scenarioRoot.resolve("screenshots");
        requireDirectory(scenarioRoot, scenarioId + " scenario root");
        requireDirectory(reportsDirectory, scenarioId + " reports directory");
        requireDirectory(screenshotsDirectory, scenarioId + " screenshots directory");
        return new ForgeEvidenceLayout(
                scenarioRoot,
                reportsDirectory,
                screenshotsDirectory,
                profile.profileId(),
                profile.manifestSize(),
                profile.manifestSha256()
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

    private static ProfileProvenance validateProfileMarker(Path path) throws IOException {
        JsonObject marker = readObject(path, "isolated profile marker");
        requireInteger(marker, "schema", 1, "isolated profile marker");
        requireString(marker, "profile_id", PROFILE_ID, "isolated profile marker");
        requireString(
                marker,
                "managed_by",
                "scripts/e2e/forge_client.py",
                "isolated profile marker"
        );

        JsonObject profileManifest = requireObject(
                marker,
                "profile_manifest",
                "isolated profile marker"
        );
        if (profileManifest.size() != 3) {
            throw new IOException("The profile manifest provenance field inventory changed");
        }
        requireString(
                profileManifest,
                "path",
                PROFILE_MANIFEST_PATH,
                "profile manifest provenance"
        );
        long profileManifestSize = requirePositiveLong(
                profileManifest,
                "size",
                "profile manifest provenance"
        );
        String profileManifestSha256 = requirePattern(
                profileManifest,
                "sha256",
                SHA256_PATTERN,
                "profile manifest provenance"
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
        requireString(
                release,
                "loader_version",
                "1.20.1-47.4.9",
                "profile release"
        );
        requireInteger(release, "java", 17, "profile release");
        return new ProfileProvenance(
                PROFILE_ID,
                profileManifestSize,
                profileManifestSha256
        );
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
        if (scenarios.size() != 5
                || !scenarios.get(0).isJsonPrimitive()
                || !scenarios.get(1).isJsonPrimitive()
                || !scenarios.get(2).isJsonPrimitive()
                || !scenarios.get(3).isJsonPrimitive()
                || !scenarios.get(4).isJsonPrimitive()
                || !ScenarioSelection.ETHEREAL_STORAGE.equals(
                    scenarios.get(0).getAsString()
                )
                || !ScenarioSelection.ETHEREAL_CHANNEL.equals(
                    scenarios.get(1).getAsString()
                )
                || !ScenarioSelection.FOREST_LANTERN.equals(
                    scenarios.get(2).getAsString()
                )
                || !ScenarioSelection.ATTRAHITE_BLOCK_REGISTRY.equals(
                    scenarios.get(3).getAsString()
                )
                || !ScenarioSelection.SLITHERITE_BLOCK_REGISTRY.equals(
                    scenarios.get(4).getAsString()
                )) {
            throw new IOException("The evidence marker scenario order changed");
        }
        if (!ScenarioSelection.ETHEREAL_STORAGE.equals(scenarioId)
                && !ScenarioSelection.ETHEREAL_CHANNEL.equals(scenarioId)
                && !ScenarioSelection.FOREST_LANTERN.equals(scenarioId)
                && !ScenarioSelection.ATTRAHITE_BLOCK_REGISTRY.equals(scenarioId)
                && !ScenarioSelection.SLITHERITE_BLOCK_REGISTRY.equals(scenarioId)) {
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

    private static long requirePositiveLong(
            JsonObject object,
            String name,
            String label
    ) throws IOException {
        JsonElement element = object.get(name);
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()
                || !POSITIVE_INTEGER_PATTERN.matcher(element.getAsString()).matches()) {
            throw new IOException("The " + label + " has an invalid " + name);
        }
        try {
            return element.getAsLong();
        } catch (NumberFormatException exception) {
            throw new IOException("The " + label + " has an invalid " + name, exception);
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

    private record ProfileProvenance(
            String profileId,
            long manifestSize,
            String manifestSha256
    ) {
    }
}
