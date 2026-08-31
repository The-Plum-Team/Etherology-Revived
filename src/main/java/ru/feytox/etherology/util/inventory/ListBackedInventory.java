package ru.feytox.etherology.util.inventory;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

/**
 * Supplies the vanilla {@link Inventory} operations for an inventory backed by a
 * stable {@link DefaultedList} instance.
 */
public interface ListBackedInventory extends Inventory {

    /**
     * Returns the stable list that stores this inventory's stacks.
     */
    DefaultedList<ItemStack> getItems();

    /**
     * Derives the slot count from the backing list.
     */
    @Override
    default int size() {
        return getItems().size();
    }

    /**
     * Reports whether every slot contains an empty stack.
     */
    @Override
    default boolean isEmpty() {
        for (int slot = 0; slot < size(); slot++) {
            if (!getStack(slot).isEmpty()) return false;
        }
        return true;
    }

    /**
     * Returns the live stack stored in the requested slot.
     */
    @Override
    default ItemStack getStack(int slot) {
        return getItems().get(slot);
    }

    /**
     * Splits a stack and marks the inventory dirty only when an item was removed.
     */
    @Override
    default ItemStack removeStack(int slot, int count) {
        ItemStack removedStack = Inventories.splitStack(getItems(), slot, count);
        if (!removedStack.isEmpty()) markDirty();
        return removedStack;
    }

    /**
     * Removes the complete stack without invoking the dirty hook.
     */
    @Override
    default ItemStack removeStack(int slot) {
        return Inventories.removeStack(getItems(), slot);
    }

    /**
     * Stores the supplied stack and truncates it to this inventory's stack limit.
     */
    @Override
    default void setStack(int slot, ItemStack stack) {
        getItems().set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
    }

    /**
     * Provides an empty dirty hook for implementations that do not persist state.
     */
    @Override
    default void markDirty() {
    }

    /**
     * Allows access by default so location-sensitive inventories can opt in to checks.
     */
    @Override
    default boolean canPlayerUse(PlayerEntity player) {
        return true;
    }

    /**
     * Resets every slot through the backing list's default-value semantics.
     */
    @Override
    default void clear() {
        getItems().clear();
    }
}
