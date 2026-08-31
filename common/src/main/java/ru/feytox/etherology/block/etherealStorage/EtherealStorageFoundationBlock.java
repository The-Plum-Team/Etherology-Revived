package ru.feytox.etherology.block.etherealStorage;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.registry.block.SharedBlockEntities;

/**
 * Provides the shared ethereal-storage block and its server-owned persistent menu core.
 */
public final class EtherealStorageFoundationBlock extends HorizontalFacingBlock
        implements BlockEntityProvider {

    static final DirectionProperty FACING = HorizontalFacingBlock.FACING;

    /**
     * Creates a static stone-like machine compatible with the existing block model.
     */
    public EtherealStorageFoundationBlock() {
        super(AbstractBlock.Settings.copy(Blocks.STONE).nonOpaque());
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
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
     * Leaves visual ownership to the registered Gecko block-entity renderer.
     *
     * @param state current storage state
     * @return the animated block-entity render path
     */
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    /**
     * Faces the storage front toward the player after placement.
     *
     * @param context placement context containing the player's horizontal facing
     * @return placed state facing opposite the player
     */
    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
    }

    /**
     * Installs the storage's logical-server ticker only for its exact block-entity type.
     *
     * @param world world requesting a ticker
     * @param state current block state
     * @param type block-entity type being ticked
     * @param <T> concrete block-entity type
     * @return the fifth-tick glint charger on the server, otherwise no ticker
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            World world,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (world.isClient || type != SharedBlockEntities.ETHEREAL_STORAGE.get()) {
            return null;
        }

        return (tickWorld, tickPos, tickState, blockEntity) ->
                EtherealStorageFoundationBlockEntity.serverTick(
                        tickWorld,
                        tickPos,
                        tickState,
                        (EtherealStorageFoundationBlockEntity) blockEntity
                );
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

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
