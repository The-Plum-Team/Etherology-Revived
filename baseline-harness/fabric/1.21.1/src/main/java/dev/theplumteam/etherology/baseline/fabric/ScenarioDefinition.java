package dev.theplumteam.etherology.baseline.fabric;

record ScenarioDefinition(
        String id,
        String screenshotFileName,
        String worldDirectoryName,
        String worldDisplayName,
        long worldSeed
) {
}
