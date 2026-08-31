package ru.feytox.etherology.item.glints;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GlintEtherDataTest {

    @Test
    void computesCanonicalCapacityAndIncrementRemainder() {
        float storedEther = GlintEtherData.incrementedStoredEther(0.0f, 128.0f, 130.0f);

        assertEquals(128.0f, storedEther);
        assertEquals(2.0f, GlintEtherData.incrementRemainder(0.0f, storedEther, 130.0f));
    }

    @Test
    void computesRemovedEtherWithoutUnderflowing() {
        float storedAfter = GlintEtherData.decrementedStoredEther(5.25f, 20.0f);

        assertEquals(0.0f, storedAfter);
        assertEquals(5.25f, GlintEtherData.removedEther(5.25f, storedAfter));
    }

    @Test
    void compiledOwnerUsesTheExactNestedNbtKeys() throws IOException {
        assertTrue(constants("ru/feytox/etherology/item/glints/GlintEtherData.class")
                .contains("stored_ether"));
        assertTrue(constants("ru/feytox/etherology/util/misc/ItemDataKey.class")
                .contains("etherology:components"));
    }

    private static Set<String> constants(String classResource) throws IOException {
        InputStream classStream = GlintEtherDataTest.class
                .getClassLoader()
                .getResourceAsStream(classResource);
        assertNotNull(classStream);

        Set<String> constants = new HashSet<>();
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
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitLdcInsn(Object value) {
                            if (value instanceof String stringValue) {
                                constants.add(stringValue);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return constants;
    }
}
