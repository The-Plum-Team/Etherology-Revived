package ru.feytox.etherology.util.misc;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import ru.feytox.etherology.util.inventory.ListBackedInventory;

public interface UpdatableInventory extends ListBackedInventory {
    void onTrackedSlotTake(PlayerEntity player, ItemStack stack, int index);
    void onTrackedUpdate(int index);
    void onSpecialEvent(int eventId, ItemStack stack);
}
