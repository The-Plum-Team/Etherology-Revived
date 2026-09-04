package ru.feytox.etherology.util.misc;

import com.mojang.serialization.Codec;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ItemDataContractTest {

    private static final String CLASS_RESOURCE =
            "/ru/feytox/etherology/util/misc/ItemData.class";
    private static final String ITEM_DATA_KEY_OWNER =
            "ru/feytox/etherology/util/misc/ItemDataKey";
    private static final String ITEM_DATA_OWNER =
            "ru/feytox/etherology/util/misc/ItemData";

    @Test
    void updateOverloadsReplaceTheComponentAndReturnTheSameWrapper() {
        ItemData<Integer> data = new ItemData<>(null, null, 2);

        assertFalse(data.wasSaved());
        assertSame(data, data.set(3, Integer::sum));
        assertEquals(5, data.getComponent());
        assertSame(data, data.set(4, 6, (component, first, second) ->
                component + first * second));
        assertEquals(29, data.getComponent());
    }

    @Test
    void failedEncodingDoesNotMarkTheWrapperSaved() {
        ItemDataKey<Integer> key = new ItemDataKey<>("test_value", Codec.intRange(0, 10));
        ItemData<Integer> data = new ItemData<>(null, key, 11);

        assertThrows(IllegalStateException.class, data::save);
        assertFalse(data.wasSaved());
    }

    @Test
    void compiledSaveMarksSuccessOnlyAfterWritingTheStack() throws IOException {
        AtomicBoolean stackWriteFound = new AtomicBoolean();
        AtomicBoolean savedAfterStackWrite = new AtomicBoolean();
        AtomicBoolean savedAccessorFound = new AtomicBoolean();

        reader().accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (name.equals("save")) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String calledMethod,
                                String descriptor,
                                boolean isInterface
                        ) {
                            if (owner.equals(ITEM_DATA_KEY_OWNER) && calledMethod.equals("set")) {
                                stackWriteFound.set(true);
                            }
                        }

                        @Override
                        public void visitFieldInsn(
                                int opcode,
                                String owner,
                                String fieldName,
                                String descriptor
                        ) {
                            if (opcode == Opcodes.PUTFIELD
                                    && owner.equals(ITEM_DATA_OWNER)
                                    && fieldName.equals("saved")
                                    && stackWriteFound.get()) {
                                savedAfterStackWrite.set(true);
                            }
                        }
                    };
                }
                if (!name.equals("wasSaved")) return null;

                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String fieldName,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.GETFIELD
                                && owner.equals(ITEM_DATA_OWNER)
                                && fieldName.equals("saved")) {
                            savedAccessorFound.set(true);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue(stackWriteFound.get());
        assertTrue(savedAfterStackWrite.get());
        assertTrue(savedAccessorFound.get());
    }

    private ClassReader reader() throws IOException {
        InputStream stream = getClass().getResourceAsStream(CLASS_RESOURCE);
        assertNotNull(stream);
        try (stream) {
            return new ClassReader(stream);
        }
    }
}
