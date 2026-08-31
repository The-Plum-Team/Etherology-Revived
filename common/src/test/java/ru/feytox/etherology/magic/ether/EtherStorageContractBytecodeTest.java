package ru.feytox.etherology.magic.ether;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EtherStorageContractBytecodeTest {

    private static final String ETHER_STORAGE =
            "ru/feytox/etherology/magic/ether/EtherStorage.class";
    private static final String ETHER_PIPE =
            "ru/feytox/etherology/magic/ether/EtherPipe.class";
    private static final String ETHER_DISPLAY =
            "ru/feytox/etherology/magic/ether/EtherDisplay.class";
    private static final String EVAPORATING_ETHER_PIPE =
            "ru/feytox/etherology/magic/ether/EvaporatingEtherPipe.class";
    private static final String ETHER_TRANSFER =
            "ru/feytox/etherology/magic/ether/EtherTransfer.class";

    @Test
    void sharedContractDependsOnTheEvaporationCapabilityInsteadOfTheFabricChannel()
            throws IOException {
        String constants = classConstants(ETHER_STORAGE);
        assertTrue(constants.contains("EvaporatingEtherPipe"));
        assertFalse(constants.contains("EtherealChannelBlockEntity"));

        assertDirectlyExtends(ETHER_PIPE, "ru/feytox/etherology/magic/ether/EtherStorage");
        assertDirectlyExtends(ETHER_DISPLAY, "ru/feytox/etherology/magic/ether/EtherStorage");
        assertDirectlyExtends(
                EVAPORATING_ETHER_PIPE,
                "ru/feytox/etherology/magic/ether/EtherPipe"
        );
    }

    @Test
    void sharedTransferKeepsTheCanonicalActivationAndOutputSequence() throws IOException {
        MethodInvocations transfer = invocations(ETHER_STORAGE, "transfer");

        assertBefore(transfer, "getTransportableEther", "isActivated");
        assertBefore(transfer, "isActivated", "clearEvaporationState");
        assertBefore(transfer, "clearEvaporationState", "getOutputSide");
        assertBefore(transfer, "getOutputSide", "transferTo");
        assertEquals(1, transfer.count("clearEvaporationState"));
        assertEquals(1, transfer.count("transferTo"));

        MethodInvocations clearState = invocations(
                ETHER_STORAGE,
                "clearEvaporationState"
        );
        assertBefore(clearState, "setEvaporating", "setCrossEvaporating");
        assertEquals(1, clearState.count("setEvaporating"));
        assertEquals(1, clearState.count("setCrossEvaporating"));
    }

    @Test
    void sharedTransferKeepsCanonicalValidationMutationAndRollbackOrder()
            throws IOException {
        MethodInvocations transfer = invocations(ETHER_STORAGE, "transferTo");

        assertBefore(transfer, "getStoragePos", "getVector");
        assertBefore(transfer, "getVector", "add");
        assertBefore(transfer, "add", "getWorldChunk");
        assertBefore(transfer, "getWorldChunk", "getBlockEntity");
        assertBefore(transfer, "add", "getBlockEntity");
        assertBefore(transfer, "getBlockEntity", "setEvaporating");
        assertBefore(transfer, "setEvaporating", "setCrossEvaporating");
        assertBefore(transfer, "setCrossEvaporating", "isCrossEvaporate");
        assertBefore(transfer, "isCrossEvaporate", "isInputSide");
        assertBefore(transfer, "isInputSide", "canInputFrom");
        assertBefore(transfer, "canInputFrom", "canOutputTo");
        assertBefore(transfer, "canOutputTo", "moveAvailable");
        assertEquals(1, transfer.count("setEvaporating"));
        assertEquals(1, transfer.count("setCrossEvaporating"));
        assertEquals(2, transfer.count("evaporate"));
        assertEquals(0, transfer.count("isChunkLoaded"));
        assertEquals(1, transfer.count("getWorldChunk"));
        assertEquals(1, transfer.count("getBlockEntity"));
        assertEquals(1, transfer.count("moveAvailable"));

        MethodInvocations exchange = invocations(ETHER_TRANSFER, "moveAvailable");
        assertBefore(exchange, "getMaxEther", "decrement");
        assertBefore(exchange, "getStoredEther", "decrement");
        assertBefore(exchange, "getTransferSize", "decrement");
        assertBefore(exchange, "decrement", "increment");
        assertEquals(1, exchange.count("decrement"));
        assertEquals(2, exchange.count("increment"));

        MethodInvocations evaporate = invocations(ETHER_STORAGE, "evaporate");
        assertBefore(evaporate, "getStoredEther", "setEvaporating");
        assertBefore(evaporate, "setEvaporating", "setCrossEvaporating");
        assertBefore(evaporate, "setCrossEvaporating", "decrement");
        assertEquals(1, evaporate.count("decrement"));
        assertTrue(loadsFloatConstant(ETHER_STORAGE, "evaporate", 0.2f));
    }

    @Test
    void sharedTransferUsesTheNonCreatingLoadedChunkLookupMode() throws IOException {
        assertTrue(loadsCreationType("transferTo", "CHECK"));
        assertFalse(loadsCreationType("transferTo", "IMMEDIATE"));
    }

    private static MethodInvocations invocations(String classResource, String methodName)
            throws IOException {
        MethodInvocations invocations = new MethodInvocations();
        reader(classResource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(methodName)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        invocations.add(owner, name);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return invocations;
    }

    private static void assertDirectlyExtends(String classResource, String expectedInterface)
            throws IOException {
        String[] interfaces = reader(classResource).getInterfaces();
        assertTrue(Arrays.asList(interfaces).contains(expectedInterface));
    }

    private static boolean loadsFloatConstant(
            String classResource,
            String methodName,
            float expectedValue
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        reader(classResource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(methodName)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof Float floatValue
                                && Float.compare(floatValue, expectedValue) == 0) {
                            found.set(true);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found.get();
    }

    private static boolean loadsCreationType(String methodName, String expectedField)
            throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        reader(ETHER_STORAGE).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(methodName)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals(
                                    "net/minecraft/world/chunk/WorldChunk$CreationType"
                                )
                                && name.equals(expectedField)) {
                            found.set(true);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found.get();
    }

    private static void assertBefore(
            MethodInvocations invocations,
            String firstMethod,
            String secondMethod
    ) {
        int firstIndex = invocations.indexOf(firstMethod);
        int secondIndex = invocations.indexOf(secondMethod);
        assertTrue(firstIndex >= 0, "Missing invocation " + firstMethod);
        assertTrue(secondIndex >= 0, "Missing invocation " + secondMethod);
        assertTrue(firstIndex < secondIndex, firstMethod + " must precede " + secondMethod);
    }

    private static String classConstants(String classResource) throws IOException {
        InputStream classStream = EtherStorageContractBytecodeTest.class
                .getClassLoader()
                .getResourceAsStream(classResource);
        assertNotNull(classStream);
        try (classStream) {
            return new String(classStream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    private static ClassReader reader(String classResource) throws IOException {
        InputStream classStream = EtherStorageContractBytecodeTest.class
                .getClassLoader()
                .getResourceAsStream(classResource);
        assertNotNull(classStream);
        try (classStream) {
            return new ClassReader(classStream);
        }
    }
}
