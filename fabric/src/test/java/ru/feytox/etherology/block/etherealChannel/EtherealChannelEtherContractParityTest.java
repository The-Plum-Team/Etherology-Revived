package ru.feytox.etherology.block.etherealChannel;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EtherealChannelEtherContractParityTest {

    private static final String CHANNEL_BLOCK_ENTITY =
            "ru/feytox/etherology/block/etherealChannel/EtherealChannelBlockEntity.class";

    @Test
    void canonicalFabricChannelConsumesTheSharedEvaporatingPipeCapability()
            throws IOException {
        ClassReader reader = reader();
        List<String> interfaces = Arrays.asList(reader.getInterfaces());
        assertTrue(interfaces.contains(
                "ru/feytox/etherology/magic/ether/EvaporatingEtherPipe"
        ));
        assertTrue(interfaces.contains(
                "ru/feytox/etherology/magic/ether/EtherDisplay"
        ));
        assertFalse(interfaces.contains(
                "ru/feytox/etherology/magic/ether/EtherPipe"
        ));
        assertTrue(hasPublicBooleanSetter(reader, "setEvaporating"));
        assertTrue(hasPublicBooleanSetter(reader, "setCrossEvaporating"));
    }

    private static boolean hasPublicBooleanSetter(ClassReader reader, String expectedName) {
        AtomicBoolean found = new AtomicBoolean();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if ((access & Opcodes.ACC_PUBLIC) != 0
                        && name.equals(expectedName)
                        && descriptor.equals("(Z)V")) {
                    found.set(true);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found.get();
    }

    private static ClassReader reader() throws IOException {
        InputStream classStream = EtherealChannelEtherContractParityTest.class
                .getClassLoader()
                .getResourceAsStream(CHANNEL_BLOCK_ENTITY);
        assertNotNull(classStream);
        try (classStream) {
            return new ClassReader(classStream);
        }
    }
}
