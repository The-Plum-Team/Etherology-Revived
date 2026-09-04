package ru.feytox.etherology.block.pedestal;

import net.minecraft.block.BlockState;
import net.minecraft.util.StringIdentifiable;
import ru.feytox.etherology.registry.block.SharedPedestalBlocks;

import java.util.Locale;

/**
 * Describes one vertical segment of a connected Pedestal column.
 */
public enum PedestalShape implements StringIdentifiable {
    BOTTOM(false),
    MIDDLE(false),
    TOP(true),
    FULL(true);

    private final boolean hasItem;

    PedestalShape(boolean hasItem) {
        this.hasItem = hasItem;
    }

    /**
     * Reports whether this segment owns the display inventory and block entity.
     *
     * @return {@code true} for standalone and top segments
     */
    public boolean isHasItem() {
        return hasItem;
    }

    /**
     * Derives the segment shape from the blocks immediately below and above it.
     *
     * @param underState state directly below the current Pedestal
     * @param topState state directly above the current Pedestal
     * @return connected-column shape
     */
    public static PedestalShape getShape(
            BlockState underState,
            BlockState topState
    ) {
        boolean isUnder = underState.isOf(SharedPedestalBlocks.PEDESTAL.get());
        boolean isTop = topState.isOf(SharedPedestalBlocks.PEDESTAL.get());
        if (isUnder) return isTop ? MIDDLE : TOP;
        return isTop ? BOTTOM : FULL;
    }

    /** {@inheritDoc} */
    @Override
    public String asString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
