package ru.feytox.etherology.util.misc;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Provides a deterministic cached animation offset derived from a block position.
 */
public interface UniqueProvider {

    /**
     * Replaces the cached offset.
     *
     * @param value cached radians, or {@code null} to recalculate lazily
     */
    void setCachedUniqueOffset(@Nullable Float value);

    /**
     * Returns the cached offset when it has already been calculated.
     *
     * @return cached radians, or {@code null}
     */
    @Nullable
    Float getCachedUniqueOffset();

    /**
     * Returns a stable animation offset for the supplied position.
     *
     * @param pos block position used as the deterministic seed
     * @return cached offset in radians
     */
    default float getUniqueOffset(BlockPos pos) {
        Float cachedOffset = getCachedUniqueOffset();
        if (cachedOffset != null) return cachedOffset;

        float sum = 0.0f;
        sum += pos.getX() % 32;
        sum += pos.getY() % 64;
        sum += pos.getZ() % 128;
        float unique = MathHelper.abs(sum) / 10.0f;
        float result = 2 * MathHelper.PI * unique;
        setCachedUniqueOffset(result);
        return result;
    }
}
