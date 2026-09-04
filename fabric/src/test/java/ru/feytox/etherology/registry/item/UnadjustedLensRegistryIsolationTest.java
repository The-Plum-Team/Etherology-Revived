package ru.feytox.etherology.registry.item;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class UnadjustedLensRegistryIsolationTest {

    private static final String LEGACY_ITEMS =
            "/ru/feytox/etherology/registry/item/EItems.class";
    private static final String FABRIC_INITIALIZER =
            "/ru/feytox/etherology/Etherology.class";
    private static final String SHARED_LENS_ITEMS =
            "ru/feytox/etherology/registry/item/SharedLensItems";
    private static final String LEGACY_ITEMS_OWNER =
            "ru/feytox/etherology/registry/item/EItems";
    private static final String REGISTRY_SUPPLIER =
            "dev/architectury/registry/registries/RegistrySupplier";

    @Test
    void legacyFieldIsTheExactSharedSupplierAlias() throws IOException {
        AtomicInteger aliasFields = new AtomicInteger();
        List<String> aliasEvents = new ArrayList<>();
        reader(LEGACY_ITEMS).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if (name.equals("UNADJUSTED_LENS")) {
                    aliasFields.incrementAndGet();
                    assertEquals(
                            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                            access
                    );
                    assertEquals("Lnet/minecraft/item/Item;", descriptor);
                }
                return null;
            }

            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("<clinit>")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    private boolean recordingAlias;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value.equals("unadjusted_lens")) {
                            aliasEvents.add("ID:unadjusted_lens");
                        }
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW
                                && type.equals("ru/feytox/etherology/item/UnadjustedLens")) {
                            aliasEvents.add("NEW:UnadjustedLens");
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals(SHARED_LENS_ITEMS)
                                && name.equals("UNADJUSTED_LENS")) {
                            recordingAlias = true;
                            aliasEvents.add("SharedLensItems#UNADJUSTED_LENS");
                        }
                        if (opcode == Opcodes.PUTSTATIC
                                && owner.equals(LEGACY_ITEMS_OWNER)
                                && name.equals("UNADJUSTED_LENS")) {
                            aliasEvents.add("EItems#UNADJUSTED_LENS");
                            recordingAlias = false;
                        }
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        if (recordingAlias
                                && owner.equals(REGISTRY_SUPPLIER)
                                && name.equals("get")) {
                            aliasEvents.add("RegistrySupplier#get" + descriptor);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(1, aliasFields.get());
        assertEquals(
                List.of(
                        "SharedLensItems#UNADJUSTED_LENS",
                        "RegistrySupplier#get()Ljava/lang/Object;",
                        "EItems#UNADJUSTED_LENS"
                ),
                aliasEvents
        );
    }

    @Test
    void sharedLensRegistryAttachesOnceImmediatelyBeforeLegacyItems()
            throws IOException {
        Set<String> relevantOwners = Set.of(
                "ru/feytox/etherology/registry/item/SharedToolItems",
                SHARED_LENS_ITEMS,
                LEGACY_ITEMS_OWNER
        );
        List<String> calls = new ArrayList<>();
        reader(FABRIC_INITIALIZER).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("initialize")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        if (relevantOwners.contains(owner)) {
                            calls.add(owner + "#" + name + descriptor);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(
                List.of(
                        "ru/feytox/etherology/registry/item/SharedToolItems#register()V",
                        SHARED_LENS_ITEMS + "#register()V",
                        LEGACY_ITEMS_OWNER + "#registerItems()V"
                ),
                calls
        );
    }

    private static ClassReader reader(String resource) throws IOException {
        InputStream stream = UnadjustedLensRegistryIsolationTest.class
                .getResourceAsStream(resource);
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return new ClassReader(stream);
        }
    }
}
