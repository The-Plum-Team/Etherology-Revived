package ru.feytox.etherology.magic.staff;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StaffLensPatternContractTest {

    @Test
    void emptyPatternAndMemoizedListsKeepCanonicalIdentity() {
        assertEquals("empty", StaffPattern.EMPTY.getName());
        assertTrue(StaffPattern.EMPTY.isEmpty());
        assertFalse(StaffLenses.REDSTONE.isEmpty());

        Supplier<List<? extends StaffPattern>> empty = StaffPattern.memoize();
        assertSame(empty.get(), empty.get());
        assertTrue(empty.get().isEmpty());

        Supplier<List<? extends StaffPattern>> values = StaffPattern.memoize(
                StaffPattern.EMPTY,
                StaffLenses.REDSTONE
        );
        assertSame(values.get(), values.get());
        assertEquals(List.of(StaffPattern.EMPTY, StaffLenses.REDSTONE), values.get());
    }

    @Test
    void lensEnumKeepsItsExactNameAndMemoizedInventory() {
        assertEquals(List.of(StaffLenses.REDSTONE), StaffLenses.LENSES.get());
        assertSame(StaffLenses.LENSES.get(), StaffLenses.LENSES.get());
        assertEquals("redstone", StaffLenses.REDSTONE.getName());
    }
}
