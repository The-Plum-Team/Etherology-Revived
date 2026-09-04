package ru.feytox.etherology.util.inventory;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

/**
 * Supplies vanilla inventory operations for an inventory backed by one stable list.
 */
public interface ListBackedInventory extends Inventory {

    /**
     * Returns the stable list that stores this inventory's stacks.
     *
     * @return live inventory storage
     */
    DefaultedList<ItemStack> getItems();

    /** {@inheritDoc} */
    @Override
    default int size() {
        return getItems().size();
    }

    /** {@inheritDoc} */
    @Override
    default boolean isEmpty() {
        for (int slot = 0; slot < size(); slot++) {
            if (!getStack(slot).isEmpty()) return false;
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    default ItemStack getStack(int slot) {
        return getItems().get(slot);
    }

    /** {@inheritDoc} */
    @Override
    default ItemStack removeStack(int slot, int count) {
        ItemStack removedStack = Inventories.splitStack(getItems(), slot, count);
        if (!removedStack.isEmpty()) markDirty();
        return removedStack;
    }

    /** {@inheritDoc} */
    @Override
    default ItemStack removeStack(int slot) {
        return Inventories.removeStack(getItems(), slot);
    }

    /** {@inheritDoc} */
    @Override
    default void setStack(int slot, ItemStack stack) {
        getItems().set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
    }

    /** {@inheritDoc} */
    @Override
    default void markDirty() {
    }

    /** {@inheritDoc} */
    @Override
    default boolean canPlayerUse(PlayerEntity player) {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    default void clear() {
        getItems().clear();
    }
}
