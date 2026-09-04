package ru.feytox.etherology.magic.aspects;

import org.jetbrains.annotations.Nullable;

/**
 * Distinguishes datapack keys whose underlying identifier belongs to different registries.
 */
public enum AspectContainerType {
    ITEM(null),
    ENTITY("entity"),
    POTION("potion"),
    SPLASH_POTION("splash_potion"),
    LINGERING_POTION("lingering_potion"),
    TIPPED_ARROW("tipped_arrow");

    @Nullable
    private final String prefix;

    AspectContainerType(@Nullable String prefix) {
        this.prefix = prefix;
    }

    @Nullable
    public String getPrefix() {
        return prefix;
    }

    /**
     * Resolves a serialized type prefix without imposing locale-sensitive case rules.
     *
     * @param prefix prefix preceding the underlying identifier
     * @param defaultValue value returned when the prefix is not a container type
     * @return matching type or {@code defaultValue}
     */
    public static AspectContainerType getByPrefix(
            @Nullable String prefix,
            AspectContainerType defaultValue
    ) {
        if (prefix == null) return defaultValue;
        for (AspectContainerType type : values()) {
            if (type.name().equalsIgnoreCase(prefix)) return type;
        }
        return defaultValue;
    }
}
