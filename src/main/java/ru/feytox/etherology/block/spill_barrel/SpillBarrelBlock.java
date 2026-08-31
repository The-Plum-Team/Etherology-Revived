package ru.feytox.etherology.block.spill_barrel;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.registry.block.EBlocks;
import ru.feytox.etherology.util.misc.RegistrableBlock;

import java.util.List;

import static net.minecraft.state.property.Properties.WATERLOGGED;

public class SpillBarrelBlock extends Block implements RegistrableBlock, BlockEntityProvider, Waterloggable {

    private static final BooleanProperty WITH_FRAME = BooleanProperty.of("with_frame");
    private static final VoxelShape NORTH_LOG;
    private static final VoxelShape SOUTH_LOG;
    private static final VoxelShape EAST_LOG;
    private static final VoxelShape WEST_LOG;
    private static final VoxelShape FRAME_SHAPE;
    private static final VoxelShape NORTH_BARREL;
    private static final VoxelShape EAST_BARREL;

    private static final VoxelShape NORTH_SHAPE;
    private static final VoxelShape EAST_SHAPE;


    public SpillBarrelBlock() {
        super(Settings.copy(Blocks.BARREL).nonOpaque());
        this.setDefaultState(this.getDefaultState()
                .with(WITH_FRAME, false)
                .with(HorizontalFacingBlock.FACING, Direction.NORTH)
                .with(WATERLOGGED, false));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
        super.appendTooltip(stack, world, tooltip, options);

        NbtCompound barrelData = BlockItem.getBlockEntityNbt(stack);
        DefaultedList<ItemStack> items = DefaultedList.ofSize(16, ItemStack.EMPTY);
        if (barrelData != null) Inventories.readNbt(barrelData, items);
        ItemStack potionStack = items.stream().filter(item -> !item.isEmpty()).findFirst().orElse(null);
        long potionCount = items.stream().filter(item -> !item.isEmpty()).count();
        MutableText potionInfo = potionStack == null ? null : SpillBarrelBlockEntity.getPotionInfo(potionStack, potionCount, false, Text.empty());

        if (potionCount == 0 || potionInfo == null) {
            tooltip.add(1, Text.translatable("lore.etherology.spill_barrel.empty").formatted(Formatting.GRAY));
            return;
        }

        tooltip.add(1, potionInfo.formatted(Formatting.GRAY));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof SpillBarrelBlockEntity spillBarrel)) return ActionResult.PASS;

        ItemStack handStack = player.getStackInHand(hand);
        if (handStack.isEmpty()) {
            spillBarrel.showPotionsInfo(player);
            return ActionResult.PASS;
        }

        if (spillBarrel.tryFillBarrel(handStack.copy())) {
            player.setStackInHand(hand, ItemUsage.exchangeStack(handStack, player, Items.GLASS_BOTTLE.getDefaultStack()));
            world.playSound(null, pos, SoundEvents.ITEM_BOTTLE_EMPTY, SoundCategory.BLOCKS, 1.0f, 1.0f);
            world.emitGameEvent(null, GameEvent.FLUID_PLACE, pos);
            return ActionResult.CONSUME;
        }

        if (!spillBarrel.isEmpty() && !handStack.isOf(Items.POTION)) {
            ItemStack outputStack = spillBarrel.tryEmptyBarrel(handStack);
            if (outputStack.isOf(Items.POTION)) {
                player.setStackInHand(hand, ItemUsage.exchangeStack(handStack, player, outputStack));
                world.playSound(null, pos, SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.BLOCKS, 1.0f, 1.0f);
                world.emitGameEvent(null, GameEvent.FLUID_PICKUP, pos);
                return ActionResult.CONSUME;
            }
        }

        spillBarrel.showPotionsInfo(player);
        return ActionResult.PASS;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        // TODO: 15/04/2023 add outline for frame
        return switch (state.get(HorizontalFacingBlock.FACING)) {
            case NORTH, SOUTH -> NORTH_SHAPE;
            case EAST, WEST -> EAST_SHAPE;
            default -> super.getOutlineShape(state, world, pos, context);
        };

    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof SpillBarrelBlockEntity spillBarrel) {
            if (!world.isClient && (!player.isCreative() || (player.isCreative() && !spillBarrel.isEmpty()))) {
                ItemStack barrelStack = asItem().getDefaultStack();
                spillBarrel.setStackNbt(barrelStack);
                if (spillBarrel.hasCustomName()) {
                    barrelStack.setCustomName(spillBarrel.getCustomName());
                }
                ItemEntity itemEntity = new ItemEntity(world, (double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5, barrelStack);
                itemEntity.setToDefaultPickupDelay();
                world.spawnEntity(itemEntity);
            }
        }

        super.onBreak(world, pos, state, player);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HorizontalFacingBlock.FACING, WITH_FRAME, WATERLOGGED);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        var fluidState = ctx.getWorld().getFluidState(ctx.getBlockPos());
        var underState = ctx.getWorld().getBlockState(ctx.getBlockPos().down());
        var state = this.getDefaultState().with(HorizontalFacingBlock.FACING, ctx.getHorizontalPlayerFacing().getOpposite());
        if (shouldHaveFrame(underState.isAir(), underState.isOf(EBlocks.SPILL_BARREL))) {
            state = state.with(WITH_FRAME, true);
        }
        return state.with(WATERLOGGED, fluidState.getFluid() == Fluids.WATER);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (state.get(WATERLOGGED))
            world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));

        if (!neighborPos.equals(pos.down())) return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);

        BlockState updatedState = super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
        boolean shouldHaveFrame = shouldHaveFrame(neighborState.isAir(), neighborState.isOf(EBlocks.SPILL_BARREL));
        return updatedState.with(WITH_FRAME, shouldHaveFrame);
    }

    static boolean shouldHaveFrame(boolean neighborIsAir, boolean neighborIsSpillBarrel) {
        return neighborIsAir || neighborIsSpillBarrel;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (itemStack.hasCustomName() && blockEntity instanceof SpillBarrelBlockEntity spillBarrel) {
            spillBarrel.setCustomName(itemStack.getName());
        }
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SpillBarrelBlockEntity(pos, state);
    }

    @Override
    public String getBlockId() {
        return "spill_barrel";
    }

    static {
        NORTH_LOG = Block.createCuboidShape(3.0D, 0.0D, 3.0D, 13.0D, 3.0D, 6.0D);
        SOUTH_LOG = Block.createCuboidShape(3.0D, 0.0D, 10.0D, 13.0D, 3.0D, 13.0D);
        EAST_LOG = Block.createCuboidShape(10.0D, 0.0D, 3.0D, 13.0D, 3.0D, 13.0D);
        WEST_LOG = Block.createCuboidShape(3.0D, 0.0D, 3.0D, 6.0D, 3.0D, 13.0D);
        FRAME_SHAPE = Block.createCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
        NORTH_BARREL = Block.createCuboidShape(4.0D, 2.0D, 2.0D, 12.0D, 10.0D, 14.0D);
        EAST_BARREL = Block.createCuboidShape(2.0D, 2.0D, 4.0D, 14.0D, 10.0D, 12.0D);

        NORTH_SHAPE = VoxelShapes.combineAndSimplify(
                NORTH_BARREL,
                VoxelShapes.union(NORTH_LOG, SOUTH_LOG),
                BooleanBiFunction.OR
        );
        EAST_SHAPE = VoxelShapes.combineAndSimplify(
                EAST_BARREL,
                VoxelShapes.union(EAST_LOG, WEST_LOG),
                BooleanBiFunction.OR
        );
    }
}
