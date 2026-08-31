package ru.feytox.etherology.block.spill_barrel;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SpillBarrelBlockTest {

    private static final String CLASS_RESOURCE =
            "ru/feytox/etherology/block/spill_barrel/SpillBarrelBlock.class";
    private static final String CLASS_OWNER =
            "ru/feytox/etherology/block/spill_barrel/SpillBarrelBlock";
    private static final String BLOCK_STATE_OWNER = "net/minecraft/block/BlockState";

    @Test
    void compiledNeighborUpdateCanResetFrameState() throws Exception {
        InputStream classStream = getClass().getClassLoader().getResourceAsStream(CLASS_RESOURCE);
        assertNotNull(classStream);

        AtomicBoolean helperFound = new AtomicBoolean();
        AtomicBoolean falseResultFound = new AtomicBoolean();
        AtomicBoolean trueResultFound = new AtomicBoolean();
        AtomicBoolean helperCalledByNeighborUpdate = new AtomicBoolean();
        AtomicBoolean blockStateUpdated = new AtomicBoolean();
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
                    if (name.equals("shouldHaveFrame")) {
                        helperFound.set(true);
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.ICONST_0) falseResultFound.set(true);
                                if (opcode == Opcodes.ICONST_1) trueResultFound.set(true);
                            }
                        };
                    }
                    if (!name.equals("getStateForNeighborUpdate")) return null;

                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String name,
                                String descriptor,
                                boolean isInterface
                        ) {
                            if (owner.equals(CLASS_OWNER) && name.equals("shouldHaveFrame")) {
                                helperCalledByNeighborUpdate.set(true);
                            }
                            if (owner.equals(BLOCK_STATE_OWNER) && name.equals("with")) {
                                blockStateUpdated.set(true);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        assertTrue(helperFound.get());
        assertTrue(falseResultFound.get());
        assertTrue(trueResultFound.get());
        assertTrue(helperCalledByNeighborUpdate.get());
        assertTrue(blockStateUpdated.get());
    }
}
