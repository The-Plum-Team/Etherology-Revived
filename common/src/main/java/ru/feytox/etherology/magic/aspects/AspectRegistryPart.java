package ru.feytox.etherology.magic.aspects;

import com.mojang.serialization.Codec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents one independently addressable entry in the synchronized aspects datapack registry.
 */
public record AspectRegistryPart(Map<AspectContainerId, AspectEntry> aspectEntries) {

    private static final Logger LOGGER = LoggerFactory.getLogger(AspectRegistryPart.class);

    public static final Codec<AspectRegistryPart> CODEC = Codec
            .unboundedMap(AspectContainerId.CODEC, AspectEntry.CODEC)
            .xmap(AspectRegistryPart::new, AspectRegistryPart::aspectEntries);

    /**
     * Resolves parent references for every entry in this merged registry part.
     *
     * @return resolved aspect containers indexed by their typed identifiers
     */
    public Map<AspectContainerId, AspectContainer> applyParents() {
        Lookup lookup = new Lookup(aspectEntries, new HashMap<>());
        aspectEntries.keySet().forEach(lookup::get);
        return lookup.results();
    }

    /**
     * Combines two datapack registry parts using the authored priority contract.
     *
     * @param first first registry part
     * @param second second registry part
     * @return merged registry part
     */
    public static AspectRegistryPart merge(
            AspectRegistryPart first,
            AspectRegistryPart second
    ) {
        Map<AspectContainerId, AspectEntry> entries = Stream
                .concat(
                        first.aspectEntries().entrySet().stream(),
                        second.aspectEntries().entrySet().stream()
                )
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (firstEntry, secondEntry) -> {
                            if (firstEntry.priority() > secondEntry.priority()) {
                                return firstEntry;
                            }
                            if (firstEntry.priority() < secondEntry.priority()) {
                                return secondEntry;
                            }
                            LOGGER.error(
                                    "Found 2 aspect entries with the same ID and priority. "
                                            + "Choosing the first one (most likely a random choice)."
                            );
                            return firstEntry;
                        },
                        HashMap::new
                ));
        return new AspectRegistryPart(entries);
    }

    /**
     * Resolves parent chains while memoizing each completed aspect container.
     */
    public record Lookup(
            Map<AspectContainerId, AspectEntry> aspectEntries,
            Map<AspectContainerId, AspectContainer> results
    ) {

        /**
         * Resolves one typed identifier, recursively resolving its parents when necessary.
         *
         * @param id entry identifier
         * @return resolved aspects
         * @throws NoSuchElementException when a referenced parent does not exist
         */
        public AspectContainer get(AspectContainerId id) {
            if (results.containsKey(id)) return results.get(id);

            AspectEntry entry = aspectEntries.get(id);
            if (entry == null) {
                throw new NoSuchElementException(
                        "Could not find entry %s.".formatted(id)
                );
            }

            AspectContainer container = entry.toContainer(this);
            results.put(id, container);
            return container;
        }
    }
}
