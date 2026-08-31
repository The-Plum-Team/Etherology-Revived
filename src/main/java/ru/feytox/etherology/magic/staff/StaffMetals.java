package ru.feytox.etherology.magic.staff;

import lombok.RequiredArgsConstructor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import org.apache.commons.lang3.EnumUtils;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.registry.item.SharedMaterialItems;

import java.util.List;
import java.util.function.Supplier;

@RequiredArgsConstructor
public enum StaffMetals implements StaffPattern {
    AZEL(() -> SharedMaterialItems.AZEL_INGOT.get()),
    COPPER(() -> Items.COPPER_INGOT),
    ETHRIL(() -> SharedMaterialItems.ETHRIL_INGOT.get()),
    GOLD(() -> Items.GOLD_INGOT),
    IRON(() -> Items.IRON_INGOT),
    NETHERITE(() -> Items.NETHERITE_INGOT),
    EBONY(() -> SharedMaterialItems.EBONY_INGOT.get());

    public static final Supplier<List<? extends StaffPattern>> METALS = StaffPattern.memoize(values());
    private final Supplier<? extends Item> metalItemSupplier;
    /** Returns the registered ingredient item represented by this staff-metal pattern. */
    public Item getMetalItem() {
        return metalItemSupplier.get();
    }

    @Override
    public String getName() {
        return name().toLowerCase();
    }

    @Nullable
    public static StaffMetals getFromStack(ItemStack metalStack) {
        if (metalStack.isEmpty()) return null;

        Item metalItem = metalStack.getItem();
        String itemId = Registries.ITEM.getId(metalItem).getPath();
        String metalId = itemId.split("_")[0];
        StaffMetals result = EnumUtils.getEnumIgnoreCase(StaffMetals.class, metalId, null);
        if (result != null) return result;

        // TODO: 29.10.2023 проверить необходимость ИЛИ просто указать требование для названий StaffMetals
        for (StaffMetals staffMetal : values()) {
            if (metalItem.equals(staffMetal.getMetalItem())) return staffMetal;
        }

        return null;
    }
}
