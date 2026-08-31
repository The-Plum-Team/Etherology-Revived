package ru.feytox.etherology.block.etherealStorage;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import ru.feytox.etherology.item.EtherealStorageInputItem;
import ru.feytox.etherology.registry.misc.SharedScreenHandlers;

/**
 * Owns server-authoritative storage slots and the matching player-inventory transfer rules.
 */
public final class EtherealStorageFoundationScreenHandler extends ScreenHandler {

    private static final int STORAGE_SLOT_COUNT = 4;
    private static final int INPUT_SLOT_COUNT = 3;

    private final Inventory inventory;

    /**
     * Creates the client-side mirror that receives authoritative slot updates from the server.
     *
     * @param syncId synchronized menu instance identifier
     * @param playerInventory inventory rendered below the storage slots
     */
    public EtherealStorageFoundationScreenHandler(
            int syncId,
            PlayerInventory playerInventory
    ) {
        this(syncId, playerInventory, new SimpleInventory(STORAGE_SLOT_COUNT));
    }

    /**
     * Creates the server menu over the block entity's persistent inventory.
     *
     * @param syncId synchronized menu instance identifier
     * @param playerInventory inventory rendered below the storage slots
     * @param inventory persistent four-slot storage inventory
     */
    public EtherealStorageFoundationScreenHandler(
            int syncId,
            PlayerInventory playerInventory,
            Inventory inventory
    ) {
        super(SharedScreenHandlers.ETHEREAL_STORAGE.get(), syncId);
        checkSize(inventory, STORAGE_SLOT_COUNT);
        this.inventory = inventory;
        inventory.onOpen(playerInventory.player);

        for (int slot = 0; slot < INPUT_SLOT_COUNT; slot++) {
            addSlot(new EtherealStorageInputSlot(inventory, slot, 79 + slot * 19, 20));
        }
        addSlot(new EtherealStorageDisplaySlot(inventory, 3, 35, 20));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        playerInventory,
                        column + row * 9 + 9,
                        8 + column * 18,
                        54 + row * 18
                ));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 112));
        }
    }

    /**
     * Moves only bounded storage-input items between the storage and player ranges.
     *
     * @param player player requesting the transfer
     * @param slotIndex synchronized menu slot index
     * @return the pre-transfer stack copy, or empty when no valid move exists
     */
    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot = slots.get(slotIndex);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = slot.getStack();
        if (!(sourceStack.getItem() instanceof EtherealStorageInputItem)) {
            return ItemStack.EMPTY;
        }

        ItemStack originalStack = sourceStack.copy();
        if (slotIndex < STORAGE_SLOT_COUNT) {
            if (!insertItem(sourceStack, STORAGE_SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!insertItem(sourceStack, 0, INPUT_SLOT_COUNT, false)) {
            return ItemStack.EMPTY;
        }

        if (sourceStack.isEmpty()) {
            slot.setStackNoCallbacks(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        return originalStack;
    }

    /**
     * Releases the block entity's viewer lifecycle when the synchronized menu closes.
     *
     * @param player player closing this menu
     */
    @Override
    public void onClosed(PlayerEntity player) {
        inventory.onClose(player);
        super.onClosed(player);
    }

    /**
     * Delegates distance and ownership validation to the server inventory.
     *
     * @param player player whose continued access is being checked
     * @return whether the server inventory is still usable
     */
    @Override
    public boolean canUse(PlayerEntity player) {
        return inventory.canPlayerUse(player);
    }
}
