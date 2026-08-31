package ru.feytox.etherology.block.etherealStorage;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Provides the shared ethereal-storage block and its server-owned persistent menu core.
 */
public final class EtherealStorageFoundationBlock extends Block implements BlockEntityProvider {

    /**
     * Creates a static stone-like machine compatible with the existing block model.
     */
    public EtherealStorageFoundationBlock() {
        super(AbstractBlock.Settings.copy(Blocks.STONE).nonOpaque());
    }

    /**
     * Creates the persistent block entity associated with this registered block.
     *
     * @param pos block position supplied by the world
     * @param state placed block state
     * @return a four-slot block entity with persistent Ether state
     */
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new EtherealStorageFoundationBlockEntity(pos, state);
    }

    /**
     * Opens the authoritative block-entity menu only from the logical server.
     *
     * @param state current block state
     * @param world world containing the storage
     * @param pos storage position
     * @param player interacting player
     * @param hand interacting hand
     * @param hit resolved block hit
     * @return a consumed interaction on both logical sides
     */
    @Override
    public ActionResult onUse(
            BlockState state,
            World world,
            BlockPos pos,
            PlayerEntity player,
            Hand hand,
            BlockHitResult hit
    ) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof NamedScreenHandlerFactory screenHandlerFactory) {
                player.openHandledScreen(screenHandlerFactory);
            }
        }
        return ActionResult.SUCCESS;
    }

    /**
     * Drops the three real input stacks once while discarding the derived display stack.
     *
     * @param state state being replaced
     * @param world world containing the storage
     * @param pos storage position
     * @param newState replacement state
     * @param moved whether a piston moved the state
     */
    @Override
    public void onStateReplaced(
            BlockState state,
            World world,
            BlockPos pos,
            BlockState newState,
            boolean moved
    ) {
        if (state.getBlock() != newState.getBlock()) {
            if (!world.isClient) {
                BlockEntity blockEntity = world.getBlockEntity(pos);
                if (blockEntity instanceof EtherealStorageFoundationBlockEntity storage) {
                    for (int slot = 0; slot < storage.getInputSlotCount(); slot++) {
                        ItemStack stack = storage.removeInputStackForDrop(slot);
                        ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), stack);
                    }
                    storage.clear();
                }
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }
}
