package dev.theplumteam.etherology.baseline.fabric;

import java.util.List;

record AttrahiteServerSetupResult(
        boolean chunkLoaded,
        boolean playerCreative,
        boolean blocksPlaced,
        boolean placedStateNetworkIdsExact,
        boolean recipesExact,
        List<String> placedStates,
        List<String> placedStateNetworkIds,
        List<String> recipes,
        AttrahiteTagSnapshot tags,
        AttrahiteLootProbeResult loot,
        String worldDisplayName,
        long worldSeed,
        String dimensionId
) {
}
