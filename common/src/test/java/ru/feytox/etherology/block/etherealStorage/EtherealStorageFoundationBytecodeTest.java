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

    @Test
    void compiledTickerChargesGlintsOnlyFromTheLogicalServerCadence() throws IOException {
        InvocationInventory tickerLookup = invocations(BLOCK, "lambda$getTicker$0");
        assertTrue(tickerLookup.hasOwned("serverTick"));

        InvocationInventory serverTick = invocations(BLOCK_ENTITY, "serverTick");
        assertTrue(serverTick.has("net/minecraft/world/World", "getTime"));
        assertTrue(serverTick.hasOwned("chargeGlints"));
        assertTrue(serverTick.hasOwned("updateDisplayStack"));
        assertTrue(serverTick.hasOwned("markDirty"));
        assertTrue(loadsIntegerConstant(BLOCK_ENTITY, "serverTick", 5));
    }

    @Test
    void compiledStorageArithmeticUsesTheSharedGlintOwnerAndCombinedDrain() throws IOException {
        InvocationInventory incrementGlints = invocations(BLOCK_ENTITY, "incrementGlints");
        assertTrue(incrementGlints.has(
                "ru/feytox/etherology/item/glints/GlintEtherData",
                "increment"
        ));
        assertTrue(hasLoopStep(BLOCK_ENTITY, "incrementGlints", 1));

        InvocationInventory decrementGlints = invocations(BLOCK_ENTITY, "decrementGlints");
        assertTrue(decrementGlints.has(
                "ru/feytox/etherology/item/glints/GlintEtherData",
                "decrement"
        ));
        assertTrue(hasLoopStep(BLOCK_ENTITY, "decrementGlints", -1));

        InvocationInventory drainEther = invocations(BLOCK_ENTITY, "drainEther");
        assertTrue(drainEther.hasOwned("sumGlintEther"));
        assertTrue(drainEther.hasOwned("drainAvailableEther"));
        assertEquals(1, drainEther.count("decrementGlints"));
        assertTrue(invocations(BLOCK_ENTITY, "getTransportableEther").hasOwned("getGlintEther"));
        assertTrue(containsOpcode(BLOCK_ENTITY, "getTransportableEther", Opcodes.FADD));
        assertTrue(loadsIntegerConstant(BLOCK_ENTITY, "sumGlintEther", 3));
    }

    @Test
    void compiledStorageOwnsOneTriggerableGeckoControllerAndExactAnimations()
            throws IOException {
        assertImplements(
                BLOCK_ENTITY,
                "software/bernie/geckolib/animatable/GeoBlockEntity"
        );

        InvocationInventory constructor = invocations(BLOCK_ENTITY, "<init>");
        assertTrue(constructor.has(
                "software/bernie/geckolib/util/GeckoLibUtil",
                "createInstanceCache"
        ));

        InvocationInventory registration = invocations(BLOCK_ENTITY, "registerControllers");
        assertEquals(1, registration.count("<init>"));
        assertEquals(2, registration.count("triggerableAnim"));
        assertEquals(1, registration.count("add"));
        assertTrue(loadsString(BLOCK_ENTITY, "registerControllers", "storage_controller"));
        assertTrue(loadsString(BLOCK_ENTITY, "registerControllers", "open"));
        assertTrue(loadsString(BLOCK_ENTITY, "registerControllers", "close"));

        InvocationInventory animations = invocations(BLOCK_ENTITY, "<clinit>");
        assertEquals(2, animations.count("begin"));
        assertEquals(1, animations.count("thenPlayAndHold"));
        assertEquals(1, animations.count("thenPlay"));
        assertTrue(loadsString(
                BLOCK_ENTITY,
                "<clinit>",
                "animation.ether_storage.open"
        ));
        assertTrue(loadsString(
                BLOCK_ENTITY,
                "<clinit>",
                "animation.ether_storage.close"
        ));
    }

    @Test
    void compiledFirstAndFinalViewerLifecycleUsesBuiltInGeckoTriggers()
            throws IOException {
        InvocationInventory open = invocations(BLOCK_ENTITY, "onOpen");
        assertEquals(1, open.count("triggerAnim"));
        assertTrue(open.has("net/minecraft/world/World", "playSound"));
        assertTrue(loadsString(BLOCK_ENTITY, "onOpen", "storage_controller"));
        assertTrue(loadsString(BLOCK_ENTITY, "onOpen", "open"));
        assertTrue(readsField(BLOCK_ENTITY, "onOpen", "open"));

        InvocationInventory close = invocations(BLOCK_ENTITY, "onClose");
        assertEquals(1, close.count("triggerAnim"));
        assertTrue(close.has("net/minecraft/world/World", "playSound"));
        assertTrue(loadsString(BLOCK_ENTITY, "onClose", "storage_controller"));
        assertTrue(loadsString(BLOCK_ENTITY, "onClose", "close"));
        assertTrue(readsField(BLOCK_ENTITY, "onClose", "open"));
        assertTrue(readsField(BLOCK_ENTITY, "onClose", "viewers"));
    }

    @Test
    void compiledBlockLeavesVisualOwnershipToTheAnimatedBlockEntityRenderer()
            throws IOException {
        assertTrue(readsStaticField(
                BLOCK,
                "getRenderType",
                "net/minecraft/block/BlockRenderType",
                "ENTITYBLOCK_ANIMATED"
        ));
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

    private static boolean loadsIntegerConstant(
            String classResource,
            String methodName,
            int expectedValue
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethod(classResource, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitInsn(int opcode) {
                if (expectedValue >= -1 && expectedValue <= 5
                        && opcode == Opcodes.ICONST_0 + expectedValue) {
                    found.set(true);
                }
            }

            @Override
            public void visitIntInsn(int opcode, int operand) {
                if (operand == expectedValue) {
                    found.set(true);
                }
            }

            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof Number number && number.intValue() == expectedValue) {
                    found.set(true);
                }
            }
        });
        return found.get();
    }

    private static boolean hasLoopStep(
            String classResource,
            String methodName,
            int expectedIncrement
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethod(classResource, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitIincInsn(int variable, int increment) {
                if (increment == expectedIncrement) {
                    found.set(true);
                }
            }
        });
        return found.get();
    }

    private static boolean containsOpcode(
            String classResource,
            String methodName,
            int expectedOpcode
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethod(classResource, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitInsn(int opcode) {
                if (opcode == expectedOpcode) {
                    found.set(true);
                }
            }
        });
        return found.get();
    }

    private static boolean loadsString(
            String classResource,
            String methodName,
            String expectedValue
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethod(classResource, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitLdcInsn(Object value) {
                if (expectedValue.equals(value)) {
                    found.set(true);
                }
            }
        });
        return found.get();
    }

    private static boolean readsField(
            String classResource,
            String methodName,
            String expectedField
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethod(classResource, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                if (opcode == Opcodes.GETFIELD && name.equals(expectedField)) {
                    found.set(true);
                }
            }
        });
        return found.get();
    }

    private static boolean readsStaticField(
            String classResource,
            String methodName,
            String expectedOwner,
            String expectedField
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethod(classResource, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                if (opcode == Opcodes.GETSTATIC
                        && owner.equals(expectedOwner)
                        && name.equals(expectedField)) {
                    found.set(true);
                }
            }
        });
        return found.get();
    }

    private static void assertImplements(String classResource, String expectedInterface)
            throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        InputStream classStream = EtherealStorageFoundationBytecodeTest.class
                .getClassLoader()
                .getResourceAsStream(classResource);
        assertNotNull(classStream);
        try (classStream) {
            ClassReader reader = new ClassReader(classStream);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visit(
                        int version,
                        int access,
                        String name,
                        String signature,
                        String superName,
                        String[] interfaces
                ) {
                    for (String implementedInterface : interfaces) {
                        if (implementedInterface.equals(expectedInterface)) {
                            found.set(true);
                        }
                    }
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertTrue(found.get());
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
