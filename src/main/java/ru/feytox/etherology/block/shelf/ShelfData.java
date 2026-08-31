package ru.feytox.etherology.block.shelf;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.world.World;
import ru.feytox.etherology.block.furniture.FurnitureData;
import ru.feytox.etherology.block.pedestal.PedestalBlockEntity;
import ru.feytox.etherology.util.inventory.ListBackedInventory;

public class ShelfData extends FurnitureData implements ListBackedInventory {

    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(2, ItemStack.EMPTY);

    public ShelfData(boolean isBottom) {
        super(isBottom);
    }

    @Override
    public void onUse(World world, BlockState state, BlockPos pos, PlayerEntity player, Vec2f hitPos, Hand hand) {
        if (hand.equals(Hand.OFF_HAND) || !(world instanceof ServerWorld serverWorld))
            return;

        boolean isLeft = hitPos.x < 0.5;
        int slot = isLeft ? 0 : 1;
        ItemStack currentStack = getStack(slot);
        ItemStack playerStack = player.getStackInHand(hand);
        boolean isSameItem = playerStack.isOf(currentStack.getItem());

        if (!isSameItem && !playerStack.isEmpty()) {
            // замена предмета на предмет
            ItemStack takingStack = playerStack.copy();
            playerStack = currentStack;
            currentStack = takingStack;

            player.setStackInHand(hand, playerStack);
            setStack(slot, currentStack);
            updateData(serverWorld, pos);
            PedestalBlockEntity.playItemPlaceSound(serverWorld, pos);

        } else if (!currentStack.isEmpty() && !playerStack.isEmpty()) {
            // кладём предмет на НЕПУСТУЮ полку
            int transferCount = getTransferCount(
                    currentStack.getCount(),
                    currentStack.getMaxCount(),
                    playerStack.getCount()
            );

            playerStack.decrement(transferCount);
            currentStack.increment(transferCount);
            updateData(serverWorld, pos);
            PedestalBlockEntity.playItemPlaceSound(serverWorld, pos);

        } else if (!currentStack.isEmpty()) {
            // берём предмет ПУСТОЙ рукой с НЕПУСТОЙ полки
            ItemStack takingStack = currentStack.copy();
            currentStack.setCount(0);

            player.setStackInHand(hand, takingStack);
            updateData(serverWorld, pos);
            PedestalBlockEntity.playItemTakeSound(serverWorld, pos);
        }
    }

    static int getTransferCount(int currentCount, int maxCount, int availableCount) {
        int remainingCapacity = Math.max(0, maxCount - currentCount);
        return Math.min(Math.max(0, availableCount), remainingCapacity);
    }

    @Override
    public DefaultedList<ItemStack> getItems() {
        return inventory;
    }

    @Override
    public void writeNbt(NbtCompound nbtCompound) {
        Inventories.writeNbt(nbtCompound, inventory);
    }

    @Override
    public void readNbt(NbtCompound nbtCompound) {
        inventory.clear();
        Inventories.readNbt(nbtCompound, inventory);
    }
}
