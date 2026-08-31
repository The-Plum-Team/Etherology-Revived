package ru.feytox.etherology.util.misc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShockwaveUtilTest {

    @Test
    void rejectsLengthsThatCannotProduceFiniteKnockbackDirections() {
        assertFalse(ShockwaveUtil.canNormalize(0.0d));
        assertFalse(ShockwaveUtil.canNormalize(-1.0d));
        assertFalse(ShockwaveUtil.canNormalize(Double.NaN));
        assertFalse(ShockwaveUtil.canNormalize(Double.POSITIVE_INFINITY));
        assertTrue(ShockwaveUtil.canNormalize(1.0d));
    }
}
