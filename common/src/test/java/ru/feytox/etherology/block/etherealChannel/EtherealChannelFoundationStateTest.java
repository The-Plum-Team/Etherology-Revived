package ru.feytox.etherology.block.etherealChannel;

import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import org.junit.jupiter.api.Test;
import ru.feytox.etherology.enums.PipeSide;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EtherealChannelFoundationStateTest {

    private static final double MIN_CORE = 5.0 / 16.0;
    private static final double MAX_CORE = 11.0 / 16.0;

    @Test
    void ownsTheCanonicalSixSideNamesAndPipeValues() {
        assertEquals(
                Set.of("north", "south", "east", "west", "up", "down"),
                EtherealChannelFoundationShape.SIDE_PROPERTIES.stream()
                        .map(EnumProperty::getName)
                        .collect(Collectors.toSet())
        );

        for (EnumProperty<PipeSide> side :
                EtherealChannelFoundationShape.SIDE_PROPERTIES) {
            assertEquals(
                    Set.of("empty", "in", "out"),
                    side.getValues().stream()
                            .map(PipeSide::asString)
                            .collect(Collectors.toSet())
            );
        }
    }

    @Test
    void mapsOneOutputAndRearInputAcrossAllSixDirections() {
        for (Direction direction : EtherealChannelFoundationShape.DIRECTIONS) {
            assertEquals(
                    direction.asString(),
                    EtherealChannelFoundationShape.property(direction).getName()
            );
            assertEquals(
                    direction.getOpposite().asString(),
                    EtherealChannelFoundationShape.inputProperty(direction).getName()
            );
        }
    }

    @Test
    void distinguishesFallbackStraightCornerAndMultipleInputGeometry() {
        assertFalse(EtherealChannelFoundationShape.shouldCross(0, PipeSide.EMPTY));
        assertFalse(EtherealChannelFoundationShape.shouldCross(1, PipeSide.IN));
        assertTrue(EtherealChannelFoundationShape.shouldCross(1, PipeSide.EMPTY));
        assertTrue(EtherealChannelFoundationShape.shouldCross(2, PipeSide.IN));
    }

    @Test
    void derivesEveryOutputDirectionFromItsCanonicalPropertyName() {
        for (EnumProperty<PipeSide> property :
                EtherealChannelFoundationShape.SIDE_PROPERTIES) {
            assertEquals(
                    property.getName(),
                    Direction.byName(property.getName()).asString()
            );
        }
    }

    @Test
    void normalizesUntrustedChannelEtherIntoItsOneUnitCapacity() {
        assertEquals(0.0f, EtherealChannelFoundationBlockEntity.normalizeEther(Float.NaN));
        assertEquals(
                0.0f,
                EtherealChannelFoundationBlockEntity.normalizeEther(Float.NEGATIVE_INFINITY)
        );
        assertEquals(
                0.0f,
                EtherealChannelFoundationBlockEntity.normalizeEther(Float.POSITIVE_INFINITY)
        );
        assertEquals(0.0f, EtherealChannelFoundationBlockEntity.normalizeEther(-1.0f));
        assertEquals(1.0e-8f, EtherealChannelFoundationBlockEntity.normalizeEther(1.0e-8f));
        assertEquals(0.5f, EtherealChannelFoundationBlockEntity.normalizeEther(0.5f));
        assertEquals(1.0f, EtherealChannelFoundationBlockEntity.normalizeEther(2.0f));
    }

    @Test
    void resolvesWallFloorAndCeilingLeverOutputsExactly() {
        assertEquals(
                Direction.EAST,
                EtherealChannelFoundationShape.leverOutputDirection(
                        WallMountLocation.WALL,
                        Direction.EAST
                )
        );
        assertEquals(
                Direction.UP,
                EtherealChannelFoundationShape.leverOutputDirection(
                        WallMountLocation.FLOOR,
                        Direction.NORTH
                )
        );
        assertEquals(
                Direction.DOWN,
                EtherealChannelFoundationShape.leverOutputDirection(
                        WallMountLocation.CEILING,
                        Direction.SOUTH
                )
        );
    }

    @Test
    void cachesAllSixtyFourConnectionShapesAndExactArmBounds() {
        Set<VoxelShape> cachedShapes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int shapeIndex = 0; shapeIndex < 64; shapeIndex++) {
            VoxelShape shape = EtherealChannelFoundationShape.getShape(shapeIndex);
            assertSame(shape, EtherealChannelFoundationShape.getShape(shapeIndex));
            cachedShapes.add(shape);
        }
        assertEquals(64, cachedShapes.size());

        assertBounds(
                EtherealChannelFoundationShape.getShape(0).getBoundingBox(),
                MIN_CORE,
                MIN_CORE,
                MIN_CORE,
                MAX_CORE,
                MAX_CORE,
                MAX_CORE
        );

        for (int index = 0;
                index < EtherealChannelFoundationShape.SIDE_PROPERTIES.size();
                index++) {
            VoxelShape shape = EtherealChannelFoundationShape.getShape(1 << index);
            assertTouchesFace(
                    shape.getBoundingBox(),
                    Direction.byName(
                            EtherealChannelFoundationShape.SIDE_PROPERTIES
                                    .get(index)
                                    .getName()
                    )
            );
        }
    }

    private static void assertTouchesFace(Box bounds, Direction direction) {
        switch (direction) {
            case NORTH -> assertEquals(0.0, bounds.minZ);
            case SOUTH -> assertEquals(1.0, bounds.maxZ);
            case EAST -> assertEquals(1.0, bounds.maxX);
            case WEST -> assertEquals(0.0, bounds.minX);
            case UP -> assertEquals(1.0, bounds.maxY);
            case DOWN -> assertEquals(0.0, bounds.minY);
        }
    }

    private static void assertBounds(
            Box bounds,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        assertEquals(minX, bounds.minX);
        assertEquals(minY, bounds.minY);
        assertEquals(minZ, bounds.minZ);
        assertEquals(maxX, bounds.maxX);
        assertEquals(maxY, bounds.maxY);
        assertEquals(maxZ, bounds.maxZ);
    }
}
