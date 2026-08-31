package ru.feytox.etherology.block.forestLantern;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import ru.feytox.etherology.data.SharedForestLanternBlockTags;
import ru.feytox.etherology.registry.block.SharedForestLanternBlocks;
import ru.feytox.etherology.util.event.PlayerJumpCallback;

import java.util.EnumMap;
import java.util.Map;

/**
 * Grows from peach logs and may break when a player jumps on a mature lantern.
 *
 * @see net.minecraft.block.WallTorchBlock
 */
public class ForestLanternBlock extends HorizontalFacingBlock implements Fertilizable {

    private static final float BREAK_CHANCE = 0.4F;
    private static final int GROW_FREQUENCY = 30;
    private static final Map<Direction, VoxelShape[]> SHAPES;

    /** The greatest growth stage and the initially placed stage. */
    public static final int MAX_AGE = Properties.AGE_4_MAX;

    /** The exact mining-speed multiplier used by shears on every growth stage. */
    public static final float SHEARS_MINING_SPEED = 15.0F;

    /** Stores one of the five Forest Lantern growth stages. */
    public static final IntProperty AGE = Properties.AGE_4;

    private static boolean jumpEventRegistered;

    /** Creates a mature Forest Lantern with the canonical block settings. */
    public ForestLanternBlock() {
        super(AbstractBlock.Settings.copy(Blocks.BROWN_MUSHROOM_BLOCK)
                .notSolid()
                .sounds(BlockSoundGroup.GRASS)
                .pistonBehavior(PistonBehavior.DESTROY)
                .luminance(value -> 8)
                .postProcess((state, world, pos) -> true)
                .emissiveLighting((state, world, pos) -> true));
        setDefaultState(getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(AGE, MAX_AGE));
    }

    /**
     * Installs the Forest Lantern jump listener once for the active game lifecycle.
     */
    public static synchronized void registerJumpEvent() {
        if (jumpEventRegistered) {
            return;
        }

        PlayerJumpCallback.BEFORE_JUMP.register(ForestLanternBlock::beforePlayerJump);
        jumpEventRegistered = true;
    }

    @Override
    public VoxelShape getOutlineShape(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context
    ) {
        int age = state.get(AGE);
        return SHAPES.get(state.get(FACING))[age];
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        BlockState state = getDefaultState();
        WorldView world = context.getWorld();
        BlockPos pos = context.getBlockPos();

        for (Direction direction : context.getPlacementDirections()) {
            if (!direction.getAxis().isHorizontal()) {
                continue;
            }

            state = state.with(FACING, direction.getOpposite());
            if (state.canPlaceAt(world, pos)) {
                return state;
            }
        }

        return null;
    }

    @Override
    public boolean hasRandomTicks(BlockState state) {
        return state.get(AGE) < MAX_AGE;
    }

    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (random.nextInt(GROW_FREQUENCY) != 0) {
            return;
        }

        int currentAge = state.get(AGE);
        if (currentAge < MAX_AGE) {
            world.setBlockState(pos, state.with(AGE, currentAge + 1));
        }
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        Direction facing = state.get(FACING);
        return canPlaceAt(world, pos, facing, state.get(AGE));
    }

    @Override
    public float calcBlockBreakingDelta(
            BlockState state,
            PlayerEntity player,
            BlockView world,
            BlockPos pos
    ) {
        return state.get(AGE) == 0
                ? 1.0F
                : super.calcBlockBreakingDelta(state, player, world, pos);
    }

    @Override
    public BlockState getStateForNeighborUpdate(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            WorldAccess world,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        if (direction.getOpposite() == state.get(FACING) && !state.canPlaceAt(world, pos)) {
            return Blocks.AIR.getDefaultState();
        }
        return state;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, AGE);
    }

    @Override
    public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    public boolean isFertilizable(
            WorldView world,
            BlockPos pos,
            BlockState state,
            boolean isClient
    ) {
        return state.get(AGE) <= MAX_AGE;
    }

    @Override
    public boolean canGrow(World world, Random random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void grow(ServerWorld world, Random random, BlockPos pos, BlockState state) {
        int currentAge = state.get(AGE);
        if (currentAge == MAX_AGE) {
            tryPlaceNewLanterns(world, random, pos);
            return;
        }

        if (random.nextBoolean()) {
            return;
        }

        world.setBlockState(pos, state.with(AGE, currentAge + 1));
    }

    private static ActionResult beforePlayerJump(PlayerEntity player) {
        World world = player.getWorld();
        if (world.isClient) {
            return ActionResult.PASS;
        }

        BlockPos pos = player.getSteppingPos();
        BlockState state = world.getBlockState(pos);
        ForestLanternBlock forestLantern = SharedForestLanternBlocks.FOREST_LANTERN.get();
        if (!state.isOf(forestLantern)) {
            return ActionResult.PASS;
        }

        if (world.getRandom().nextFloat() > BREAK_CHANCE) {
            return ActionResult.PASS;
        }

        world.playSound(
                null,
                pos,
                forestLantern.getSoundGroup(state).getBreakSound(),
                SoundCategory.BLOCKS,
                0.7F,
                0.9F
        );
        world.breakBlock(pos, state.get(AGE) == MAX_AGE);
        return ActionResult.PASS;
    }

    private static boolean canPlaceAt(
            WorldView world,
            BlockPos pos,
            Direction facing,
            int age
    ) {
        BlockPos logPos = pos.offset(facing.getOpposite());
        BlockState logState = world.getBlockState(logPos);
        if (age == MAX_AGE) {
            return logState.isSideSolidFullSquare(world, logPos, facing);
        }

        return logState.isIn(SharedForestLanternBlockTags.PEACH_LOGS);
    }

    private void tryPlaceNewLanterns(ServerWorld world, Random random, BlockPos centerPos) {
        Iterable<BlockPos> positions = BlockPos.iterateRandomly(random, 27, centerPos, 1);
        for (BlockPos pos : positions) {
            if (pos.equals(centerPos) || !world.isAir(pos)) {
                continue;
            }

            if (tryPlaceLantern(world, pos)) {
                return;
            }
        }
    }

    private boolean tryPlaceLantern(ServerWorld world, BlockPos pos) {
        for (int horizontalIndex = 0; horizontalIndex < 4; horizontalIndex++) {
            Direction direction = Direction.fromHorizontal(horizontalIndex);
            if (!canPlaceAt(world, pos, direction, 0)) {
                continue;
            }

            world.setBlockState(
                    pos,
                    getDefaultState().with(AGE, 0).with(FACING, direction)
            );
            return true;
        }

        return false;
    }

    static {
        SHAPES = new EnumMap<>(Map.of(
                Direction.NORTH, new VoxelShape[]{
                        Block.createCuboidShape(4, 4, 13, 12, 12, 16),
                        Block.createCuboidShape(5.5, 5, 11, 10.5, 11, 16),
                        Block.createCuboidShape(5, 5, 9, 11, 12, 16),
                        Block.createCuboidShape(4, 5, 6, 12, 14, 16),
                        Block.createCuboidShape(2, 4, 4, 14, 16, 16)
                },
                Direction.SOUTH, new VoxelShape[]{
                        Block.createCuboidShape(4, 4, 0, 12, 12, 3),
                        Block.createCuboidShape(5.5, 5, 0, 10.5, 11, 5),
                        Block.createCuboidShape(5, 5, 0, 11, 12, 7),
                        Block.createCuboidShape(4, 5, 0, 12, 14, 10),
                        Block.createCuboidShape(2, 4, 0, 14, 16, 12)
                },
                Direction.WEST, new VoxelShape[]{
                        Block.createCuboidShape(13, 4, 4, 16, 12, 12),
                        Block.createCuboidShape(11, 5, 5.5, 16, 11, 10.5),
                        Block.createCuboidShape(9, 5, 5, 16, 12, 11),
                        Block.createCuboidShape(6, 5, 4, 16, 14, 12),
                        Block.createCuboidShape(4, 4, 2, 16, 16, 14)
                },
                Direction.EAST, new VoxelShape[]{
                        Block.createCuboidShape(0, 4, 4, 3, 12, 12),
                        Block.createCuboidShape(0, 5, 5.5, 5, 11, 10.5),
                        Block.createCuboidShape(0, 5, 5, 7, 12, 11),
                        Block.createCuboidShape(0, 5, 4, 10, 14, 12),
                        Block.createCuboidShape(0, 4, 2, 12, 16, 14)
                }
        ));
    }
}
