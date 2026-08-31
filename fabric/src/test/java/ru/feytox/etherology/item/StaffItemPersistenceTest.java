package ru.feytox.etherology.item;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StaffItemPersistenceTest {

    private static final String CLASS_RESOURCE = "ru/feytox/etherology/item/StaffItem.class";
    private static final String STAFF_ITEM_OWNER = "ru/feytox/etherology/item/StaffItem";
    private static final String ITEM_DATA_OWNER = "ru/feytox/etherology/util/misc/ItemData";

    @Test
    void bothUsePathsPersistAChangedUndamagedNestedLens() throws Exception {
        InputStream classStream = getClass().getClassLoader().getResourceAsStream(CLASS_RESOURCE);
        assertNotNull(classStream);

        AtomicBoolean usePathPersists = new AtomicBoolean();
        AtomicBoolean stopPathPersists = new AtomicBoolean();
        AtomicBoolean helperChecksSavedState = new AtomicBoolean();
        AtomicBoolean helperWritesNestedLens = new AtomicBoolean();
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
                    if (name.equals("useLensEffect") || name.equals("onStoppedUsing")) {
                        AtomicBoolean target = name.equals("useLensEffect") ? usePathPersists : stopPathPersists;
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(
                                    int opcode,
                                    String owner,
                                    String name,
                                    String descriptor,
                                    boolean isInterface
                            ) {
                                if (owner.equals(STAFF_ITEM_OWNER) && name.equals("persistUndamagedLensChanges")) {
                                    target.set(true);
                                }
                            }
                        };
                    }
                    if (!name.equals("persistUndamagedLensChanges")) return null;

                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String name,
                                String descriptor,
                                boolean isInterface
                        ) {
                            if (owner.equals(ITEM_DATA_OWNER) && name.equals("wasSaved")) {
                                helperChecksSavedState.set(true);
                            }
                            if (owner.equals(STAFF_ITEM_OWNER) && name.equals("setLensComponent")) {
                                helperWritesNestedLens.set(true);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        assertTrue(usePathPersists.get());
        assertTrue(stopPathPersists.get());
        assertTrue(helperChecksSavedState.get());
        assertTrue(helperWritesNestedLens.get());
    }
}
