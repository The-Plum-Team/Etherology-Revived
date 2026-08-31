package ru.feytox.etherology.block.etherealStorage;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import ru.feytox.etherology.item.EtherealStorageInputItem;
import ru.feytox.etherology.registry.block.SharedBlockEntities;
import ru.feytox.etherology.registry.item.SharedItems;

import java.util.List;

/**
 * Persists the bounded Ether buffer and four-slot server-owned storage-menu core.
 */
public final class EtherealStorageFoundationBlockEntity extends BlockEntity
        implements SidedInventory, NamedScreenHandlerFactory {

    private static final String STORAGE_ETHER_KEY = "storage_ether";
    private static final int INPUT_SLOT_COUNT = 3;
    private static final int DISPLAY_SLOT = 3;
    private static final int INVENTORY_SIZE = 4;
    private static final float MAX_ETHER = 64.0f;

    private final DefaultedList<ItemStack> inventory =
            DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);

    private float storageEther;
    private int viewers;
    private boolean open;

    /**
     * Associates a placed storage instance with its shared block-entity type.
     *
     * @param pos block position supplied by the world
     * @param state placed block state
     */
    public EtherealStorageFoundationBlockEntity(BlockPos pos, BlockState state) {
        super(SharedBlockEntities.ETHEREAL_STORAGE.get(), pos, state);
    }

    /**
     * Returns the persistent internal Ether buffer, excluding deferred per-glint state.
     *
     * @return stored Ether units
     */
    public float getStoredEther() {
        return storageEther;
    }

    /**
     * Replaces the internal Ether buffer and refreshes the server-maintained display slot.
     *
     * @param value Ether units clamped to this bounded core's capacity
     */
    public void setStoredEther(float value) {
        float clampedValue = normalizeEther(value);
        if (Float.compare(storageEther, clampedValue) == 0) {
            return;
        }

        storageEther = clampedValue;
        updateDisplayStack();
        markDirty();
    }

    int getInputSlotCount() {
        return INPUT_SLOT_COUNT;
    }

    ItemStack removeInputStackForDrop(int slot) {
        ItemStack removedStack = Inventories.removeStack(inventory, slot);
        if (!removedStack.isEmpty()) {
            markDirty();
        }
        return removedStack;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        writePersistentState(nbt, inventory, storageEther);
    }

    /**
     * Restores exactly four inventory slots and the persistent internal Ether buffer.
     *
     * @param nbt serialized block-entity state
     */
    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        storageEther = readPersistentState(nbt, inventory, SharedItems.ETHER.get());
    }

    /**
     * Counts viewers and plays one server-side opening sound for the first viewer.
     *
     * @param player player opening the synchronized menu
     */
    @Override
    public void onOpen(PlayerEntity player) {
        viewers = incrementViewerCount(viewers);
        if (world == null || world.isClient || open) {
            return;
        }

        world.playSound(
                null,
                pos,
                SoundEvents.BLOCK_CHEST_OPEN,
                SoundCategory.BLOCKS,
                0.5f,
                0.9f
        );
        open = true;
    }

    /**
     * Plays one server-side closing sound after the final synchronized viewer leaves.
     *
     * @param player player closing the synchronized menu
     */
    @Override
    public void onClose(PlayerEntity player) {
        viewers = decrementViewerCount(viewers);
        if (world == null || world.isClient || !open || viewers > 0) {
            return;
        }

        world.playSound(
                null,
                pos,
                SoundEvents.BLOCK_CHEST_CLOSE,
                SoundCategory.BLOCKS,
                0.5f,
                0.9f
        );
        open = false;
    }

    /**
     * Returns the stable four-slot inventory size used by persistence and menu synchronization.
     *
     * @return four slots
     */
    @Override
    public int size() {
        return inventory.size();
    }

    /**
     * Reports whether every input and display slot is empty.
     *
     * @return whether no slot contains an item
     */
    @Override
    public boolean isEmpty() {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the live stack stored at the requested inventory slot.
     *
     * @param slot inventory slot
     * @return current stack
     */
    @Override
    public ItemStack getStack(int slot) {
        return slot >= 0 && slot < INVENTORY_SIZE ? inventory.get(slot) : ItemStack.EMPTY;
    }

    /**
     * Splits an input stack and dirties persistent state when an item was removed.
     *
     * @param slot inventory slot
     * @param amount maximum number of items to remove
     * @return removed stack, or empty for the display slot
     */
    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot < 0 || slot >= INPUT_SLOT_COUNT) {
            return ItemStack.EMPTY;
        }

        ItemStack removedStack = Inventories.splitStack(inventory, slot, amount);
        if (!removedStack.isEmpty()) {
            markDirty();
        }
        return removedStack;
    }

    /**
     * Removes a complete input stack and dirties persistent state.
     *
     * @param slot inventory slot
     * @return removed stack, or empty for the display slot
     */
    @Override
    public ItemStack removeStack(int slot) {
        if (slot < 0 || slot >= INPUT_SLOT_COUNT) {
            return ItemStack.EMPTY;
        }

        ItemStack removedStack = Inventories.removeStack(inventory, slot);
        if (!removedStack.isEmpty()) {
            markDirty();
        }
        return removedStack;
    }

    /**
     * Stores only valid bounded input items and preserves server ownership of the display slot.
     *
     * @param slot inventory slot
     * @param stack replacement stack
     */
    @Override
    public void setStack(int slot, ItemStack stack) {
        if (!isValid(slot, stack)) {
            return;
        }

        inventory.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
        markDirty();
    }

    /**
     * Keeps the menu usable only while the player remains near this exact block entity.
     *
     * @param player player whose access is being checked
     * @return whether this block entity remains in range and loaded
     */
    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(this, player);
    }

    /**
     * Accepts the bounded shared glint only in one of the three input slots.
     *
     * @param slot inventory slot
     * @param stack proposed stack
     * @return whether this stack may occupy the slot
     */
    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return slot >= 0
                && slot < INPUT_SLOT_COUNT
                && (stack.isEmpty() || stack.getItem() instanceof EtherealStorageInputItem);
    }

    /**
     * Clears the three real inputs while retaining Ether and rebuilding its derived display.
     */
    @Override
    public void clear() {
        replaceInputsAndDisplay(
                inventory,
                ItemStack.EMPTY,
                createDisplayStack(SharedItems.ETHER.get(), storageEther)
        );
        markDirty();
    }

    /**
     * Exposes only the three real input slots to vanilla sided automation.
     *
     * @param side queried face
     * @return input slot indices
     */
    @Override
    public int[] getAvailableSlots(Direction side) {
        return new int[]{0, 1, 2};
    }

    /**
     * Allows vanilla automation to insert only the bounded shared glint into an input slot.
     *
     * @param slot queried slot
     * @param stack proposed stack
     * @param direction insertion face
     * @return whether insertion is allowed
     */
    @Override
    public boolean canInsert(int slot, ItemStack stack, Direction direction) {
        return isValid(slot, stack);
    }

    /**
     * Prevents vanilla automation from extracting inputs or the derived display item.
     *
     * @param slot queried slot
     * @param stack current stack
     * @param direction extraction face
     * @return always false
     */
    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction direction) {
        return false;
    }

    /**
     * Supplies the localized title used by the synchronized storage screen.
     *
     * @return translated storage title
     */
    @Override
    public Text getDisplayName() {
        return Text.translatable("block.etherology.ethereal_storage.title");
    }

    /**
     * Creates the server menu over this persistent inventory and refreshes its derived display.
     *
     * @param syncId synchronized menu instance identifier
     * @param playerInventory interacting player's inventory
     * @param player interacting player
     * @return server-owned storage menu
     */
    @Override
    public ScreenHandler createMenu(
            int syncId,
            PlayerInventory playerInventory,
            PlayerEntity player
    ) {
        updateDisplayStack();
        markDirty();
        return new EtherealStorageFoundationScreenHandler(syncId, playerInventory, this);
    }

    private void updateDisplayStack() {
        inventory.set(
                DISPLAY_SLOT,
                createDisplayStack(SharedItems.ETHER.get(), storageEther)
        );
    }

    static void writePersistentState(
            NbtCompound nbt,
            DefaultedList<ItemStack> inventory,
            float storageEther
    ) {
        Inventories.writeNbt(nbt, inventory);
        nbt.putFloat(STORAGE_ETHER_KEY, storageEther);
    }

    static float readPersistentState(
            NbtCompound nbt,
            DefaultedList<ItemStack> inventory,
            Item etherItem
    ) {
        inventory.clear();
        Inventories.readNbt(nbt, inventory);
        float storedEther = normalizeEther(nbt.getFloat(STORAGE_ETHER_KEY));
        inventory.set(DISPLAY_SLOT, createDisplayStack(etherItem, storedEther));
        return storedEther;
    }

    static ItemStack createDisplayStack(Item etherItem, float storageEther) {
        int etherCount = displayCount(storageEther);
        if (etherCount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack displayStack = etherItem.getDefaultStack();
        displayStack.setCount(etherCount);
        return displayStack;
    }

    static <T> void replaceInputsAndDisplay(
            List<T> inventory,
            T emptySlot,
            T derivedDisplay
    ) {
        for (int slot = 0; slot < DISPLAY_SLOT; slot++) {
            inventory.set(slot, emptySlot);
        }
        inventory.set(DISPLAY_SLOT, derivedDisplay);
    }

    static float normalizeEther(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return MathHelper.clamp(value, 0.0f, MAX_ETHER);
    }

    static int displayCount(float value) {
        return Math.min(64, MathHelper.floor(normalizeEther(value)));
    }

    static int incrementViewerCount(int viewerCount) {
        return viewerCount + 1;
    }

    static int decrementViewerCount(int viewerCount) {
        return Math.max(0, viewerCount - 1);
    }
}
