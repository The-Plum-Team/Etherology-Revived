package ru.feytox.etherology.block.pedestal;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

import static net.minecraft.state.property.Properties.WATERLOGGED;

/**
 * Implements the connectable, waterloggable, two-slot display Pedestal.
 */
public class PedestalBlock extends HorizontalFacingBlock
        implements BlockEntityProvider, Waterloggable {

    /** Vertical column segment. */
    public static final EnumProperty<PedestalShape> SHAPE =
            EnumProperty.of("shape", PedestalShape.class);

    /** Whether a carpet is rendered around the top. */
    public static final BooleanProperty DECORATION =
            BooleanProperty.of("decoration");

    /** Color of the rendered carpet decoration. */
    public static final EnumProperty<DyeColor> CLOTH_COLOR =
            EnumProperty.of("cloth_color", DyeColor.class);

    private static final VoxelShape MIDDLE_SHAPE =
            Block.createCuboidShape(4.0, 0.0, 4.0, 12.0, 16.0, 12.0);

    private static final VoxelShape BOTTOM_SHAPE = VoxelShapes.combineAndSimplify(
            MIDDLE_SHAPE,
            Block.createCuboidShape(3.0, 0.0, 3.0, 13.0, 3.0, 13.0),
            BooleanBiFunction.OR
    );

    private static final VoxelShape TOP_SHAPE = VoxelShapes.combineAndSimplify(
            MIDDLE_SHAPE,
            Block.createCuboidShape(2.0, 11.0, 2.0, 14.0, 16.0, 14.0),
            BooleanBiFunction.OR
    );

    private static final VoxelShape FULL_SHAPE = VoxelShapes.combineAndSimplify(
            BOTTOM_SHAPE,
            TOP_SHAPE,
            BooleanBiFunction.OR
    );

    /** Creates the original non-opaque stone Pedestal. */
    public PedestalBlock() {
        super(Settings.copy(Blocks.STONE).nonOpaque());
        setDefaultState(
                getDefaultState().with(FACING, Direction.NORTH)
                        .with(SHAPE, PedestalShape.FULL)
                        .with(DECORATION, false)
                        .with(CLOTH_COLOR, DyeColor.WHITE)
                        .with(WATERLOGGED, false)
        );
    }

    /** {@inheritDoc} */
    @Override
    protected void appendProperties(
            StateManager.Builder<Block, BlockState> builder
    ) {
        builder.add(SHAPE, DECORATION, CLOTH_COLOR, FACING, WATERLOGGED);
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return state.get(SHAPE).isHasItem()
                ? new PedestalBlockEntity(pos, state)
                : null;
    }

    /** {@inheritDoc} */
    @Override
    public BlockState getStateForNeighborUpdate(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            WorldAccess world,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        if (state.get(WATERLOGGED)) {
            world.scheduleFluidTick(
                    pos,
                    Fluids.WATER,
                    Fluids.WATER.getTickRate(world)
            );
        }
        if (!neighborPos.down().equals(pos) && !neighborPos.up().equals(pos)) {
            return state;
        }

        PedestalShape shape = PedestalShape.getShape(
                world.getBlockState(pos.down()),
                world.getBlockState(pos.up())
        );
        if (!shape.isHasItem()) {
            state = state.with(CLOTH_COLOR, DyeColor.WHITE)
                    .with(DECORATION, false);
        }
        return state.with(SHAPE, shape);
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        FluidState fluidState = context.getWorld().getFluidState(
                context.getBlockPos()
        );
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        PedestalShape shape = PedestalShape.getShape(
                world.getBlockState(pos.down()),
                world.getBlockState(pos.up())
        );
        return getDefaultState().with(SHAPE, shape)
                .with(WATERLOGGED, fluidState.getFluid() == Fluids.WATER);
    }

    /** {@inheritDoc} */
    @Override
    public ActionResult onUse(
            BlockState state,
            World world,
            BlockPos pos,
            PlayerEntity player,
            Hand hand,
            BlockHitResult hit
    ) {
        if (!world.isClient
                && world.getBlockEntity(pos)
                instanceof PedestalBlockEntity pedestal) {
            pedestal.interact((ServerWorld) world, state, player, hand);
            pedestal.syncData((ServerWorld) world);
        }
        return ActionResult.CONSUME;
    }

    /** {@inheritDoc} */
    @Override
    public VoxelShape getOutlineShape(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context
    ) {
        return switch (state.get(SHAPE)) {
            case BOTTOM -> BOTTOM_SHAPE;
            case MIDDLE -> MIDDLE_SHAPE;
            case TOP -> TOP_SHAPE;
            case FULL -> FULL_SHAPE;
        };
    }

    /** {@inheritDoc} */
    @Override
    public void onStateReplaced(
            BlockState state,
            World world,
            BlockPos pos,
            BlockState newState,
            boolean moved
    ) {
        if (!(world.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal)) {
            return;
        }

        Optional<PedestalShape> oldShape = state.getOrEmpty(SHAPE);
        Optional<PedestalShape> newShape = newState.getOrEmpty(SHAPE);
        if (oldShape.isEmpty()) return;

        boolean inventoryShapeRemoved = oldShape.get().isHasItem()
                && (newShape.isEmpty() || !newShape.get().isHasItem());
        if (inventoryShapeRemoved || !state.isOf(newState.getBlock())) {
            ItemScatterer.spawn(world, pos.up(), pedestal);
            world.removeBlockEntity(pos);
            if (world instanceof ServerWorld serverWorld) {
                PedestalBlockEntityRemoval.send(serverWorld, pos);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED)
                ? Fluids.WATER.getStill(false)
                : super.getFluidState(state);
    }
}
