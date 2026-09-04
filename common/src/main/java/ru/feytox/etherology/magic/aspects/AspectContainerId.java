package ru.feytox.etherology.magic.aspects;

import com.mojang.serialization.Codec;
import net.minecraft.util.Identifier;

import java.util.Objects;

/**
 * Identifies one aspect-bearing registry value together with its container kind.
 */
public final class AspectContainerId {

    public static final Codec<AspectContainerId> CODEC = Codec.STRING
            .xmap(AspectContainerId::of, AspectContainerId::toString)
            .stable();

    private final Identifier id;
    private final AspectContainerType containerType;

    private AspectContainerId(Identifier id, AspectContainerType containerType) {
        this.id = Objects.requireNonNull(id, "id");
        this.containerType = Objects.requireNonNull(containerType, "containerType");
    }

    /**
     * Parses either an ordinary item identifier or a type-prefixed identifier.
     *
     * @param serialized datapack key such as {@code minecraft:stone} or
     *                   {@code entity:minecraft:zombie}
     * @return parsed typed identifier
     */
    public static AspectContainerId of(String serialized) {
        int separator = serialized.indexOf(':');
        if (separator >= 0) {
            AspectContainerType type = AspectContainerType.getByPrefix(
                    serialized.substring(0, separator),
                    AspectContainerType.ITEM
            );
            if (type.getPrefix() != null) {
                String typedIdentifier = serialized.substring(separator + 1);
                if (typedIdentifier.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Typed aspect container ID is missing its identifier: "
                                    + serialized
                    );
                }
                return new AspectContainerId(
                        new Identifier(typedIdentifier),
                        type
                );
            }
        } else if (AspectContainerType.getByPrefix(
                serialized,
                AspectContainerType.ITEM
        ).getPrefix() != null) {
            throw new IllegalArgumentException(
                    "Typed aspect container ID is missing its identifier: " + serialized
            );
        }

        return new AspectContainerId(
                new Identifier(serialized),
                AspectContainerType.ITEM
        );
    }

    /**
     * Creates a typed aspect-container identifier.
     *
     * @param id underlying registry identifier
     * @param containerType kind of object represented by the identifier
     * @return typed identifier
     */
    public static AspectContainerId of(
            Identifier id,
            AspectContainerType containerType
    ) {
        return new AspectContainerId(id, containerType);
    }

    public Identifier getId() {
        return id;
    }

    public AspectContainerType getContainerType() {
        return containerType;
    }

    @Override
    public String toString() {
        String prefix = containerType.getPrefix();
        return (prefix == null ? "" : prefix + ":") + id;
    }

    /**
     * Produces a conventional single {@link Identifier} for UI entry identity.
     *
     * @return identifier whose path begins with the resolved container type
     */
    public Identifier toTypedId() {
        String prefix = containerType.getPrefix();
        return new Identifier(
                id.getNamespace(),
                (prefix == null ? "item" : prefix) + "_" + id.getPath()
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AspectContainerId that)) return false;
        return id.equals(that.id) && containerType == that.containerType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, containerType);
    }
}
