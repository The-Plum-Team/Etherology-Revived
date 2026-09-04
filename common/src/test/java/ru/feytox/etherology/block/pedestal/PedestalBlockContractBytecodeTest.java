package ru.feytox.etherology.block.pedestal;

import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PedestalBlockContractBytecodeTest {

    private static final String BLOCK =
            "ru/feytox/etherology/block/pedestal/PedestalBlock";
    private static final String BLOCK_ENTITY =
            "ru/feytox/etherology/block/pedestal/PedestalBlockEntity";
    private static final String REMOVAL =
            "ru/feytox/etherology/block/pedestal/PedestalBlockEntityRemoval";
    private static final String SHAPE =
            "ru/feytox/etherology/block/pedestal/PedestalShape";

    @Test
    void compiledBlockHasTheExactSharedSurface() throws IOException {
        PedestalClassFile.ClassShape shape = PedestalClassFile.shape(BLOCK);

        assertEquals("net/minecraft/block/HorizontalFacingBlock", shape.superName());
        assertEquals(
                List.of(
                        "net/minecraft/block/BlockEntityProvider",
                        "net/minecraft/block/Waterloggable"
                ),
                shape.interfaces()
        );
        assertTrue((shape.access() & Opcodes.ACC_PUBLIC) != 0);
        assertFalse((shape.access() & Opcodes.ACC_ABSTRACT) != 0);
        assertEquals(
                List.of(
                        "SHAPE",
                        "DECORATION",
                        "CLOTH_COLOR",
                        "MIDDLE_SHAPE",
                        "BOTTOM_SHAPE",
                        "TOP_SHAPE",
                        "FULL_SHAPE"
                ),
                new ArrayList<>(shape.fields().keySet())
        );

        assertField(
                shape,
                "SHAPE",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "Lnet/minecraft/state/property/EnumProperty;",
                "Lnet/minecraft/state/property/EnumProperty<L" + SHAPE + ";>;"
        );
        assertField(
                shape,
                "DECORATION",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "Lnet/minecraft/state/property/BooleanProperty;",
                null
        );
        assertField(
                shape,
                "CLOTH_COLOR",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "Lnet/minecraft/state/property/EnumProperty;",
                "Lnet/minecraft/state/property/EnumProperty"
                        + "<Lnet/minecraft/util/DyeColor;>;"
        );
        for (String field : List.of(
                "MIDDLE_SHAPE",
                "BOTTOM_SHAPE",
                "TOP_SHAPE",
                "FULL_SHAPE"
        )) {
            assertField(
                    shape,
                    field,
                    Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                    "Lnet/minecraft/util/shape/VoxelShape;",
                    null
            );
        }
    }

    @Test
    void declaresExactPropertiesDefaultsAndOneThousandTwentyFourStates()
            throws IOException {
        PedestalClassFile.MethodTrace initializer = PedestalClassFile.trace(
                BLOCK,
                "<clinit>"
        );
        assertEquals(
                List.of("shape", "decoration", "cloth_color"),
                initializer.stringConstants()
        );
        assertEquals(
                List.of(SHAPE, "net/minecraft/util/DyeColor"),
                initializer.typeConstants()
        );
        assertEquals(
                List.of(
                        "net/minecraft/state/property/EnumProperty#of",
                        "net/minecraft/state/property/BooleanProperty#of",
                        "net/minecraft/state/property/EnumProperty#of"
                ),
                initializer.invocations().stream()
                        .filter(invocation -> invocation.owner().contains("property/"))
                        .map(invocation -> invocation.owner() + "#" + invocation.name())
                        .toList()
        );

        PedestalClassFile.MethodTrace constructor = PedestalClassFile.trace(
                BLOCK,
                "<init>"
        );
        assertEquals(
                List.of(
                        "STONE",
                        "FACING",
                        "NORTH",
                        "SHAPE",
                        "FULL",
                        "DECORATION",
                        "CLOTH_COLOR",
                        "WHITE",
                        "WATERLOGGED"
                ),
                constructor.fieldInstructions().stream()
                        .filter(field -> field.opcode() == Opcodes.GETSTATIC)
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );
        assertEquals(
                List.of(
                        "copy",
                        "nonOpaque",
                        "<init>",
                        "getDefaultState",
                        "with",
                        "with",
                        "valueOf",
                        "with",
                        "with",
                        "valueOf",
                        "with",
                        "setDefaultState"
                ),
                invocationNames(constructor)
        );
        assertEquals(2, count(constructor.integerConstants(), 0));

        PedestalClassFile.MethodTrace properties = PedestalClassFile.trace(
                BLOCK,
                "appendProperties"
        );
        assertEquals(
                List.of(
                        "SHAPE",
                        "DECORATION",
                        "CLOTH_COLOR",
                        "FACING",
                        "WATERLOGGED"
                ),
                properties.fieldInstructions().stream()
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );
        assertEquals(List.of("add"), invocationNames(properties));
        assertTrue(properties.integerConstants().contains(5));

        int shapeValues = PedestalShape.values().length;
        int decorationValues = 2;
        int clothColorValues = DyeColor.values().length;
        long facingValues = Arrays.stream(Direction.values())
                .filter(direction -> direction.getAxis().isHorizontal())
                .count();
        int waterloggedValues = 2;
        assertEquals(4, shapeValues);
        assertEquals(16, clothColorValues);
        assertEquals(4, facingValues);
        assertEquals(
                1_024,
                shapeValues
                        * decorationValues
                        * clothColorValues
                        * Math.toIntExact(facingValues)
                        * waterloggedValues
        );
    }

    @Test
    void buildsTheThreeExactCuboidsAndTheirOriginalUnions() throws IOException {
        PedestalClassFile.MethodTrace initializer = PedestalClassFile.trace(
                BLOCK,
                "<clinit>"
        );
        assertEquals(
                List.of(
                        4.0, 0.0, 4.0, 12.0, 16.0, 12.0,
                        3.0, 0.0, 3.0, 13.0, 3.0, 13.0,
                        2.0, 11.0, 2.0, 14.0, 16.0, 14.0
                ),
                initializer.doubleConstants()
        );
        assertEquals(
                List.of(
                        "createCuboidShape",
                        "createCuboidShape",
                        "combineAndSimplify",
                        "createCuboidShape",
                        "combineAndSimplify",
                        "combineAndSimplify"
                ),
                initializer.invocations().stream()
                        .filter(invocation -> invocation.owner().equals("net/minecraft/block/Block")
                                || invocation.owner().equals(
                                        "net/minecraft/util/shape/VoxelShapes"
                                ))
                        .map(PedestalClassFile.Invocation::name)
                        .toList()
        );
        assertEquals(
                List.of(
                        "PUT:SHAPE",
                        "PUT:DECORATION",
                        "PUT:CLOTH_COLOR",
                        "PUT:MIDDLE_SHAPE",
                        "GET:MIDDLE_SHAPE",
                        "PUT:BOTTOM_SHAPE",
                        "GET:MIDDLE_SHAPE",
                        "PUT:TOP_SHAPE",
                        "GET:BOTTOM_SHAPE",
                        "GET:TOP_SHAPE",
                        "PUT:FULL_SHAPE"
                ),
                initializer.fieldInstructions().stream()
                        .filter(field -> field.owner().equals(BLOCK))
                        .map(field -> (field.opcode() == Opcodes.GETSTATIC ? "GET:" : "PUT:")
                                + field.name())
                        .toList()
        );
        assertEquals(
                3,
                initializer.fieldInstructions().stream()
                        .filter(field -> field.owner().equals(
                                "net/minecraft/util/function/BooleanBiFunction"
                        ))
                        .filter(field -> field.name().equals("OR"))
                        .count()
        );
    }

    @Test
    void shapeEnumAndOutlineSwitchRetainTheExactFourWayBranching()
            throws IOException {
        assertEquals(
                List.of("BOTTOM", "MIDDLE", "TOP", "FULL"),
                Arrays.stream(PedestalShape.values()).map(Enum::name).toList()
        );
        assertEquals(
                List.of(false, false, true, true),
                Arrays.stream(PedestalShape.values())
                        .map(PedestalShape::isHasItem)
                        .toList()
        );

        PedestalClassFile.MethodTrace shapeLookup = PedestalClassFile.trace(
                SHAPE,
                "getShape"
        );
        assertEquals(
                List.of(
                        "PEDESTAL",
                        "PEDESTAL",
                        "MIDDLE",
                        "TOP",
                        "BOTTOM",
                        "FULL"
                ),
                shapeLookup.fieldInstructions().stream()
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );
        assertEquals(
                List.of("get", "isOf", "get", "isOf"),
                invocationNames(shapeLookup)
        );
        assertEquals(
                List.of(
                        Opcodes.IFEQ,
                        Opcodes.IFEQ,
                        Opcodes.GOTO,
                        Opcodes.IFEQ,
                        Opcodes.GOTO
                ),
                shapeLookup.jumpOpcodes()
        );

        PedestalClassFile.MethodTrace stringValue = PedestalClassFile.trace(
                SHAPE,
                "asString"
        );
        assertEquals(List.of("name", "toLowerCase"), invocationNames(stringValue));
        assertEquals(
                List.of(new PedestalClassFile.FieldInstruction(
                        Opcodes.GETSTATIC,
                        "java/util/Locale",
                        "ROOT",
                        "Ljava/util/Locale;"
                )),
                stringValue.fieldInstructions()
        );

        PedestalClassFile.MethodTrace outline = PedestalClassFile.trace(
                BLOCK,
                "getOutlineShape"
        );
        assertEquals(
                List.of(
                        "SHAPE",
                        "BOTTOM_SHAPE",
                        "MIDDLE_SHAPE",
                        "TOP_SHAPE",
                        "FULL_SHAPE"
                ),
                outline.fieldInstructions().stream()
                        .filter(field -> field.name().equals("SHAPE")
                                || field.name().endsWith("_SHAPE"))
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );
        assertEquals(
                List.of(new PedestalClassFile.TableSwitch(1, 4, 4)),
                outline.tableSwitches()
        );
        assertTrue(outline.lookupSwitchKeys().isEmpty());
    }

    @Test
    void placementAndNeighborUpdatesDeriveShapeAndWaterWithoutDecoratingColumns()
            throws IOException {
        PedestalClassFile.MethodTrace placement = PedestalClassFile.trace(
                BLOCK,
                "getPlacementState"
        );
        assertEquals(
                List.of(
                        "getWorld",
                        "getBlockPos",
                        "getFluidState",
                        "getWorld",
                        "getBlockPos",
                        "down",
                        "getBlockState",
                        "up",
                        "getBlockState",
                        "getShape",
                        "getDefaultState",
                        "with",
                        "getFluid",
                        "valueOf",
                        "with"
                ),
                invocationNames(placement)
        );
        assertEquals(
                List.of("SHAPE", "WATERLOGGED", "WATER"),
                placement.fieldInstructions().stream()
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );

        PedestalClassFile.MethodTrace neighbor = PedestalClassFile.trace(
                BLOCK,
                "getStateForNeighborUpdate"
        );
        List<String> neighborInvocations = invocationNames(neighbor);
        assertEquals(1, count(neighborInvocations, "scheduleFluidTick"));
        assertEquals(1, count(neighborInvocations, "getShape"));
        assertEquals(1, count(neighborInvocations, "isHasItem"));
        assertEquals(3, count(neighborInvocations, "with"));
        assertEquals(
                List.of(
                        "WATERLOGGED",
                        "WATER",
                        "WATER",
                        "CLOTH_COLOR",
                        "WHITE",
                        "DECORATION",
                        "SHAPE"
                ),
                neighbor.fieldInstructions().stream()
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );

        PedestalClassFile.MethodTrace createBlockEntity = PedestalClassFile.trace(
                BLOCK,
                "createBlockEntity"
        );
        assertEquals(
                List.of("get", "isHasItem", "<init>"),
                invocationNames(createBlockEntity)
        );
        assertEquals(
                List.of(new PedestalClassFile.TypeInstruction(Opcodes.NEW, BLOCK_ENTITY)),
                createBlockEntity.typeInstructions().stream()
                        .filter(type -> type.opcode() == Opcodes.NEW)
                        .toList()
        );
        assertTrue(createBlockEntity.opcodes().contains(Opcodes.ACONST_NULL));
    }

    @Test
    void useAndReplacementKeepServerInteractionSyncDropsAndExplicitRemoval()
            throws IOException {
        PedestalClassFile.MethodTrace use = PedestalClassFile.trace(BLOCK, "onUse");
        assertEquals(
                List.of("getBlockEntity", "interact", "syncData"),
                invocationNames(use)
        );
        assertEquals(
                List.of("isClient", "CONSUME"),
                use.fieldInstructions().stream()
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );
        assertTrue(use.typeInstructions().contains(
                new PedestalClassFile.TypeInstruction(Opcodes.INSTANCEOF, BLOCK_ENTITY)
        ));
        assertTrue(use.typeInstructions().contains(
                new PedestalClassFile.TypeInstruction(
                        Opcodes.CHECKCAST,
                        "net/minecraft/server/world/ServerWorld"
                )
        ));

        PedestalClassFile.MethodTrace replacement = PedestalClassFile.trace(
                BLOCK,
                "onStateReplaced"
        );
        assertEquals(
                List.of(
                        "getBlockEntity",
                        "getOrEmpty",
                        "getOrEmpty",
                        "isEmpty",
                        "get",
                        "isHasItem",
                        "isEmpty",
                        "get",
                        "isHasItem",
                        "getBlock",
                        "isOf",
                        "up",
                        "spawn",
                        "removeBlockEntity",
                        "send"
                ),
                invocationNames(replacement)
        );
        assertEquals(
                REMOVAL + "#send",
                replacement.invocations().get(replacement.invocations().size() - 1)
                        .owner()
                        + "#"
                        + replacement.invocations().get(
                                replacement.invocations().size() - 1
                        ).name()
        );
        assertTrue(replacement.typeInstructions().contains(
                new PedestalClassFile.TypeInstruction(
                        Opcodes.INSTANCEOF,
                        "net/minecraft/server/world/ServerWorld"
                )
        ));

        PedestalClassFile.MethodTrace fluid = PedestalClassFile.trace(
                BLOCK,
                "getFluidState"
        );
        assertEquals(
                List.of("get", "booleanValue", "getStill", "getFluidState"),
                invocationNames(fluid)
        );
        assertEquals(
                List.of("WATERLOGGED", "WATER"),
                fluid.fieldInstructions().stream()
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );
    }

    private static void assertField(
            PedestalClassFile.ClassShape shape,
            String name,
            int access,
            String descriptor,
            String signature
    ) {
        PedestalClassFile.FieldDefinition field = shape.fields().get(name);
        assertEquals(access, field.access(), name);
        assertEquals(descriptor, field.descriptor(), name);
        assertEquals(signature, field.signature(), name);
    }

    private static List<String> invocationNames(PedestalClassFile.MethodTrace trace) {
        return trace.invocations().stream()
                .map(PedestalClassFile.Invocation::name)
                .toList();
    }

    private static <T> long count(List<T> values, T expected) {
        return values.stream().filter(expected::equals).count();
    }
}
