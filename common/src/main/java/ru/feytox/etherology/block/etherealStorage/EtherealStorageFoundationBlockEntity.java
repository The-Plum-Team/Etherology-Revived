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
import net.minecraft.world.World;
import ru.feytox.etherology.item.EtherealStorageInputItem;
import ru.feytox.etherology.item.glints.GlintEtherData;
import ru.feytox.etherology.registry.block.SharedBlockEntities;
import ru.feytox.etherology.registry.item.SharedItems;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * Persists internal and per-glint Ether while owning the four-slot server storage menu.
 */
public final class EtherealStorageFoundationBlockEntity extends BlockEntity
        implements SidedInventory, NamedScreenHandlerFactory, GeoBlockEntity {

    private static final String STORAGE_ETHER_KEY = "storage_ether";
    private static final String STORAGE_CONTROLLER = "storage_controller";
    private static final String OPEN_TRIGGER = "open";
    private static final String CLOSE_TRIGGER = "close";
    private static final int INPUT_SLOT_COUNT = 3;
    private static final int DISPLAY_SLOT = 3;
    private static final int INVENTORY_SIZE = 4;
    private static final float MAX_ETHER = 64.0f;
    private static final RawAnimation OPEN_ANIMATION = RawAnimation.begin()
            .thenPlayAndHold("animation.ether_storage.open");
    private static final RawAnimation CLOSE_ANIMATION = RawAnimation.begin()
            .thenPlay("animation.ether_storage.close");

    private final DefaultedList<ItemStack> inventory =
            DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private final AnimatableInstanceCache animationCache =
            GeckoLibUtil.createInstanceCache(this);

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
     * Returns the persistent internal Ether buffer represented by the derived display slot.
     *
     * @return stored Ether units
     */
    public float getStoredEther() {
        return storageEther;
    }

    /**
     * Returns the capacity of the internal buffer, excluding the three glints.
     *
     * @return 64 internal Ether units
     */
    public float getMaxEther() {
        return MAX_ETHER;
    }

    /**
     * Sums the persisted Ether held by input slots zero through two.
     *
     * @return Ether units held in the installed glints
     */
    public float getGlintEther() {
        return sumGlintEther(inventory);
    }

    /**
     * Returns all Ether available for transport from the internal buffer and glints.
     *
     * @return combined internal and per-glint Ether
     */
    public float getTransportableEther() {
        return storageEther + getGlintEther();
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

    /**
     * Adds Ether to the 64-unit internal buffer and returns the amount that did not fit.
     *
     * @param value Ether units offered to the internal buffer
     * @return Ether units left over after filling the internal buffer
     */
    public float increment(float value) {
        float storedBefore = storageEther;
        float storedAfter = Math.min(storedBefore + value, MAX_ETHER);
        setStoredEther(storedAfter);
        return Math.max(0.0f, storedBefore + value - MAX_ETHER);
    }

    /**
     * Removes Ether using the canonical internal/glint selection and reverse-slot drain order.
     *
     * @param value maximum Ether units to remove
     * @return Ether units actually removed
     */
    public float decrement(float value) {
        EtherDrainResult result = drainEther(inventory, storageEther, value);
        if (result.removedEther() <= 0.0f) {
            return 0.0f;
        }

        storageEther = result.storedEther();
        updateDisplayStack();
        markDirty();
        return result.removedEther();
    }

    /**
     * Fills installed glints from slot zero through slot two and returns the remainder.
     *
     * @param value Ether units offered to the installed glints
     * @return Ether units that did not fit
     */
    public float incrementGlint(float value) {
        float remainder = incrementGlints(inventory, value);
        if (remainder < value) {
            markDirty();
        }
        return remainder;
    }

    /**
     * Drains installed glints from slot two through slot zero.
     *
     * @param value maximum Ether units to remove
     * @return Ether units actually removed
     */
    public float decrementGlint(float value) {
        float removedEther = decrementGlints(inventory, value);
        if (removedEther > 0.0f) {
            markDirty();
        }
        return removedEther;
    }

    /**
     * Accepts network input only from horizontal faces other than the block's front.
     *
     * @param side queried input face
     * @return whether the face may accept Ether
     */
    public boolean isInputSide(Direction side) {
        return side != Direction.DOWN
                && side != Direction.UP
                && side != getCachedState().get(EtherealStorageFoundationBlock.FACING);
    }

    /**
     * Exposes the canonical downward Ether output face.
     *
     * @return the downward face
     */
    public Direction getOutputSide() {
        return Direction.DOWN;
    }

    static void serverTick(
            World world,
            BlockPos pos,
            BlockState state,
            EtherealStorageFoundationBlockEntity storage
    ) {
        if (world.getTime() % 5 != 0) {
            return;
        }

        float storedAfter = chargeGlints(storage.inventory, storage.storageEther);
        if (Float.compare(storage.storageEther, storedAfter) == 0) {
            return;
        }

        storage.storageEther = storedAfter;
        storage.updateDisplayStack();
        storage.markDirty();
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
        triggerAnim(STORAGE_CONTROLLER, OPEN_TRIGGER);
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
        triggerAnim(STORAGE_CONTROLLER, CLOSE_TRIGGER);
        open = false;
    }

    /**
     * Registers one synchronized controller whose trigger names match the storage animations.
     *
     * @param controllers GeckoLib controller registrar for this block entity
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                this,
                STORAGE_CONTROLLER,
                0,
                state -> PlayState.STOP
        ).triggerableAnim(OPEN_TRIGGER, OPEN_ANIMATION)
                .triggerableAnim(CLOSE_TRIGGER, CLOSE_ANIMATION));
    }

    /**
     * Returns the per-block-entity GeckoLib state cache used by synchronized triggers.
     *
     * @return this storage instance's animation cache
     */
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
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

    static float sumGlintEther(List<ItemStack> inventory) {
        float storedEther = 0.0f;
        for (int slot = 0; slot < INPUT_SLOT_COUNT; slot++) {
            ItemStack stack = inventory.get(slot);
            if (stack.getItem() instanceof EtherealStorageInputItem) {
                storedEther += GlintEtherData.getStoredEther(stack);
            }
        }
        return storedEther;
    }

    static float incrementGlints(List<ItemStack> inventory, float value) {
        float remainder = value;
        for (int slot = 0; slot < INPUT_SLOT_COUNT; slot++) {
            ItemStack stack = inventory.get(slot);
            if (!(stack.getItem() instanceof EtherealStorageInputItem glintItem)) {
                continue;
            }
            if (GlintEtherData.getStoredEther(stack) != glintItem.getMaxEther()) {
                remainder = GlintEtherData.increment(
                        stack,
                        glintItem.getMaxEther(),
                        remainder
                );
            }
            if (remainder == 0.0f) {
                break;
            }
        }
        return remainder;
    }

    static float decrementGlints(List<ItemStack> inventory, float value) {
        float remaining = value;
        for (int slot = INPUT_SLOT_COUNT - 1; slot >= 0; slot--) {
            ItemStack stack = inventory.get(slot);
            if (!(stack.getItem() instanceof EtherealStorageInputItem)) {
                continue;
            }
            if (GlintEtherData.getStoredEther(stack) > 0.0f) {
                remaining -= GlintEtherData.decrement(stack, remaining);
            }
            if (remaining <= 0.0f) {
                break;
            }
        }
        return value - remaining;
    }

    static float chargeGlints(List<ItemStack> inventory, float storageEther) {
        if (storageEther == 0.0f) {
            return storageEther;
        }

        float storedAfterOffer = Math.max(0.0f, storageEther - 1.0f);
        float offeredEther = glintChargeOffer(storageEther);
        float remainder = incrementGlints(inventory, offeredEther);
        return Math.min(MAX_ETHER, storedAfterOffer + remainder);
    }

    static EtherDrainResult drainEther(
            List<ItemStack> inventory,
            float storageEther,
            float value
    ) {
        EtherDrainResult plannedDrain = drainAvailableEther(
                storageEther,
                sumGlintEther(inventory),
                value
        );
        float removedInternal = storageEther - plannedDrain.storedEther();
        float requestedGlintEther = plannedDrain.removedEther() - removedInternal;
        float removedGlint = requestedGlintEther > 0.0f
                ? decrementGlints(inventory, requestedGlintEther)
                : 0.0f;
        return new EtherDrainResult(
                plannedDrain.storedEther(),
                removedInternal + removedGlint
        );
    }

    static float glintChargeOffer(float storageEther) {
        return storageEther - Math.max(0.0f, storageEther - 1.0f);
    }

    static EtherDrainResult drainAvailableEther(
            float storageEther,
            float glintEther,
            float value
    ) {
        if (storageEther >= value) {
            float storedAfter = Math.max(storageEther - value, 0.0f);
            return new EtherDrainResult(storedAfter, storageEther - storedAfter);
        }

        if (glintEther >= value) {
            return new EtherDrainResult(storageEther, value);
        }

        float storedAfter = Math.max(storageEther - value, 0.0f);
        float removedInternal = storageEther - storedAfter;
        float removedGlint = Math.min(glintEther, value - removedInternal);
        return new EtherDrainResult(storedAfter, removedInternal + removedGlint);
    }
}
