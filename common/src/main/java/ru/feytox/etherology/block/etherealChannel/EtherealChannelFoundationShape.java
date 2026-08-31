package ru.feytox.etherology.block.etherealChannel;

import net.minecraft.block.BlockState;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import ru.feytox.etherology.enums.PipeSide;

import java.util.List;

final class EtherealChannelFoundationShape {

    static final EnumProperty<PipeSide> NORTH = EnumProperty.of("north", PipeSide.class);
    static final EnumProperty<PipeSide> SOUTH = EnumProperty.of("south", PipeSide.class);
    static final EnumProperty<PipeSide> EAST = EnumProperty.of("east", PipeSide.class);
    static final EnumProperty<PipeSide> WEST = EnumProperty.of("west", PipeSide.class);
    static final EnumProperty<PipeSide> UP = EnumProperty.of("up", PipeSide.class);
    static final EnumProperty<PipeSide> DOWN = EnumProperty.of("down", PipeSide.class);

    static final VoxelShape CENTER = cuboid(5, 5, 5, 11, 11, 11);

    static final List<Direction> DIRECTIONS = List.of(Direction.values());

    static final List<EnumProperty<PipeSide>> SIDE_PROPERTIES = List.of(
            NORTH,
            SOUTH,
            EAST,
            WEST,
            UP,
            DOWN
    );

    private static final List<VoxelShape> SIDE_SHAPES = List.of(
            cuboid(5, 5, 0, 11, 11, 5),
            cuboid(5, 5, 11, 11, 11, 16),
            cuboid(11, 5, 5, 16, 11, 11),
            cuboid(0, 5, 5, 5, 11, 11),
            cuboid(5, 11, 5, 11, 16, 11),
            cuboid(5, 0, 5, 11, 5, 11)
    );

    private static final VoxelShape[] SHAPES = buildShapes();

    private EtherealChannelFoundationShape() {
    }

    static BlockState withEmptySides(BlockState state) {
        BlockState result = state;
        for (EnumProperty<PipeSide> property : SIDE_PROPERTIES) {
            result = result.with(property, PipeSide.EMPTY);
        }
        return result;
    }

    static EnumProperty<PipeSide> property(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    static EnumProperty<PipeSide> inputProperty(Direction direction) {
        return property(direction.getOpposite());
    }

    static boolean shouldCross(int inputCount, PipeSide rearSide) {
        return inputCount > 0 && (inputCount > 1 || !rearSide.isInput());
    }

    static Direction leverOutputDirection(
            WallMountLocation location,
            Direction horizontalFacing
    ) {
        if (location == WallMountLocation.WALL) {
            return horizontalFacing;
        }
        return location == WallMountLocation.FLOOR ? Direction.UP : Direction.DOWN;
    }

    static VoxelShape getShape(BlockState state) {
        int shapeIndex = 0;
        for (int index = 0; index < SIDE_PROPERTIES.size(); index++) {
            if (!state.get(SIDE_PROPERTIES.get(index)).isEmpty()) {
                shapeIndex |= 1 << index;
            }
        }
        return getShape(shapeIndex);
    }

    static VoxelShape getShape(int shapeIndex) {
        return SHAPES[shapeIndex];
    }

    private static VoxelShape[] buildShapes() {
        VoxelShape[] shapes = new VoxelShape[1 << SIDE_PROPERTIES.size()];
        for (int shapeIndex = 0; shapeIndex < shapes.length; shapeIndex++) {
            VoxelShape shape = CENTER;
            for (int sideIndex = 0; sideIndex < SIDE_SHAPES.size(); sideIndex++) {
                if ((shapeIndex & (1 << sideIndex)) != 0) {
                    shape = VoxelShapes.combineAndSimplify(
                            shape,
                            SIDE_SHAPES.get(sideIndex),
                            BooleanBiFunction.OR
                    );
                }
            }
            shapes[shapeIndex] = shape;
        }
        return shapes;
    }

    private static VoxelShape cuboid(
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        return VoxelShapes.cuboid(
                minX / 16.0,
                minY / 16.0,
                minZ / 16.0,
                maxX / 16.0,
                maxY / 16.0,
                maxZ / 16.0
        );
    }
}
