package dev.theplumteam.etherology.e2e.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StableRenderCounterTest {

    @Test
    void requiresOneUninterruptedExactRenderStreak() {
        StableRenderCounter counter = new StableRenderCounter(3);

        assertFalse(counter.observe(true));
        assertFalse(counter.observe(true));
        assertFalse(counter.observe(false));
        assertEquals(0, counter.completedRenders());
        assertFalse(counter.observe(true));
        assertFalse(counter.observe(true));
        assertTrue(counter.observe(true));
        assertEquals(3, counter.completedRenders());
    }
}
