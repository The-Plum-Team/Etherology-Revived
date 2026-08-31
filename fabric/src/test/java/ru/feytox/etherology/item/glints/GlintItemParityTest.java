package ru.feytox.etherology.item.glints;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GlintItemParityTest {

    @Test
    void canonicalFabricGlintDelegatesToTheSharedDataAndCapacityOwners() throws IOException {
        ClassReader glintReader = reader(
                "ru/feytox/etherology/item/glints/GlintItem.class"
        );
        assertEquals(
                "ru/feytox/etherology/item/EtherealStorageInputItem",
                glintReader.getSuperName()
        );
        assertTrue(invokesSharedOwner(glintReader, "getStoredEther", "getStoredEther"));
        assertTrue(invokesSharedOwner(glintReader, "increment", "increment"));
        assertTrue(invokesSharedOwner(glintReader, "decrement", "decrement"));

        ClassReader componentTypes = reader(
                "ru/feytox/etherology/registry/misc/ComponentTypes.class"
        );
        assertTrue(readsSharedStoredEtherKey(componentTypes));
        assertEquals(128.0f, ru.feytox.etherology.item.EtherealStorageInputItem.MAX_ETHER);
    }

    private static boolean invokesSharedOwner(
            ClassReader reader,
            String methodName,
            String delegatedMethod
    ) {
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
                        if (owner.equals("ru/feytox/etherology/item/glints/GlintEtherData")
                                && name.equals(delegatedMethod)) {
                            found.set(true);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found.get();
    }

    private static boolean readsSharedStoredEtherKey(ClassReader reader) {
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
                if (!name.equals("<clinit>")) {
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
                                && owner.equals("ru/feytox/etherology/item/glints/GlintEtherData")
                                && name.equals("STORED_ETHER")) {
                            found.set(true);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found.get();
    }

    private static ClassReader reader(String classResource) throws IOException {
        InputStream classStream = GlintItemParityTest.class
                .getClassLoader()
                .getResourceAsStream(classResource);
        assertNotNull(classStream);
        try (classStream) {
            return new ClassReader(classStream);
        }
    }
}
