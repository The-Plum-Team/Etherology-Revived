package dev.theplumteam.etherology.baseline.fabric;

import java.util.List;

record AttrahiteRegistrySnapshot(
        boolean classesExact,
        boolean blockItemsExact,
        boolean defaultStatesExact,
        boolean stateCountsExact,
        boolean networkIdsExact,
        List<String> descriptions
) {

    boolean passed() {
        return classesExact
                && blockItemsExact
                && defaultStatesExact
                && stateCountsExact
                && networkIdsExact;
    }
}
