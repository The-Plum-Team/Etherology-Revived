package ru.feytox.etherology.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.magic.staff.StaffLenses;

/**
 * Supplies only the loader-owned Ether and staff operations required by canonical lenses.
 */
public interface LensRuntimeBackend {

    /**
     * Attempts to consume the requested Ether amount from an entity.
     */
    boolean decrementEther(LivingEntity entity, float etherCost);

    /**
     * Returns whether the stack is backed by the loader's canonical staff item.
     */
    boolean isStaff(ItemStack stack);

    /**
     * Stores the copied lens and updates the staff's lens-part metadata.
     */
    void placeStaffLens(ItemStack staffStack, ItemStack lensStack, StaffLenses lensType);

    /**
     * Returns the stored lens while removing both its storage and lens-part metadata.
     */
    @Nullable
    ItemStack takeStaffLens(ItemStack staffStack);

    /**
     * Returns the non-empty stored lens, or {@code null} when none is stored.
     */
    @Nullable
    ItemStack getStaffLens(ItemStack staffStack);
}
