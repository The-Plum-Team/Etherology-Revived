package ru.feytox.etherology.block.pedestal;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PedestalDispenserBehaviorBytecodeTest {

    private static final String BEHAVIOR =
            "ru/feytox/etherology/block/pedestal/PedestalDispenserBehavior";
    private static final String BLOCK_ENTITY =
            "ru/feytox/etherology/block/pedestal/PedestalBlockEntity";

    @Test
    void ownsOneEagerStatelessBehaviorInstance() throws IOException {
        PedestalClassFile.ClassShape shape = PedestalClassFile.shape(BEHAVIOR);
        assertEquals(
                "net/minecraft/block/dispenser/ItemDispenserBehavior",
                shape.superName()
        );
        assertEquals(List.of(), shape.interfaces());

        PedestalClassFile.FieldDefinition instance = shape.fields().get("INSTANCE");
        assertEquals(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                instance.access()
        );
        assertEquals("L" + BEHAVIOR + ";", instance.descriptor());

        PedestalClassFile.MethodTrace initializer = PedestalClassFile.trace(
                BEHAVIOR,
                "<clinit>"
        );
        assertEquals(
                List.of(new PedestalClassFile.TypeInstruction(Opcodes.NEW, BEHAVIOR)),
                initializer.typeInstructions()
        );
        assertEquals(List.of("<init>"), invocationNames(initializer));
        assertEquals(
                List.of("INSTANCE"),
                initializer.fieldInstructions().stream()
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );

        PedestalClassFile.MethodTrace accessor = PedestalClassFile.trace(
                BEHAVIOR,
                "getInstance"
        );
        assertEquals(
                List.of("INSTANCE"),
                accessor.fieldInstructions().stream()
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );
        assertEquals(List.of(Opcodes.ARETURN), accessor.opcodes());
    }

    @Test
    void customSuccessReturnsTheMutatedStackAndFailureUsesVanillaFallback()
            throws IOException {
        PedestalClassFile.MethodTrace dispense = PedestalClassFile.trace(
                BEHAVIOR,
                "dispenseSilently"
        );
        assertEquals(
                List.of("tryUseOnPedestal", "dispenseSilently"),
                invocationNames(dispense)
        );
        assertEquals(
                BEHAVIOR,
                dispense.invocations().get(0).owner()
        );
        assertEquals(
                "net/minecraft/block/dispenser/ItemDispenserBehavior",
                dispense.invocations().get(1).owner()
        );
        assertEquals(Opcodes.INVOKESPECIAL, dispense.invocations().get(1).opcode());
        assertEquals(List.of(Opcodes.IFEQ, Opcodes.GOTO), dispense.jumpOpcodes());
        assertEquals(1, count(dispense.opcodes(), Opcodes.ARETURN));
    }

    @Test
    void selectorTargetsTheBlockInFrontForEveryNonemptyDirection()
            throws IOException {
        PedestalClassFile.MethodTrace selector = PedestalClassFile.trace(
                BEHAVIOR,
                "testDispenser"
        );
        assertEquals(
                List.of(
                        "isEmpty",
                        "getBlockState",
                        "get",
                        "getPos",
                        "getVector",
                        "add",
                        "getWorld",
                        "getBlockEntity"
                ),
                invocationNames(selector)
        );
        assertEquals(
                List.of("FACING"),
                selector.fieldInstructions().stream()
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );
        assertTrue(selector.typeInstructions().contains(
                new PedestalClassFile.TypeInstruction(Opcodes.INSTANCEOF, BLOCK_ENTITY)
        ));
        assertEquals(List.of(Opcodes.IFEQ), selector.jumpOpcodes());
        assertTrue(selector.tableSwitches().isEmpty());
        assertTrue(selector.lookupSwitchKeys().isEmpty());
        assertFalse(selector.fieldInstructions().stream().anyMatch(
                field -> field.owner().equals("net/minecraft/util/math/Direction")
        ));
    }

    @Test
    void occupiedCarpetFallsThroughToDisplayAndFullTargetFallsThroughToVanilla()
            throws IOException {
        PedestalClassFile.MethodTrace use = PedestalClassFile.trace(
                BEHAVIOR,
                "tryUseOnPedestal"
        );
        assertEquals(
                List.of(
                        "isEmpty",
                        "getBlockState",
                        "get",
                        "getPos",
                        "getVector",
                        "add",
                        "getWorld",
                        "getBlockEntity",
                        "getBlockState",
                        "getStack",
                        "getStack",
                        "placeCarpet",
                        "placeItem",
                        "syncData"
                ),
                invocationNames(use)
        );
        assertEquals(
                List.of(
                        Opcodes.IFEQ,
                        Opcodes.IFEQ,
                        Opcodes.GOTO,
                        Opcodes.IFNE,
                        Opcodes.IFEQ,
                        Opcodes.GOTO,
                        Opcodes.IFNE
                ),
                use.jumpOpcodes()
        );
        assertEquals(4, count(use.opcodes(), Opcodes.IRETURN));
        assertTrue(use.typeInstructions().contains(
                new PedestalClassFile.TypeInstruction(Opcodes.INSTANCEOF, BLOCK_ENTITY)
        ));
        assertEquals(
                List.of(0, 1),
                use.integerConstants().subList(2, 4)
        );
    }

    @Test
    void carpetPlacementRequiresAnEmptyDecorationAndUsesOppositeFacing()
            throws IOException {
        PedestalClassFile.MethodTrace carpet = PedestalClassFile.trace(
                BEHAVIOR,
                "placeCarpet"
        );
        assertEquals(
                List.of(
                        "isEmpty",
                        "getItem",
                        "getBlock",
                        "copyWithCount",
                        "setStack",
                        "decrement",
                        "getOpposite",
                        "getDyeColor",
                        "setCarpetColor"
                ),
                invocationNames(carpet)
        );
        assertEquals(
                List.of(
                        new PedestalClassFile.TypeInstruction(
                                Opcodes.INSTANCEOF,
                                "net/minecraft/item/BlockItem"
                        ),
                        new PedestalClassFile.TypeInstruction(
                                Opcodes.CHECKCAST,
                                "net/minecraft/item/BlockItem"
                        ),
                        new PedestalClassFile.TypeInstruction(
                                Opcodes.INSTANCEOF,
                                "net/minecraft/block/DyedCarpetBlock"
                        ),
                        new PedestalClassFile.TypeInstruction(
                                Opcodes.CHECKCAST,
                                "net/minecraft/block/DyedCarpetBlock"
                        )
                ),
                carpet.typeInstructions()
        );
        assertEquals(
                List.of(
                        Opcodes.IFNE,
                        Opcodes.IFEQ,
                        Opcodes.GOTO,
                        Opcodes.IFEQ,
                        Opcodes.GOTO
                ),
                carpet.jumpOpcodes()
        );
        assertEquals(4, count(carpet.opcodes(), Opcodes.IRETURN));
        assertEquals(1, count(invocationNames(carpet), "setStack"));
        assertEquals(1, count(invocationNames(carpet), "decrement"));
        assertEquals(1, count(invocationNames(carpet), "setCarpetColor"));
        assertTrue(carpet.tableSwitches().isEmpty());
        assertTrue(carpet.lookupSwitchKeys().isEmpty());
    }

    @Test
    void genericItemPlacementRequiresAnEmptyDisplayAndMovesExactlyOne()
            throws IOException {
        PedestalClassFile.MethodTrace item = PedestalClassFile.trace(
                BEHAVIOR,
                "placeItem"
        );
        assertEquals(
                List.of(
                        "isEmpty",
                        "copyWithCount",
                        "setStack",
                        "decrement",
                        "playItemPlaceSound"
                ),
                invocationNames(item)
        );
        assertEquals(List.of(Opcodes.IFNE), item.jumpOpcodes());
        assertEquals(2, count(item.opcodes(), Opcodes.IRETURN));
        assertEquals(List.of(0, 0, 1, 1, 1), item.integerConstants());
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
