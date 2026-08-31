package ru.feytox.etherology.block.etherealStorage;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EtherealStorageFoundationBytecodeTest {

    private static final String BLOCK_ENTITY =
            "ru/feytox/etherology/block/etherealStorage/"
                    + "EtherealStorageFoundationBlockEntity.class";
    private static final String BLOCK =
            "ru/feytox/etherology/block/etherealStorage/"
                    + "EtherealStorageFoundationBlock.class";
    private static final String SCREEN_HANDLER =
            "ru/feytox/etherology/block/etherealStorage/"
                    + "EtherealStorageFoundationScreenHandler.class";
    private static final String INPUT_SLOT =
            "ru/feytox/etherology/block/etherealStorage/EtherealStorageInputSlot.class";
    private static final String DISPLAY_SLOT =
            "ru/feytox/etherology/block/etherealStorage/EtherealStorageDisplaySlot.class";

    @Test
    void compiledNbtPathWritesAndReadsInventoryAndEtherBeforeRebuildingDisplay()
            throws IOException {
        InvocationInventory writeInvocations = invocations(BLOCK_ENTITY, "writePersistentState");
        assertTrue(writeInvocations.has("net/minecraft/inventory/Inventories", "writeNbt"));
        assertTrue(writeInvocations.has("net/minecraft/nbt/NbtCompound", "putFloat"));

        InvocationInventory readInvocations = invocations(BLOCK_ENTITY, "readPersistentState");
        assertTrue(readInvocations.has("net/minecraft/inventory/Inventories", "readNbt"));
        assertTrue(readInvocations.has("net/minecraft/nbt/NbtCompound", "getFloat"));
        assertTrue(readInvocations.hasOwned("normalizeEther"));
        assertTrue(readInvocations.hasOwned("createDisplayStack"));
        assertTrue(readInvocations.has("net/minecraft/util/collection/DefaultedList", "set"));
        assertTrue(
                readInvocations.indexOf("net/minecraft/inventory/Inventories", "readNbt")
                        < readInvocations.indexOfOwned("createDisplayStack")
        );
    }

    @Test
    void compiledSlotsAndAutomationEnforceTheTypedThreeInputTopology()
            throws IOException {
        assertEquals(
                1,
                countTypeChecks(
                        INPUT_SLOT,
                        "canInsert",
                        "ru/feytox/etherology/item/EtherealStorageInputItem"
                )
        );
        assertReturnsFalse(DISPLAY_SLOT, "canInsert");
        assertReturnsFalse(DISPLAY_SLOT, "canTakeItems");

        InvocationInventory canInsert = invocations(BLOCK_ENTITY, "canInsert");
        assertTrue(canInsert.hasOwned("isValid"));
        assertReturnsFalse(BLOCK_ENTITY, "canExtract");
        assertEquals(3, countIntArrayValues(BLOCK_ENTITY, "getAvailableSlots"));
    }

    @Test
    void compiledQuickMoveRejectsNonInputsAndNeverTargetsTheDisplaySlot()
            throws IOException {
        assertEquals(
                1,
                countTypeChecks(
                        SCREEN_HANDLER,
                        "quickMove",
                        "ru/feytox/etherology/item/EtherealStorageInputItem"
                )
        );
        assertEquals(2, invocations(SCREEN_HANDLER, "quickMove").count("insertItem"));
    }

    @Test
    void compiledBlockOpensAndDropsOnlyFromTheLogicalServer() throws IOException {
        assertServerGuardPrecedesCall(BLOCK, "onUse", "openHandledScreen");
        assertServerGuardPrecedesCall(BLOCK, "onStateReplaced", "spawn");
    }

    private static InvocationInventory invocations(String classResource, String methodName)
            throws IOException {
        InvocationInventory inventory = new InvocationInventory();
        visitMethod(classResource, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitMethodInsn(
                    int opcode,
                    String owner,
                    String name,
                    String descriptor,
                    boolean isInterface
            ) {
                inventory.add(owner, name);
            }
        });
        return inventory;
    }

    private static int countTypeChecks(
            String classResource,
            String methodName,
            String expectedType
    ) throws IOException {
        AtomicInteger count = new AtomicInteger();
        visitMethod(classResource, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitTypeInsn(int opcode, String type) {
                if (opcode == Opcodes.INSTANCEOF && type.equals(expectedType)) {
                    count.incrementAndGet();
                }
            }
        });
        return count.get();
    }

    private static void assertReturnsFalse(String classResource, String methodName)
            throws IOException {
        AtomicBoolean loadsFalse = new AtomicBoolean();
        AtomicBoolean returnsValue = new AtomicBoolean();
        visitMethod(classResource, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitInsn(int opcode) {
                if (opcode == Opcodes.ICONST_0) {
                    loadsFalse.set(true);
                }
                if (opcode == Opcodes.IRETURN && loadsFalse.get()) {
                    returnsValue.set(true);
                }
            }
        });
        assertTrue(returnsValue.get());
    }

    private static int countIntArrayValues(String classResource, String methodName)
            throws IOException {
        AtomicInteger values = new AtomicInteger();
        visitMethod(classResource, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitInsn(int opcode) {
                if (opcode == Opcodes.IASTORE) {
                    values.incrementAndGet();
                }
            }
        });
        return values.get();
    }

    private static void assertServerGuardPrecedesCall(
            String classResource,
            String methodName,
            String invokedMethod
    ) throws IOException {
        AtomicInteger instruction = new AtomicInteger();
        AtomicInteger serverGuard = new AtomicInteger(-1);
        AtomicInteger invocation = new AtomicInteger(-1);
        visitMethod(classResource, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                if (owner.equals("net/minecraft/world/World") && name.equals("isClient")) {
                    serverGuard.set(instruction.get());
                }
                instruction.incrementAndGet();
            }

            @Override
            public void visitMethodInsn(
                    int opcode,
                    String owner,
                    String name,
                    String descriptor,
                    boolean isInterface
            ) {
                if (name.equals(invokedMethod)) {
                    invocation.compareAndSet(-1, instruction.get());
                }
                instruction.incrementAndGet();
            }

            @Override
            public void visitInsn(int opcode) {
                instruction.incrementAndGet();
            }
        });
        assertTrue(serverGuard.get() >= 0);
        assertTrue(invocation.get() > serverGuard.get());
    }

    private static void visitMethod(
            String classResource,
            String methodName,
            MethodVisitor visitor
    ) throws IOException {
        InputStream classStream = EtherealStorageFoundationBytecodeTest.class
                .getClassLoader()
                .getResourceAsStream(classResource);
        assertNotNull(classStream);
        try (classStream) {
            ClassReader reader = new ClassReader(classStream);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions
                ) {
                    return name.equals(methodName) ? visitor : null;
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }
}
