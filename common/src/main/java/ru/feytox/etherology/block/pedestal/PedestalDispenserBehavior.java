package ru.feytox.etherology.block.pedestal;

import net.minecraft.block.BlockState;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DyedCarpetBlock;
import net.minecraft.block.dispenser.ItemDispenserBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPointer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Routes a dispenser stack into the Pedestal directly in front of it.
 */
public class PedestalDispenserBehavior extends ItemDispenserBehavior {

    private static final PedestalDispenserBehavior INSTANCE =
            new PedestalDispenserBehavior();

    /**
     * Returns the single stateless behavior instance.
     *
     * @return shared Pedestal dispenser behavior
     */
    public static PedestalDispenserBehavior getInstance() {
        return INSTANCE;
    }

    /** {@inheritDoc} */
    @Override
    protected ItemStack dispenseSilently(BlockPointer pointer, ItemStack stack) {
        return tryUseOnPedestal(pointer, stack)
                ? stack
                : super.dispenseSilently(pointer, stack);
    }

    /**
     * Reports whether the dispenser currently points at a Pedestal inventory.
     *
     * @param pointer dispenser position and state
     * @param stack candidate stack
     * @return {@code true} when the custom behavior should replace vanilla selection
     */
    public static boolean testDispenser(
            BlockPointer pointer,
            ItemStack stack
    ) {
        if (stack.isEmpty()) return false;
        Direction direction = pointer.getBlockState().get(DispenserBlock.FACING);
        BlockPos targetPos = pointer.getPos().add(direction.getVector());
        return pointer.getWorld().getBlockEntity(targetPos)
                instanceof PedestalBlockEntity;
    }

    private boolean tryUseOnPedestal(
            BlockPointer pointer,
            ItemStack stack
    ) {
        if (stack.isEmpty()) return false;
        Direction direction = pointer.getBlockState().get(DispenserBlock.FACING);
        BlockPos targetPos = pointer.getPos().add(direction.getVector());
        ServerWorld world = pointer.getWorld();
        if (!(world.getBlockEntity(targetPos)
                instanceof PedestalBlockEntity pedestal)) {
            return false;
        }

        BlockState state = world.getBlockState(targetPos);
        ItemStack pedestalStack = pedestal.getStack(0);
        ItemStack carpetStack = pedestal.getStack(1);
        boolean carpetPlaced = placeCarpet(
                world,
                pedestal,
                state,
                carpetStack,
                stack,
                direction
        );
        boolean itemPlaced = carpetPlaced || placeItem(
                world,
                pedestal,
                targetPos,
                pedestalStack,
                stack
        );
        if (!itemPlaced) return false;

        pedestal.syncData(world);
        return true;
    }

    private boolean placeCarpet(
            ServerWorld world,
            PedestalBlockEntity pedestal,
            BlockState state,
            ItemStack carpetStack,
            ItemStack candidateStack,
            Direction facing
    ) {
        if (!carpetStack.isEmpty()) return false;
        if (!(candidateStack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        if (!(blockItem.getBlock() instanceof DyedCarpetBlock carpet)) {
            return false;
        }

        pedestal.setStack(1, candidateStack.copyWithCount(1));
        candidateStack.decrement(1);
        pedestal.setCarpetColor(
                world,
                facing.getOpposite(),
                state,
                carpet.getDyeColor(),
                true
        );
        return true;
    }

    private boolean placeItem(
            ServerWorld world,
            PedestalBlockEntity pedestal,
            BlockPos pedestalPos,
            ItemStack pedestalStack,
            ItemStack candidateStack
    ) {
        if (!pedestalStack.isEmpty()) return false;

        pedestal.setStack(0, candidateStack.copyWithCount(1));
        candidateStack.decrement(1);
        PedestalBlockEntity.playItemPlaceSound(world, pedestalPos);
        return true;
    }
}
