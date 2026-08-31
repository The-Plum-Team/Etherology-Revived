package ru.feytox.etherology.block.etherealChannel;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FacingBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.enums.PipeSide;
import ru.feytox.etherology.magic.ether.EtherStorage;
import ru.feytox.etherology.registry.block.SharedBlockEntities;

/**
 * Provides the loader-neutral directed Ether channel block and its connection state.
 */
public final class EtherealChannelFoundationBlock extends Block
        implements BlockEntityProvider, Waterloggable {

    /**
     * Stops outgoing Ether transfer while the channel receives strong redstone power.
     */
    public static final BooleanProperty ACTIVATED = BooleanProperty.of("activated");

    /**
     * Selects the channel's one outgoing direction, including vertical directions.
     */
    public static final DirectionProperty FACING = FacingBlock.FACING;

    /**
     * Selects the full-cube outline used when a channel case encloses this channel.
     */
    public static final BooleanProperty IN_CASE = BooleanProperty.of("in_case");

    /**
     * Records whether the visible channel geometry joins through the center as a cross.
     */
    public static final BooleanProperty IS_CROSS = BooleanProperty.of("is_cross");

    /**
     * Defines the canonical six-by-six-by-six channel core used by every outline variant.
     */
    public static final VoxelShape CENTER_SHAPE = EtherealChannelFoundationShape.CENTER;

    /**
     * Creates a brown, non-opaque channel with every connection initially empty.
     */
    public EtherealChannelFoundationBlock() {
        super(AbstractBlock.Settings.create()
                .mapColor(MapColor.BROWN)
                .strength(1.0f)
                .nonOpaque()
                .solid());

        BlockState defaultState = getDefaultState()
                .with(ACTIVATED, false)
                .with(FACING, Direction.NORTH)
                .with(IN_CASE, false)
                .with(IS_CROSS, false)
                .with(Properties.WATERLOGGED, false);
        setDefaultState(EtherealChannelFoundationShape.withEmptySides(defaultState));
    }

    /**
     * Creates the persistent directed-channel block entity for a placed state.
     *
     * @param pos placed block position
     * @param state placed block state
     * @return a one-Ether directed channel
     */
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new EtherealChannelFoundationBlockEntity(pos, state);
    }

    /**
     * Resolves all input connections while retaining the player's six-direction look direction.
     *
     * @param context placement world, position, fluid, and player look direction
     * @return the initial connected and waterlogged state
     */
    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        BlockState placementState = getDefaultState()
                .with(FACING, context.getPlayerLookDirection());
        BlockPos pos = context.getBlockPos();
        return getChannelState(context.getWorld(), placementState, pos).with(
                ACTIVATED,
                context.getWorld().getReceivedStrongRedstonePower(pos) > 0
        );
    }

    /**
     * Schedules post-placement topology and power resolution after block-entity finalization.
     *
     * @param state newly added channel state
     * @param world world receiving the channel
     * @param pos channel position
     * @param oldState state replaced by the channel
     * @param notify whether placement requested neighbor notification
     */
    @Override
    public void onBlockAdded(
            BlockState state,
            World world,
            BlockPos pos,
            BlockState oldState,
            boolean notify
    ) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (!world.isClient && !oldState.isOf(this)) {
            world.scheduleBlockTick(pos, this, 1);
        }
    }

    /**
     * Resolves command/world-set placement before the channel's first transfer cadence.
     *
     * @param state current unresolved or previously resolved state
     * @param world logical server world containing the channel
     * @param pos channel position
     * @param random server tick random source
     */
    @Override
    public void scheduledTick(
            BlockState state,
            ServerWorld world,
            BlockPos pos,
            Random random
    ) {
        BlockState resolvedState = getChannelState(world, state, pos).with(
                ACTIVATED,
                world.getReceivedStrongRedstonePower(pos) > 0
        );
        if (resolvedState != state) {
            world.setBlockState(pos, resolvedState, Block.NOTIFY_ALL);
        }
    }

    /**
     * Schedules water ticks for a waterlogged channel after a neighboring state changes.
     *
     * @param state current channel state
     * @param direction changed neighbor direction
     * @param neighborState changed neighbor state
     * @param world mutable world view
     * @param pos channel position
     * @param neighborPos changed neighbor position
     * @return the superclass's resulting state
     */
    @Override
    public BlockState getStateForNeighborUpdate(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            WorldAccess world,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        if (state.get(Properties.WATERLOGGED)) {
            world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }
        return super.getStateForNeighborUpdate(
                state,
                direction,
                neighborState,
                world,
                pos,
                neighborPos
        );
    }

    /**
     * Exposes still water from states whose canonical waterlogged property is true.
     *
     * @param state current channel state
     * @return still water when waterlogged, otherwise the superclass fluid state
     */
    @Override
    public FluidState getFluidState(BlockState state) {
        if (state.get(Properties.WATERLOGGED)) {
            return Fluids.WATER.getStill(false);
        }
        return super.getFluidState(state);
    }

    /**
     * Uses a full cube for a cased channel and cached core-and-arm geometry otherwise.
     *
     * @param state current channel state
     * @param world block view used for shape resolution
     * @param pos channel position
     * @param context querying entity's shape context
     * @return the canonical channel outline
     */
    @Override
    public VoxelShape getOutlineShape(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context
    ) {
        if (state.get(IN_CASE)) {
            return VoxelShapes.fullCube();
        }
        return EtherealChannelFoundationShape.getShape(state);
    }

    /**
     * Rebuilds connections and blocks outgoing transfer while strong redstone power is present.
     *
     * @param state current channel state
     * @param world server world containing the channel
     * @param pos channel position
     * @param sourceBlock block that caused the update
     * @param sourcePos source block position
     * @param notify whether the source requested neighbor notification
     */
    @Override
    public void neighborUpdate(
            BlockState state,
            World world,
            BlockPos pos,
            Block sourceBlock,
            BlockPos sourcePos,
            boolean notify
    ) {
        boolean powered = world.getReceivedStrongRedstonePower(pos) > 0;
        world.setBlockState(pos, getChannelState(world, state, pos).with(ACTIVATED, powered));
    }

    /**
     * Installs the fifth-tick transfer ticker only on the logical server and exact shared type.
     *
     * @param world world requesting the ticker
     * @param state current channel state
     * @param type block-entity type being ticked
     * @param <T> concrete block-entity type
     * @return a server channel ticker, otherwise no ticker
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            World world,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (world.isClient || type != SharedBlockEntities.ETHEREAL_CHANNEL.get()) {
            return null;
        }

        return (tickWorld, tickPos, tickState, blockEntity) ->
                EtherealChannelFoundationBlockEntity.serverTick(
                        tickWorld,
                        tickPos,
                        tickState,
                        (EtherealChannelFoundationBlockEntity) blockEntity
                );
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(
                FACING,
                ACTIVATED,
                IN_CASE,
                IS_CROSS,
                EtherealChannelFoundationShape.NORTH,
                EtherealChannelFoundationShape.SOUTH,
                EtherealChannelFoundationShape.WEST,
                EtherealChannelFoundationShape.EAST,
                EtherealChannelFoundationShape.UP,
                EtherealChannelFoundationShape.DOWN,
                Properties.WATERLOGGED
        );
    }

    BlockState getChannelState(BlockView world, BlockState state, BlockPos pos) {
        Direction outputDirection = state.get(FACING);
        boolean inCase = state.get(IN_CASE);
        boolean waterlogged = world.getFluidState(pos).getFluid() == Fluids.WATER;
        BlockState connectedState = EtherealChannelFoundationShape.withEmptySides(
                getDefaultState()
                        .with(FACING, outputDirection)
                        .with(IN_CASE, inCase)
                        .with(Properties.WATERLOGGED, waterlogged)
                        .with(IS_CROSS, false)
        );

        int inputCount = 0;
        for (Direction direction : EtherealChannelFoundationShape.DIRECTIONS) {
            if (direction == outputDirection || !isNeighborOutput(world, pos, direction)) {
                continue;
            }

            connectedState = connectedState.with(
                    inputProperty(direction.getOpposite()),
                    PipeSide.IN
            );
            inputCount++;
        }
        return applyFacingState(connectedState, inputCount);
    }

    boolean isNeighborOutput(BlockView world, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.offset(direction);
        if (world instanceof WorldView worldView && !worldView.isChunkLoaded(neighborPos)) {
            return false;
        }

        BlockState neighborState = world.getBlockState(neighborPos);
        if (neighborState.isOf(Blocks.LEVER)
                && leverOutputDirection(neighborState) == direction) {
            return true;
        }

        BlockEntity blockEntity = world.getBlockEntity(neighborPos);
        if (!(blockEntity instanceof EtherStorage storage)) {
            return false;
        }
        return storage.isOutputSide(direction.getOpposite());
    }

    static Direction leverOutputDirection(BlockState leverState) {
        WallMountLocation location = leverState.get(LeverBlock.FACE);
        Direction horizontalFacing = leverState.get(LeverBlock.FACING);
        return EtherealChannelFoundationShape.leverOutputDirection(
                location,
                horizontalFacing
        );
    }

    static BlockState applyFacingState(BlockState state, int inputCount) {
        Direction outputDirection = state.get(FACING);
        BlockState facingState = state.with(outputProperty(outputDirection), PipeSide.OUT);
        EnumProperty<PipeSide> rearInputProperty = inputProperty(outputDirection);
        if (inputCount == 0) {
            return facingState.with(rearInputProperty, PipeSide.IN);
        }
        if (EtherealChannelFoundationShape.shouldCross(
                inputCount,
                facingState.get(rearInputProperty)
        )) {
            return facingState.with(IS_CROSS, true);
        }
        return facingState;
    }

    static EnumProperty<PipeSide> inputProperty(Direction direction) {
        return EtherealChannelFoundationShape.inputProperty(direction);
    }

    static EnumProperty<PipeSide> outputProperty(Direction direction) {
        return EtherealChannelFoundationShape.property(direction);
    }
}
