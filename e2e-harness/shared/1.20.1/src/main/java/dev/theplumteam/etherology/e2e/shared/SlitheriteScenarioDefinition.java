package dev.theplumteam.etherology.e2e.shared;

/**
 * Carries the deterministic world and evidence identity validated by each loader.
 *
 * @param id scenario selector and evidence directory id
 * @param initialScreenshotFileName first native framebuffer capture name
 * @param reopenedScreenshotFileName post-restart framebuffer capture name
 * @param worldDirectoryName isolated save directory name
 * @param worldDisplayName level name expected after creation and reopen
 * @param worldSeed deterministic generator seed
 */
public record SlitheriteScenarioDefinition(
        String id,
        String initialScreenshotFileName,
        String reopenedScreenshotFileName,
        String worldDirectoryName,
        String worldDisplayName,
        long worldSeed
) {
}
