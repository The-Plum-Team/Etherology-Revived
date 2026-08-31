package ru.feytox.etherology.block.matrix;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MatrixBlockEntityNbtTest {

    private static final String CLASS_RESOURCE =
            "ru/feytox/etherology/block/matrix/MatrixBlockEntity.class";
    private static final String MATRIX_OWNER =
            "ru/feytox/etherology/block/matrix/MatrixBlockEntity";
    private static final String NBT_OWNER = "net/minecraft/nbt/NbtCompound";

    @Test
    void compiledNbtMethodsPersistStoredEther() throws IOException {
        assertTrue(referencesStoredEtherNbt("writeNbt", "putFloat", Opcodes.GETFIELD));
        assertTrue(referencesStoredEtherNbt("readNbt", "getFloat", Opcodes.PUTFIELD));
    }

    private boolean referencesStoredEtherNbt(
            String methodName,
            String nbtMethodName,
            int fieldOpcode
    ) throws IOException {
        InputStream classStream = getClass().getClassLoader().getResourceAsStream(CLASS_RESOURCE);
        assertNotNull(classStream);

        AtomicBoolean keyFound = new AtomicBoolean();
        AtomicBoolean nbtMethodFound = new AtomicBoolean();
        AtomicBoolean fieldFound = new AtomicBoolean();
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
                    if (!name.equals(methodName)) return null;

                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitLdcInsn(Object value) {
                            if (value.equals("stored_ether")) keyFound.set(true);
                        }

                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String name,
                                String descriptor,
                                boolean isInterface
                        ) {
                            if (owner.equals(NBT_OWNER) && name.equals(nbtMethodName)) {
                                nbtMethodFound.set(true);
                            }
                        }

                        @Override
                        public void visitFieldInsn(
                                int opcode,
                                String owner,
                                String name,
                                String descriptor
                        ) {
                            if (opcode == fieldOpcode
                                    && owner.equals(MATRIX_OWNER)
                                    && name.equals("storedEther")) {
                                fieldFound.set(true);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        return keyFound.get() && nbtMethodFound.get() && fieldFound.get();
    }
}
