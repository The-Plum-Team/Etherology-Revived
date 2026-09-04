package ru.feytox.etherology.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.magic.staff.StaffLenses;

import java.util.Objects;

/**
 * Holds the loader backend used by the shared lens implementation.
 */
public final class LensRuntime {

    private static final LensRuntimeBackend UNAVAILABLE = new UnavailableBackend();
    private static volatile LensRuntimeBackend backend = UNAVAILABLE;

    private LensRuntime() {
    }

    /**
     * Binds one loader backend, allowing an idempotent repeat with the same instance only.
     */
    public static synchronized void bind(LensRuntimeBackend backend) {
        Objects.requireNonNull(backend, "backend");
        if (LensRuntime.backend != UNAVAILABLE && LensRuntime.backend != backend) {
            throw new IllegalStateException("A lens runtime backend is already bound");
        }
        LensRuntime.backend = backend;
    }

    static boolean decrementEther(LivingEntity entity, float etherCost) {
        return backend.decrementEther(entity, etherCost);
    }

    static boolean isStaff(ItemStack stack) {
        return backend.isStaff(stack);
    }

    static void placeStaffLens(ItemStack staffStack, ItemStack lensStack, StaffLenses lensType) {
        backend.placeStaffLens(staffStack, lensStack, lensType);
    }

    @Nullable
    static ItemStack takeStaffLens(ItemStack staffStack) {
        return backend.takeStaffLens(staffStack);
    }

    @Nullable
    static ItemStack getStaffLens(ItemStack staffStack) {
        return backend.getStaffLens(staffStack);
    }

    private static final class UnavailableBackend implements LensRuntimeBackend {

        @Override
        public boolean decrementEther(LivingEntity entity, float etherCost) {
            return false;
        }

        @Override
        public boolean isStaff(ItemStack stack) {
            return false;
        }

        @Override
        public void placeStaffLens(
                ItemStack staffStack,
                ItemStack lensStack,
                StaffLenses lensType
        ) {
        }

        @Nullable
        @Override
        public ItemStack takeStaffLens(ItemStack staffStack) {
            return null;
        }

        @Nullable
        @Override
        public ItemStack getStaffLens(ItemStack staffStack) {
            return null;
        }
    }
}
