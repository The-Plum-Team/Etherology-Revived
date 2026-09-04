package ru.feytox.etherology.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.Test;
import ru.feytox.etherology.magic.staff.StaffLenses;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LensRuntimeContractTest {

    @Test
    void defaultBindingIsFailClosedAndOneBackendOwnsEveryDelegation() {
        assertFalse(LensRuntime.decrementEther(null, 1.25f));
        assertFalse(LensRuntime.isStaff(null));
        assertDoesNotThrow(() -> LensRuntime.placeStaffLens(
                null,
                null,
                StaffLenses.REDSTONE
        ));
        assertNull(LensRuntime.takeStaffLens(null));
        assertNull(LensRuntime.getStaffLens(null));
        assertThrows(NullPointerException.class, () -> LensRuntime.bind(null));

        RecordingBackend backend = new RecordingBackend();
        LensRuntime.bind(backend);
        assertDoesNotThrow(() -> LensRuntime.bind(backend));

        assertTrue(LensRuntime.decrementEther(null, 3.5f));
        assertTrue(LensRuntime.isStaff(null));
        LensRuntime.placeStaffLens(null, null, StaffLenses.REDSTONE);
        assertNull(LensRuntime.takeStaffLens(null));
        assertNull(LensRuntime.getStaffLens(null));

        assertEquals(1, backend.decrementCalls);
        assertEquals(3.5f, backend.lastEtherCost);
        assertEquals(1, backend.staffChecks);
        assertEquals(1, backend.placeCalls);
        assertSame(StaffLenses.REDSTONE, backend.lastLensType);
        assertEquals(1, backend.takeCalls);
        assertEquals(1, backend.getCalls);

        assertThrows(
                IllegalStateException.class,
                () -> LensRuntime.bind(new RecordingBackend())
        );
        assertTrue(LensRuntime.decrementEther(null, 7.0f));
        assertEquals(2, backend.decrementCalls);
        assertEquals(7.0f, backend.lastEtherCost);
    }

    private static final class RecordingBackend implements LensRuntimeBackend {

        private int decrementCalls;
        private float lastEtherCost;
        private int staffChecks;
        private int placeCalls;
        private StaffLenses lastLensType;
        private int takeCalls;
        private int getCalls;

        @Override
        public boolean decrementEther(LivingEntity entity, float etherCost) {
            decrementCalls++;
            lastEtherCost = etherCost;
            return true;
        }

        @Override
        public boolean isStaff(ItemStack stack) {
            staffChecks++;
            return true;
        }

        @Override
        public void placeStaffLens(
                ItemStack staffStack,
                ItemStack lensStack,
                StaffLenses lensType
        ) {
            placeCalls++;
            lastLensType = lensType;
        }

        @Override
        public ItemStack takeStaffLens(ItemStack staffStack) {
            takeCalls++;
            return null;
        }

        @Override
        public ItemStack getStaffLens(ItemStack staffStack) {
            getCalls++;
            return null;
        }
    }
}
