package dev.theplumteam.etherology.e2e.shared;

import com.google.gson.JsonObject;

import java.nio.file.Path;

/**
 * Holds loader-validated evidence paths and their immutable profile provenance.
 *
 * @param scenarioRoot root passed to Minecraft's screenshot recorder
 * @param reportsDirectory destination for the report and completion marker
 * @param screenshotsDirectory destination created by the screenshot recorder
 * @param framebufferWidth marker-pinned capture width in physical pixels
 * @param framebufferHeight marker-pinned capture height in physical pixels
 * @param provenanceFields loader-validated typed provenance for the top-level report
 */
public record SlitheriteEvidenceTarget(
        Path scenarioRoot,
        Path reportsDirectory,
        Path screenshotsDirectory,
        int framebufferWidth,
        int framebufferHeight,
        JsonObject provenanceFields
) {

    /**
     * Freezes provenance so the final report describes the target that was resolved.
     */
    public SlitheriteEvidenceTarget {
        provenanceFields = provenanceFields.deepCopy();
    }

    /**
     * Returns an independent copy so publication cannot mutate validated provenance.
     */
    public JsonObject provenanceFields() {
        return provenanceFields.deepCopy();
    }

    /**
     * Resolves one flat PNG name under the validated screenshot directory.
     */
    public Path screenshotPath(String fileName) {
        Path relativePath = Path.of(fileName);
        if (relativePath.getNameCount() != 1
                || fileName.contains("\\")
                || !fileName.endsWith(".png")) {
            throw new IllegalArgumentException(
                    "The screenshot file name is unsafe: " + fileName
            );
        }
        return screenshotsDirectory.resolve(relativePath);
    }
}
