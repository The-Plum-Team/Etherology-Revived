package ru.feytox.etherology.magic.staff;

import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.item.LensItem;

import java.util.List;
import java.util.function.Supplier;

public enum StaffLenses implements StaffPattern {
    REDSTONE;

    public static final Supplier<List<? extends StaffPattern>> LENSES = StaffPattern.memoize(values());

    @Override
    public String getName() {
        return name().toLowerCase();
    }

    @Nullable
    public static StaffLenses getLens(ItemStack lensStack) {
        if (!(lensStack.getItem() instanceof LensItem lensItem)) return null;
        return lensItem.getLensType();
    }
}
