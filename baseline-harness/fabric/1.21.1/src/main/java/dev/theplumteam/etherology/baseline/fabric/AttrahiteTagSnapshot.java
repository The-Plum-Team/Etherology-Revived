package dev.theplumteam.etherology.baseline.fabric;

import java.util.List;

record AttrahiteTagSnapshot(
        boolean blockTagsExact,
        boolean itemTagsExact,
        List<String> descriptions
) {
}
