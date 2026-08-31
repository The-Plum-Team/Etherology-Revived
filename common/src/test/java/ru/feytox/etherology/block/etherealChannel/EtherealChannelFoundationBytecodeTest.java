package ru.feytox.etherology.block.etherealChannel;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EtherealChannelFoundationBytecodeTest {

    private static final String BLOCK =
            "ru/feytox/etherology/block/etherealChannel/"
                    + "EtherealChannelFoundationBlock.class";
    private static final String BLOCK_ENTITY =
            "ru/feytox/etherology/block/etherealChannel/"
                    + "EtherealChannelFoundationBlockEntity.class";
    private static final String SHAPE =
            "ru/feytox/etherology/block/etherealChannel/"
                    + "EtherealChannelFoundationShape.class";

    @Test
    void compiledEntityOwnsTheCanonicalOneEtherSurfaceAndPersistentKeys()
            throws IOException {
        assertImplements(
                BLOCK_ENTITY,
                "ru/feytox/etherology/magic/ether/EvaporatingEtherPipe"
        );
        assertImplements(
                BLOCK_ENTITY,
                "ru/feytox/etherology/magic/ether/EtherDisplay"
        );
        assertLoadsFloat(BLOCK_ENTITY, "getMaxEther", 1.0f);
        assertLoadsFloat(BLOCK_ENTITY, "getTransferSize", 1.0f);

        List<String> writeInvocations = invocations(BLOCK_ENTITY, "writeNbt");
        assertEquals(1, count(writeInvocations, "net/minecraft/nbt/NbtCompound#putFloat"));
        assertEquals(2, count(writeInvocations, "net/minecraft/nbt/NbtCompound#putBoolean"));
        assertTrue(loadsString(BLOCK_ENTITY, "writeNbt", "stored_ether"));
        assertTrue(loadsString(BLOCK_ENTITY, "writeNbt", "evaporating"));
        assertTrue(loadsString(BLOCK_ENTITY, "writeNbt", "cross_evaporating"));

        List<String> readInvocations = invocations(BLOCK_ENTITY, "readNbt");
        assertEquals(1, count(readInvocations, "net/minecraft/nbt/NbtCompound#getFloat"));
        assertEquals(2, count(readInvocations, "net/minecraft/nbt/NbtCompound#getBoolean"));
        assertTrue(loadsString(BLOCK_ENTITY, "readNbt", "stored_ether"));
        assertTrue(loadsString(BLOCK_ENTITY, "readNbt", "evaporating"));
        assertTrue(loadsString(BLOCK_ENTITY, "readNbt", "cross_evaporating"));
        assertTrue(containsInvocationNamed(readInvocations, "normalizeEther"));

        assertTrue(containsInvocationNamed(
                invocations(BLOCK_ENTITY, "setStoredEther"),
                "normalizeEther"
        ));
    }

    @Test
    void compiledFifthTickTransfersWithoutPublishingUnchangedState()
            throws IOException {
        List<String> transferTick = invocations(BLOCK_ENTITY, "transferTick");
        assertTrue(transferTick.contains("net/minecraft/server/world/ServerWorld#getTime"));
        assertTrue(containsInvocationNamed(transferTick, "transfer"));
        assertFalse(containsInvocationNamed(transferTick, "syncData"));
        assertFalse(containsInvocationNamed(transferTick, "markDirty"));
        assertFalse(containsInvocationNamed(transferTick, "markForUpdate"));
        assertTrue(loadsInteger(BLOCK_ENTITY, "transferTick", 5));
        assertTrue(containsInvocationNamed(invocations(BLOCK_ENTITY, "serverTick"), "transferTick"));
    }

    @Test
    void compiledEntityUsesVanillaUpdatePacketsAndInitialChunkNbt()
            throws IOException {
        assertTrue(invocations(BLOCK_ENTITY, "toUpdatePacket").contains(
                "net/minecraft/network/packet/s2c/play/BlockEntityUpdateS2CPacket#create"
        ));
        assertTrue(containsInvocationNamed(
                invocations(BLOCK_ENTITY, "toInitialChunkDataNbt"),
                "createNbt"
        ));
    }

    @Test
    void compiledIncomingAndEvaporationMutationsPublishImmediately()
            throws IOException {
        assertTrue(containsInvocationNamed(
                invocations(BLOCK_ENTITY, "setStoredEther"),
                "publishMutation"
        ));
        assertTrue(containsInvocationNamed(
                invocations(BLOCK_ENTITY, "setEvaporating"),
                "publishMutation"
        ));
        assertTrue(containsInvocationNamed(
                invocations(BLOCK_ENTITY, "setCrossEvaporating"),
                "publishMutation"
        ));

        List<String> publication = invocations(BLOCK_ENTITY, "publishMutation");
        assertTrue(containsInvocationNamed(publication, "markDirty"));
        assertTrue(publication.contains(
                "net/minecraft/server/world/ServerChunkManager#markForUpdate"
        ));
    }

    @Test
    void compiledTickerIsServerOnlyAndExactTypeGuarded() throws IOException {
        assertTrue(readsField(
                BLOCK,
                "getTicker",
                Opcodes.GETFIELD,
                "net/minecraft/world/World",
                "isClient"
        ));
        assertTrue(readsField(
                BLOCK,
                "getTicker",
                Opcodes.GETSTATIC,
                "ru/feytox/etherology/registry/block/SharedBlockEntities",
                "ETHEREAL_CHANNEL"
        ));
        assertTrue(containsOpcode(BLOCK, "getTicker", Opcodes.IF_ACMPEQ));
        assertTrue(containsInvocationNamed(
                invocations(BLOCK, "lambda$getTicker$0"),
                "serverTick"
        ));
        assertFalse(classConstants(BLOCK).contains("clientTicker"));
        assertFalse(classConstants(BLOCK_ENTITY).contains("net/minecraft/client/"));
    }

    @Test
    void compiledConnectionDiscoveryUsesLeverAndEtherStorageOutputs()
            throws IOException {
        List<String> neighborOutput = invocations(BLOCK, "isNeighborOutput");
        assertTrue(containsInvocationNamed(neighborOutput, "isChunkLoaded"));
        assertTrue(neighborOutput.contains("net/minecraft/block/BlockState#isOf"));
        assertTrue(containsInvocationNamed(neighborOutput, "leverOutputDirection"));
        assertTrue(neighborOutput.contains("net/minecraft/world/BlockView#getBlockEntity"));
        assertTrue(neighborOutput.contains(
                "ru/feytox/etherology/magic/ether/EtherStorage#isOutputSide"
        ));
        assertTrue(indexOfNamed(neighborOutput, "isChunkLoaded")
                < indexOfNamed(neighborOutput, "getBlockState"));
        assertTrue(indexOfNamed(neighborOutput, "isChunkLoaded")
                < indexOfNamed(neighborOutput, "getBlockEntity"));

        List<String> channelState = invocations(BLOCK, "getChannelState");
        assertTrue(channelState.contains("net/minecraft/world/BlockView#getFluidState"));
        assertTrue(containsInvocationNamed(channelState, "withEmptySides"));
        assertTrue(containsInvocationNamed(channelState, "isNeighborOutput"));
        assertTrue(containsInvocationNamed(channelState, "applyFacingState"));

        List<String> outputDirection = invocations(BLOCK_ENTITY, "outputDirection");
        assertTrue(outputDirection.contains("net/minecraft/util/math/Direction#byName"));
    }

    @Test
    void compiledRedstoneWaterAndOutlinePathsRetainCanonicalCalls()
            throws IOException {
        assertTrue(loadsString(BLOCK, "<clinit>", "activated"));
        assertTrue(loadsString(BLOCK, "<clinit>", "in_case"));
        assertTrue(loadsString(BLOCK, "<clinit>", "is_cross"));
        assertTrue(readsField(
                BLOCK,
                "<clinit>",
                Opcodes.GETSTATIC,
                "net/minecraft/block/FacingBlock",
                "FACING"
        ));
        assertTrue(readsField(
                BLOCK,
                "appendProperties",
                Opcodes.GETSTATIC,
                "net/minecraft/state/property/Properties",
                "WATERLOGGED"
        ));

        List<String> redstone = invocations(BLOCK, "neighborUpdate");
        assertTrue(redstone.contains("net/minecraft/world/World#getReceivedStrongRedstonePower"));
        assertTrue(containsInvocationNamed(redstone, "getChannelState"));
        assertTrue(redstone.contains("net/minecraft/world/World#setBlockState"));

        List<String> placement = invocations(BLOCK, "getPlacementState");
        assertTrue(placement.contains(
                "net/minecraft/world/World#getReceivedStrongRedstonePower"
        ));
        assertTrue(containsInvocationNamed(placement, "getChannelState"));

        List<String> blockAdded = invocations(BLOCK, "onBlockAdded");
        assertTrue(blockAdded.contains("net/minecraft/world/World#scheduleBlockTick"));
        assertFalse(blockAdded.contains("net/minecraft/world/World#setBlockState"));

        List<String> scheduledTick = invocations(BLOCK, "scheduledTick");
        assertTrue(scheduledTick.contains(
                "net/minecraft/server/world/ServerWorld#getReceivedStrongRedstonePower"
        ));
        assertTrue(containsInvocationNamed(scheduledTick, "getChannelState"));
        assertTrue(scheduledTick.contains(
                "net/minecraft/server/world/ServerWorld#setBlockState"
        ));
        assertTrue(loadsInteger(BLOCK, "scheduledTick", 3));

        List<String> neighborState = invocations(BLOCK, "getStateForNeighborUpdate");
        assertTrue(neighborState.contains("net/minecraft/world/WorldAccess#scheduleFluidTick"));
        assertTrue(containsInvocationNamed(neighborState, "getTickRate"));

        List<String> outline = invocations(BLOCK, "getOutlineShape");
        assertTrue(outline.contains("net/minecraft/util/shape/VoxelShapes#fullCube"));
        assertTrue(containsInvocationNamed(outline, "getShape"));
    }

    @Test
    void compiledOutlineUsesOnePrecomputedSixtyFourStateShapeTable()
            throws IOException {
        assertTrue(containsInstruction(SHAPE, "buildShapes", Opcodes.ISHL));
        assertTrue(invocations(SHAPE, "buildShapes").contains(
                "net/minecraft/util/shape/VoxelShapes#combineAndSimplify"
        ));
        assertFalse(containsInvocationNamed(
                invocations(SHAPE, "getShape"),
                "combineAndSimplify"
        ));
    }

    private static List<String> invocations(String classResource, String methodName)
            throws IOException {
        List<String> invocations = new ArrayList<>();
        visitMethod(classResource, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitMethodInsn(
                    int opcode,
                    String owner,
                    String name,
                    String descriptor,
                    boolean isInterface
            ) {
                invocations.add(owner + "#" + name);
            }
        });
        return invocations;
    }

    private static int count(List<String> invocations, String expectedInvocation) {
        int count = 0;
        for (String invocation : invocations) {
            if (invocation.equals(expectedInvocation)) {
                count++;
            }
        }
        return count;
    }

    private static boolean containsInvocationNamed(List<String> invocations, String methodName) {
        return indexOfNamed(invocations, methodName) >= 0;
    }

    private static int indexOfNamed(List<String> invocations, String methodName) {
        for (int index = 0; index < invocations.size(); index++) {
            if (invocations.get(index).endsWith("#" + methodName)) {
                return index;
            }
        }
        return -1;
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

    private static boolean loadsInteger(
            String classResource,
            String methodName,
            int expectedValue
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethod(classResource, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitInsn(int opcode) {
                if (expectedValue >= -1
                        && expectedValue <= 5
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

    private static void assertLoadsFloat(
            String classResource,
            String methodName,
            float expectedValue
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethod(classResource, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitInsn(int opcode) {
                if (Float.compare(expectedValue, 1.0f) == 0 && opcode == Opcodes.FCONST_1) {
                    found.set(true);
                }
            }

            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof Float floatValue
                        && Float.compare(floatValue, expectedValue) == 0) {
                    found.set(true);
                }
            }
        });
        assertTrue(found.get());
    }

    private static boolean readsField(
            String classResource,
            String methodName,
            int expectedOpcode,
            String expectedOwner,
            String expectedName
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethod(classResource, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitFieldInsn(
                    int opcode,
                    String owner,
                    String name,
                    String descriptor
            ) {
                if (opcode == expectedOpcode
                        && owner.equals(expectedOwner)
                        && name.equals(expectedName)) {
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
            public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) {
                if (opcode == expectedOpcode) {
                    found.set(true);
                }
            }
        });
        return found.get();
    }

    private static boolean containsInstruction(
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

    private static void assertImplements(String classResource, String expectedInterface)
            throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        InputStream classStream = EtherealChannelFoundationBytecodeTest.class
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

    private static String classConstants(String classResource) throws IOException {
        InputStream classStream = EtherealChannelFoundationBytecodeTest.class
                .getClassLoader()
                .getResourceAsStream(classResource);
        assertNotNull(classStream);
        try (classStream) {
            return new String(classStream.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
        }
    }

    private static void visitMethod(
            String classResource,
            String methodName,
            MethodVisitor visitor
    ) throws IOException {
        InputStream classStream = EtherealChannelFoundationBytecodeTest.class
                .getClassLoader()
                .getResourceAsStream(classResource);
        assertNotNull(classStream);
        AtomicInteger matches = new AtomicInteger();
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
                    if (name.equals(methodName)) {
                        matches.incrementAndGet();
                        return visitor;
                    }
                    return null;
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertTrue(matches.get() > 0, "Missing method " + methodName + " in " + classResource);
    }
}
