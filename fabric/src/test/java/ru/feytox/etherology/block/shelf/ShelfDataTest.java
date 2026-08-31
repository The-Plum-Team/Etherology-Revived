package ru.feytox.etherology.block.shelf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ShelfDataTest {

    @Test
    void capsMergeAtBothAvailableItemsAndShelfCapacity() {
        assertEquals(1, ShelfData.getTransferCount(60, 64, 1));
        assertEquals(4, ShelfData.getTransferCount(60, 64, 8));
        assertEquals(0, ShelfData.getTransferCount(64, 64, 8));
        assertEquals(0, ShelfData.getTransferCount(65, 64, 8));
        assertEquals(0, ShelfData.getTransferCount(60, 64, -1));
    }
}
