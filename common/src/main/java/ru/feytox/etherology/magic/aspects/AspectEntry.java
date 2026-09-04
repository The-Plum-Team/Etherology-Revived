package ru.feytox.etherology.magic.aspects;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.RecordBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Stores directly authored aspects, optional parents, and merge priority for one datapack key.
 */
public record AspectEntry(
        AspectContainer aspects,
        List<AspectContainerId> parents,
        int priority
) {

    private static final Logger LOGGER = LoggerFactory.getLogger(AspectEntry.class);

    public static final Codec<AspectEntry> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<AspectEntry, T>> decode(
                DynamicOps<T> ops,
                T input
        ) {
            return ops.getMap(input)
                    .setLifecycle(Lifecycle.stable())
                    .map(mapLike -> {
                        AtomicInteger priority = new AtomicInteger();
                        List<AspectContainerId> parents = new ArrayList<>();

                        Stream<Pair<T, T>> aspectsStream = mapLike.entries()
                                .filter(pair -> switch (ops
                                        .getStringValue(pair.getFirst())
                                        .getOrThrow(false, LOGGER::error)) {
                                    case "priority" -> {
                                        priority.set(Codec.INT
                                                .parse(ops, pair.getSecond())
                                                .getOrThrow(false, LOGGER::error));
                                        yield false;
                                    }
                                    case "parents" -> {
                                        parents.addAll(AspectContainerId.CODEC
                                                .listOf()
                                                .parse(ops, pair.getSecond())
                                                .getOrThrow(false, LOGGER::error));
                                        yield false;
                                    }
                                    case "parent" -> {
                                        parents.add(AspectContainerId.CODEC
                                                .parse(ops, pair.getSecond())
                                                .getOrThrow(false, LOGGER::error));
                                        yield false;
                                    }
                                    default -> true;
                                });
                        AspectContainer aspects = AspectContainer.parse(
                                ops,
                                aspectsStream
                        );
                        return new AspectEntry(aspects, parents, priority.get());
                    })
                    .map(entry -> Pair.of(entry, input));
        }

        @Override
        public <T> DataResult<T> encode(
                AspectEntry input,
                DynamicOps<T> ops,
                T prefix
        ) {
            RecordBuilder<T> builder = ops.mapBuilder();
            if (input.priority() != 0) {
                builder.add(
                        "priority",
                        Codec.INT.encodeStart(ops, input.priority())
                );
            }
            if (input.parents().size() == 1) {
                builder.add(
                        "parent",
                        AspectContainerId.CODEC.encodeStart(
                                ops,
                                input.parents().get(0)
                        )
                );
            } else if (input.parents().size() > 1) {
                builder.add(
                        "parents",
                        AspectContainerId.CODEC.listOf()
                                .encodeStart(ops, input.parents())
                );
            }
            AspectContainer.encodeStart(builder, ops, input.aspects());
            return builder.build(prefix);
        }
    };

    /**
     * Resolves inherited aspects before adding this entry's directly authored values.
     *
     * @param lookup registry-part lookup responsible for resolving parents
     * @return merged aspect container
     */
    public AspectContainer toContainer(AspectRegistryPart.Lookup lookup) {
        if (parents.isEmpty()) return aspects;

        return parents.stream()
                .map(lookup::get)
                .reduce(AspectContainer::add)
                .orElseThrow()
                .add(aspects);
    }
}
