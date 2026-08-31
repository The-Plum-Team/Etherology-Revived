package ru.feytox.etherology.block.empowerTable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EmpowerTableBlockEntityTest {

    @Test
    void bulkCraftingAllowsAnExactStackFit() {
        assertTrue(EmpowerTableBlockEntity.canFitOutput(62, 2, 64));
        assertTrue(EmpowerTableBlockEntity.canFitOutput(0, 64, 64));
        assertFalse(EmpowerTableBlockEntity.canFitOutput(63, 2, 64));
    }
}
