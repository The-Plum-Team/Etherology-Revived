package ru.feytox.etherology.enums;

import net.minecraft.util.StringIdentifiable;

import java.util.Locale;

/**
 * Serializes one channel face as disconnected, accepting input, or supplying output.
 */
public enum PipeSide implements StringIdentifiable {
    /** No visible or mechanical connection. */
    EMPTY,

    /** Ether enters the channel through this outward face. */
    IN,

    /** Ether leaves the channel through this outward face. */
    OUT;

    /**
     * Reports whether this face accepts Ether.
     *
     * @return whether this value is {@link #IN}
     */
    public boolean isInput() {
        return this.equals(IN);
    }

    /**
     * Reports whether this face supplies Ether.
     *
     * @return whether this value is {@link #OUT}
     */
    public boolean isOutput() {
        return this.equals(OUT);
    }

    /**
     * Reports whether this face has no connection.
     *
     * @return whether this value is {@link #EMPTY}
     */
    public boolean isEmpty() {
        return this.equals(EMPTY);
    }

    /**
     * Returns the locale-independent blockstate value.
     *
     * @return {@code empty}, {@code in}, or {@code out}
     */
    @Override
    public String asString() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
