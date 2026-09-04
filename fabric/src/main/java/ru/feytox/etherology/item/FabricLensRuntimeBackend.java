package ru.feytox.etherology.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.magic.ether.EtherComponent;
import ru.feytox.etherology.magic.staff.StaffComponent;
import ru.feytox.etherology.magic.staff.StaffLenses;
import ru.feytox.etherology.magic.staff.StaffPart;
import ru.feytox.etherology.magic.staff.StaffPartInfo;
import ru.feytox.etherology.magic.staff.StaffPattern;
import ru.feytox.etherology.registry.misc.ComponentTypes;
import ru.feytox.etherology.util.misc.ItemComponent;

/**
 * Delegates shared lens operations to the canonical Fabric Ether and staff graph.
 */
public final class FabricLensRuntimeBackend implements LensRuntimeBackend {

    public static final FabricLensRuntimeBackend INSTANCE = new FabricLensRuntimeBackend();

    private FabricLensRuntimeBackend() {
    }

    @Override
    public boolean decrementEther(LivingEntity entity, float etherCost) {
        return EtherComponent.decrement(entity, etherCost);
    }

    @Override
    public boolean isStaff(ItemStack stack) {
        return stack.getItem() instanceof StaffItem;
    }

    @Override
    public void placeStaffLens(
            ItemStack staffStack,
            ItemStack lensStack,
            StaffLenses lensType
    ) {
        StaffItem.setLensComponent(staffStack, lensStack);
        StaffComponent.getWrapper(staffStack)
                .ifPresent(staff -> staff.set(
                        new StaffPartInfo(StaffPart.LENS, lensType, StaffPattern.EMPTY),
                        StaffComponent::setPartInfo
                ).save());
    }

    @Nullable
    @Override
    public ItemStack takeStaffLens(ItemStack staffStack) {
        ItemStack lensStack = getStaffLens(staffStack);
        StaffComponent.getWrapper(staffStack)
                .ifPresent(staff -> staff.set(
                        StaffPart.LENS,
                        StaffComponent::removePartInfo
                ).save());
        ComponentTypes.STAFF_LENS.remove(staffStack);
        return lensStack;
    }

    @Nullable
    @Override
    public ItemStack getStaffLens(ItemStack staffStack) {
        return ComponentTypes.STAFF_LENS.get(staffStack)
                .map(ItemComponent::stack)
                .filter(stack -> !stack.isEmpty())
                .orElse(null);
    }
}
