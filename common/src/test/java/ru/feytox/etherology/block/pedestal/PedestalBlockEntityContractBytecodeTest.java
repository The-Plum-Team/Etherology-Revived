package ru.feytox.etherology.block.pedestal;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PedestalBlockEntityContractBytecodeTest {

    private static final String BLOCK_ENTITY =
            "ru/feytox/etherology/block/pedestal/PedestalBlockEntity";
    private static final String LIST_INVENTORY =
            "ru/feytox/etherology/util/inventory/ListBackedInventory";
    private static final String UNIQUE_PROVIDER =
            "ru/feytox/etherology/util/misc/UniqueProvider";
    private static final String REVELATION_PROVIDER =
            "ru/feytox/etherology/magic/aspects/RevelationAspectProvider";

    @Test
    void compiledEntityIsAPlainTwoSlotVanillaBlockEntity() throws IOException {
        PedestalClassFile.ClassShape shape = PedestalClassFile.shape(BLOCK_ENTITY);

        assertEquals("net/minecraft/block/entity/BlockEntity", shape.superName());
        assertEquals(
                List.of(
                        LIST_INVENTORY,
                        UNIQUE_PROVIDER,
                        "net/minecraft/inventory/SidedInventory",
                        REVELATION_PROVIDER
                ),
                shape.interfaces()
        );
        assertEquals(
                List.of(
                        "DISPLAY_SLOT",
                        "CARPET_SLOT",
                        "items",
                        "cachedUniqueOffset"
                ),
                new ArrayList<>(shape.fields().keySet())
        );
        assertConstantField(shape, "DISPLAY_SLOT", 0);
        assertConstantField(shape, "CARPET_SLOT", 1);

        PedestalClassFile.FieldDefinition items = shape.fields().get("items");
        assertEquals(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, items.access());
        assertEquals(
                "Lnet/minecraft/util/collection/DefaultedList;",
                items.descriptor()
        );
        assertEquals(
                "Lnet/minecraft/util/collection/DefaultedList"
                        + "<Lnet/minecraft/item/ItemStack;>;",
                items.signature()
        );
        assertNull(items.value());

        PedestalClassFile.FieldDefinition cached = shape.fields().get(
                "cachedUniqueOffset"
        );
        assertEquals(Opcodes.ACC_PRIVATE, cached.access());
        assertEquals("Ljava/lang/Float;", cached.descriptor());
        assertNull(cached.value());

        PedestalClassFile.MethodTrace constructor = PedestalClassFile.trace(
                BLOCK_ENTITY,
                "<init>"
        );
        assertEquals(
                List.of("get", "<init>", "ofSize"),
                invocationNames(constructor)
        );
        assertEquals(
                List.of("PEDESTAL", "EMPTY", "items"),
                constructor.fieldInstructions().stream()
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );
        assertEquals(List.of(2), constructor.integerConstants());
    }

    @Test
    void inventoryDefaultsPreserveStableListSemanticsAndClampToOne()
            throws IOException {
        PedestalClassFile.ClassShape inventory = PedestalClassFile.shape(LIST_INVENTORY);
        assertEquals("java/lang/Object", inventory.superName());
        assertEquals(List.of("net/minecraft/inventory/Inventory"), inventory.interfaces());
        assertTrue((inventory.access() & Opcodes.ACC_INTERFACE) != 0);

        assertEquals(
                List.of("getItems", "size"),
                invocationNames(PedestalClassFile.trace(LIST_INVENTORY, "size"))
        );

        PedestalClassFile.MethodTrace empty = PedestalClassFile.trace(
                LIST_INVENTORY,
                "isEmpty"
        );
        assertEquals(
                List.of("size", "getStack", "isEmpty"),
                invocationNames(empty)
        );
        assertEquals(
                List.of(new PedestalClassFile.Increment(1, 1)),
                empty.increments()
        );

        assertEquals(
                List.of("getItems", "get"),
                invocationNames(PedestalClassFile.trace(LIST_INVENTORY, "getStack"))
        );
        assertEquals(
                List.of("getItems", "splitStack", "isEmpty", "markDirty"),
                invocationNames(PedestalClassFile.trace(
                        LIST_INVENTORY,
                        "removeStack",
                        "(II)Lnet/minecraft/item/ItemStack;"
                ))
        );
        assertEquals(
                List.of("getItems", "removeStack"),
                invocationNames(PedestalClassFile.trace(
                        LIST_INVENTORY,
                        "removeStack",
                        "(I)Lnet/minecraft/item/ItemStack;"
                ))
        );
        assertEquals(
                List.of(
                        "getItems",
                        "set",
                        "getCount",
                        "getMaxCountPerStack",
                        "getMaxCountPerStack",
                        "setCount"
                ),
                invocationNames(PedestalClassFile.trace(LIST_INVENTORY, "setStack"))
        );
        assertEquals(
                List.of("getItems", "clear"),
                invocationNames(PedestalClassFile.trace(LIST_INVENTORY, "clear"))
        );

        PedestalClassFile.MethodTrace maxCount = PedestalClassFile.trace(
                BLOCK_ENTITY,
                "getMaxCountPerStack"
        );
        assertEquals(List.of(1), maxCount.integerConstants());
        assertEquals(1, count(maxCount.opcodes(), Opcodes.IRETURN));
    }

    @Test
    void persistenceSyncAndPacketsCarryBothInventoryAndRemovedState()
            throws IOException {
        PedestalClassFile.MethodTrace write = PedestalClassFile.trace(
                BLOCK_ENTITY,
                "writeNbt"
        );
        assertEquals(
                List.of("writeNbt", "putBoolean", "writeNbt"),
                invocationNames(write)
        );
        assertEquals(List.of("removed"), write.stringConstants());
        assertEquals(
                List.of("items", "removed"),
                write.fieldInstructions().stream()
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );
        assertEquals(
                Opcodes.INVOKESPECIAL,
                write.invocations().get(write.invocations().size() - 1).opcode()
        );

        PedestalClassFile.MethodTrace read = PedestalClassFile.trace(
                BLOCK_ENTITY,
                "readNbt"
        );
        assertEquals(
                List.of("readNbt", "clear", "readNbt", "getBoolean"),
                invocationNames(read)
        );
        assertEquals(List.of("removed"), read.stringConstants());
        assertEquals(
                List.of("items", "items", "removed"),
                read.fieldInstructions().stream()
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );
        assertEquals(Opcodes.INVOKESPECIAL, read.invocations().get(0).opcode());
        assertEquals(
                Opcodes.PUTFIELD,
                read.fieldInstructions().get(read.fieldInstructions().size() - 1).opcode()
        );

        assertEquals(
                List.of("createNbt"),
                invocationNames(PedestalClassFile.trace(
                        BLOCK_ENTITY,
                        "toInitialChunkDataNbt"
                ))
        );
        assertEquals(
                List.of("create"),
                invocationNames(PedestalClassFile.trace(BLOCK_ENTITY, "toUpdatePacket"))
        );
        assertEquals(
                "net/minecraft/network/packet/s2c/play/BlockEntityUpdateS2CPacket",
                PedestalClassFile.trace(BLOCK_ENTITY, "toUpdatePacket")
                        .invocations()
                        .get(0)
                        .owner()
        );

        PedestalClassFile.MethodTrace sync = PedestalClassFile.trace(
                BLOCK_ENTITY,
                "syncData"
        );
        assertEquals(
                List.of("markDirty", "getChunkManager", "markForUpdate"),
                invocationNames(sync)
        );
        assertEquals(List.of("pos"), sync.fieldInstructions().stream()
                .map(PedestalClassFile.FieldInstruction::name)
                .toList());
    }

    @Test
    void sidedAutomationIsClosedFromEveryDirection() throws IOException {
        PedestalClassFile.MethodTrace slots = PedestalClassFile.trace(
                BLOCK_ENTITY,
                "getAvailableSlots"
        );
        assertEquals(List.of(0), slots.integerConstants());
        assertTrue(slots.opcodes().contains(Opcodes.ARETURN));
        assertEquals(List.of(), slots.invocations());

        assertReturnsFalse("canInsert");
        assertReturnsFalse("canExtract");
    }

    @Test
    void revelationReadsOnlyTheDisplayedStackThroughTheSharedAspectLoader()
            throws IOException {
        PedestalClassFile.MethodTrace revelation = PedestalClassFile.trace(
                BLOCK_ENTITY,
                "getRevelationAspects"
        );

        assertEquals(
                List.of("getStack", "isEmpty", "getAspects", "orElse"),
                invocationNames(revelation)
        );
        assertEquals(List.of(0, 0, 0), revelation.integerConstants());
        assertTrue(revelation.invocations().stream().anyMatch(invocation ->
                invocation.owner().equals(
                        "ru/feytox/etherology/data/aspects/AspectsLoader"
                ) && invocation.name().equals("getAspects")));
    }

    @Test
    void carpetBranchesCoverStackPlaceDifferentNoopSingleSwapAndSameRetrieve()
            throws IOException {
        PedestalClassFile.MethodTrace carpet = PedestalClassFile.trace(
                BLOCK_ENTITY,
                "placeCarpet"
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
                        "getItem",
                        "getBlock",
                        "canCombine",
                        "getCount",
                        "getMaxCount",
                        "setStack",
                        "increment",
                        "setStackInHand",
                        "setCarpetColor",
                        "copyWithCount",
                        "getCount",
                        "isEmpty",
                        "setStack",
                        "decrement",
                        "setStackInHand",
                        "getDyeColor",
                        "setCarpetColor",
                        "setStackInHand",
                        "setStack",
                        "getDyeColor",
                        "setCarpetColor"
                ),
                invocationNames(carpet)
        );
        assertEquals(
                List.of(
                        Opcodes.IFEQ,
                        Opcodes.GOTO,
                        Opcodes.IFEQ,
                        Opcodes.GOTO,
                        Opcodes.IFEQ,
                        Opcodes.IF_ICMPGE,
                        Opcodes.IF_ICMPLE,
                        Opcodes.IFNE
                ),
                carpet.jumpOpcodes()
        );
        assertEquals(6, count(carpet.opcodes(), Opcodes.IRETURN));
        assertEquals(3, count(invocationNames(carpet), "setStack"));
        assertEquals(3, count(invocationNames(carpet), "setStackInHand"));
        assertEquals(3, count(invocationNames(carpet), "setCarpetColor"));
    }

    @Test
    void itemBranchesCoverPlaceDifferentNoopFullSameNoopAndSameRetrieve()
            throws IOException {
        PedestalClassFile.MethodTrace interaction = PedestalClassFile.trace(
                BLOCK_ENTITY,
                "interact"
        );
        assertEquals(
                List.of(
                        "getStackInHand",
                        "getStack",
                        "getStack",
                        "isEmpty",
                        "placeCarpet",
                        "isEmpty",
                        "copyWithCount",
                        "setStack",
                        "decrement",
                        "setStackInHand",
                        "playItemPlaceSound",
                        "canCombine",
                        "getCount",
                        "getMaxCount",
                        "setStack",
                        "increment",
                        "setStackInHand",
                        "playItemTakeSound",
                        "isEmpty",
                        "isEmpty",
                        "isEmpty",
                        "setStackInHand",
                        "setStack",
                        "setCarpetColor",
                        "isEmpty",
                        "setStackInHand",
                        "setStack",
                        "playItemTakeSound"
                ),
                invocationNames(interaction)
        );
        assertEquals(
                List.of(
                        Opcodes.IFNE,
                        Opcodes.IFEQ,
                        Opcodes.IFEQ,
                        Opcodes.IFEQ,
                        Opcodes.IF_ICMPGE,
                        Opcodes.IFEQ,
                        Opcodes.IFEQ,
                        Opcodes.IFEQ,
                        Opcodes.IFEQ
                ),
                interaction.jumpOpcodes()
        );
        assertEquals(6, count(interaction.opcodes(), Opcodes.RETURN));
        assertEquals(4, count(invocationNames(interaction), "setStackInHand"));
        assertEquals(4, count(invocationNames(interaction), "setStack"));
        assertEquals(1, count(invocationNames(interaction), "playItemPlaceSound"));
        assertEquals(2, count(invocationNames(interaction), "playItemTakeSound"));
    }

    @Test
    void emptyHandTakesDisplayBeforeDecorationForTheFinalTwoInteractions()
            throws IOException {
        PedestalClassFile.MethodTrace interaction = PedestalClassFile.trace(
                BLOCK_ENTITY,
                "interact"
        );
        List<String> calls = invocationNames(interaction);
        int carpetTransfer = nthIndexOf(calls, "setCarpetColor", 1);
        int displayTransferSound = nthIndexOf(calls, "playItemTakeSound", 2);

        assertTrue(carpetTransfer >= 0);
        assertTrue(displayTransferSound > carpetTransfer);
        assertEquals(
                List.of("isEmpty", "setStackInHand", "setStack", "playItemTakeSound"),
                calls.subList(calls.size() - 4, calls.size())
        );
    }

    @Test
    void decorationStateAndAnimationOffsetRetainTheirExactOwners()
            throws IOException {
        PedestalClassFile.MethodTrace playerDirection = PedestalClassFile.trace(
                BLOCK_ENTITY,
                "setCarpetColor",
                "(Lnet/minecraft/server/world/ServerWorld;"
                        + "Lnet/minecraft/entity/player/PlayerEntity;"
                        + "Lnet/minecraft/block/BlockState;"
                        + "Lnet/minecraft/util/DyeColor;Z)V"
        );
        assertEquals(
                List.of("getHorizontalFacing", "getOpposite", "setCarpetColor"),
                invocationNames(playerDirection)
        );

        PedestalClassFile.MethodTrace stateUpdate = PedestalClassFile.trace(
                BLOCK_ENTITY,
                "setCarpetColor",
                "(Lnet/minecraft/server/world/ServerWorld;"
                        + "Lnet/minecraft/util/math/Direction;"
                        + "Lnet/minecraft/block/BlockState;"
                        + "Lnet/minecraft/util/DyeColor;Z)V"
        );
        assertEquals(
                List.of("with", "valueOf", "with", "with", "setBlockState", "playCarpetSound"),
                invocationNames(stateUpdate)
        );
        assertEquals(
                List.of("pos", "CLOTH_COLOR", "DECORATION", "FACING"),
                stateUpdate.fieldInstructions().stream()
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );

        PedestalClassFile.MethodTrace unique = PedestalClassFile.trace(
                UNIQUE_PROVIDER,
                "getUniqueOffset"
        );
        assertEquals(
                List.of(
                        "getCachedUniqueOffset",
                        "floatValue",
                        "getX",
                        "getY",
                        "getZ",
                        "abs",
                        "valueOf",
                        "setCachedUniqueOffset"
                ),
                invocationNames(unique)
        );
        assertTrue(unique.integerConstants().containsAll(List.of(32, 64, 128)));
        assertEquals(List.of(10.0f, 2.0f * (float) Math.PI), unique.floatConstants());
    }

    private static void assertConstantField(
            PedestalClassFile.ClassShape shape,
            String name,
            int value
    ) {
        PedestalClassFile.FieldDefinition field = shape.fields().get(name);
        assertEquals(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                field.access(),
                name
        );
        assertEquals("I", field.descriptor(), name);
        assertNull(field.signature(), name);
        assertEquals(value, field.value(), name);
    }

    private static void assertReturnsFalse(String methodName) throws IOException {
        PedestalClassFile.MethodTrace trace = PedestalClassFile.trace(
                BLOCK_ENTITY,
                methodName
        );
        assertEquals(List.of(0), trace.integerConstants(), methodName);
        assertEquals(
                List.of(Opcodes.ICONST_0, Opcodes.IRETURN),
                trace.opcodes(),
                methodName
        );
        assertTrue(trace.invocations().isEmpty(), methodName);
    }

    private static List<String> invocationNames(PedestalClassFile.MethodTrace trace) {
        return trace.invocations().stream()
                .map(PedestalClassFile.Invocation::name)
                .toList();
    }

    private static <T> long count(List<T> values, T expected) {
        return values.stream().filter(expected::equals).count();
    }

    private static int nthIndexOf(List<String> values, String expected, int occurrence) {
        int remaining = occurrence;
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).equals(expected)) {
                remaining--;
                if (remaining == 0) {
                    return index;
                }
            }
        }
        return -1;
    }
}
