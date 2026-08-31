package ru.feytox.etherology.block.etherealStorage;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import ru.feytox.etherology.item.EtherealStorageInputItem;

final class EtherealStorageInputSlot extends Slot {

    EtherealStorageInputSlot(Inventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Override
    public boolean canInsert(ItemStack stack) {
        return stack.getItem() instanceof EtherealStorageInputItem;
    }
}
