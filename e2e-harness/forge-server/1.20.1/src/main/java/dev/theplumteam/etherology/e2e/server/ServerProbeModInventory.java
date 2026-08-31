package dev.theplumteam.etherology.e2e.server;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ServerProbeModInventory {

    private ServerProbeModInventory() {
    }

    static List<String> parseDeclaredIds(String rawIds) {
        if (rawIds == null || rawIds.isBlank()) {
            throw new IllegalArgumentException("The declared mod-id property is missing");
        }

        List<String> modIds = List.of(rawIds.split(",", -1));
        if (modIds.stream().anyMatch(modId -> !modId.matches("[a-z0-9][a-z0-9_-]*"))) {
            throw new IllegalArgumentException("The declared mod-id property is unsafe");
        }
        if (new HashSet<>(modIds).size() != modIds.size()) {
            throw new IllegalArgumentException("The declared mod-id property contains duplicates");
        }
        return modIds;
    }

    static List<String> sortedUniqueIds(Collection<String> modIds) {
        return modIds.stream()
                .distinct()
                .sorted()
                .toList();
    }

    static List<String> sortedIntersection(
            Collection<String> loadedModIds,
            Collection<String> declaredModIds
    ) {
        Set<String> loadedModIdSet = Set.copyOf(loadedModIds);
        return declaredModIds.stream()
                .filter(loadedModIdSet::contains)
                .distinct()
                .sorted()
                .toList();
    }
}
