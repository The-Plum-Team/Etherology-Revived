package dev.theplumteam.etherology.baseline.fabric;

import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

record AttrahiteLootProbeResult(
        long sharedSeed,
        float sharedRoll,
        boolean silkTouchExact,
        boolean plainToolEmpty,
        boolean fortuneThreeExact,
        Map<Identifier, List<String>> dropsByBlock,
        List<String> silkTouchDrops,
        List<String> plainToolDrops,
        List<String> fortuneThreeDrops
) {
}
