package ru.feytox.etherology.registry.block;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EarlyBootstrapRegistrationTest {

    private static final String BOAT_TYPE_MIXIN =
            "ru/feytox/etherology/mixin/BoatEntityTypeMixin.class";
    private static final String BLOCK_ENTITY_TYPE_MIXIN =
            "ru/feytox/etherology/mixin/BlockEntityTypeMixin.class";
    private static final String DECO_BLOCKS =
            "ru/feytox/etherology/registry/block/DecoBlocks.class";
    private static final String EXTRA_BLOCKS =
            "ru/feytox/etherology/registry/block/ExtraBlocksRegistry.class";
    private static final Set<String> PROJECT_BLOCK_REGISTRIES = Set.of(
            "ru/feytox/etherology/registry/block/DecoBlocks",
            "ru/feytox/etherology/registry/block/ExtraBlocksRegistry"
    );

    @Test
    void vanillaClassInitializersDoNotReferenceProjectBlockRegistries() throws IOException {
        assertFalse(referencesOwner(BOAT_TYPE_MIXIN, null, PROJECT_BLOCK_REGISTRIES));
        assertFalse(referencesOwner(BLOCK_ENTITY_TYPE_MIXIN, null, PROJECT_BLOCK_REGISTRIES));
    }

    @Test
    void peachBoatUsesVanillaBootstrapBlockBeforeDeferredBinding() throws IOException {
        assertTrue(referencesField(
                BOAT_TYPE_MIXIN,
                "injectCustomTypes",
                "net/minecraft/block/Blocks",
                "OAK_PLANKS"
        ));
        assertTrue(referencesMethod(
                EXTRA_BLOCKS,
                "registerAll",
                "ru/feytox/etherology/util/misc/BoatTypes",
                "bindPeachBaseBlock"
        ));
    }

    @Test
    void signSupportIsWiredAfterDecorativeBlocksRegister() throws IOException {
        assertTrue(referencesMethod(
                DECO_BLOCKS,
                "addSupportedBlocks",
                "ru/feytox/etherology/mixin/BlockEntityTypeMixin",
                "etherology$setBlocks"
        ));
    }

    private boolean referencesOwner(
            String resourceName,
            String methodName,
            Set<String> owners
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethods(resourceName, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                found.compareAndSet(false, owners.contains(owner));
            }

            @Override
            public void visitMethodInsn(
                    int opcode,
                    String owner,
                    String name,
                    String descriptor,
                    boolean isInterface
            ) {
                found.compareAndSet(false, owners.contains(owner));
            }
        });
        return found.get();
    }

    private boolean referencesField(
            String resourceName,
            String methodName,
            String expectedOwner,
            String expectedName
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethods(resourceName, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                if (owner.equals(expectedOwner) && name.equals(expectedName)) {
                    found.set(true);
                }
            }
        });
        return found.get();
    }

    private boolean referencesMethod(
            String resourceName,
            String methodName,
            String expectedOwner,
            String expectedName
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethods(resourceName, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitMethodInsn(
                    int opcode,
                    String owner,
                    String name,
                    String descriptor,
                    boolean isInterface
            ) {
                if (owner.equals(expectedOwner) && name.equals(expectedName)) {
                    found.set(true);
                }
            }
        });
        return found.get();
    }

    private void visitMethods(
            String resourceName,
            String expectedMethodName,
            MethodVisitor methodVisitor
    ) throws IOException {
        InputStream classStream = getClass().getClassLoader().getResourceAsStream(resourceName);
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
                    if (expectedMethodName == null || name.equals(expectedMethodName)) {
                        return methodVisitor;
                    }
                    return null;
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }
}
