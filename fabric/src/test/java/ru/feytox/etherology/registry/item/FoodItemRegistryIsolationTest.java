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
import static org.junit.jupiter.api.Assertions.assertNull;

final class FoodItemRegistryIsolationTest {

    private static final String E_ITEMS =
            "ru/feytox/etherology/registry/item/EItems.class";
    private static final String ETHEROLOGY = "ru/feytox/etherology/Etherology.class";
    private static final String SHARED_FOOD_ITEMS =
            "ru/feytox/etherology/registry/item/SharedFoodItems";
    private static final String REGISTRY_SUPPLIER =
            "dev/architectury/registry/registries/RegistrySupplier";

    @Test
    void legacyFieldAliasesTheSharedSupplierWithoutDuplicateRegistration()
            throws IOException {
        List<String> aliasEvents = new ArrayList<>();
        AtomicInteger legacyIdLoads = new AtomicInteger();
        AtomicInteger legacyFoodComponentReferences = new AtomicInteger();
        AtomicInteger fieldAccess = new AtomicInteger(-1);
        ClassReader reader = classReader(E_ITEMS);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if (name.equals("FOREST_LANTERN_CRUMB")) {
                    fieldAccess.set(access);
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
                return new MethodVisitor(Opcodes.ASM9) {
                    private boolean recordingAlias;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value.equals("forest_lantern_crumb")) {
                            legacyIdLoads.incrementAndGet();
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String fieldName,
                            String fieldDescriptor
                    ) {
                        if (owner.equals(
                                "ru/feytox/etherology/registry/item/EFoodComponents"
                        )) {
                            legacyFoodComponentReferences.incrementAndGet();
                        }
                        if (name.equals("<clinit>")
                                && opcode == Opcodes.GETSTATIC
                                && owner.equals(SHARED_FOOD_ITEMS)
                                && fieldName.equals("FOREST_LANTERN_CRUMB")) {
                            recordingAlias = true;
                            aliasEvents.add("SharedFoodItems#FOREST_LANTERN_CRUMB");
                        }
                        if (recordingAlias
                                && opcode == Opcodes.PUTSTATIC
                                && owner.equals(
                                        "ru/feytox/etherology/registry/item/EItems"
                                )
                                && fieldName.equals("FOREST_LANTERN_CRUMB")) {
                            aliasEvents.add("EItems#FOREST_LANTERN_CRUMB");
                            recordingAlias = false;
                        }
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String invocationName,
                            String invocationDescriptor,
                            boolean isInterface
                    ) {
                        if (recordingAlias
                                && owner.equals(REGISTRY_SUPPLIER)
                                && invocationName.equals("get")) {
                            aliasEvents.add("RegistrySupplier#get");
                        }
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (recordingAlias
                                && opcode == Opcodes.CHECKCAST
                                && type.equals("net/minecraft/item/Item")) {
                            aliasEvents.add("CHECKCAST Item");
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fieldAccess.get()
        );
        assertEquals(
                List.of(
                        "SharedFoodItems#FOREST_LANTERN_CRUMB",
                        "RegistrySupplier#get",
                        "CHECKCAST Item",
                        "EItems#FOREST_LANTERN_CRUMB"
                ),
                aliasEvents
        );
        assertEquals(0, legacyIdLoads.get());
        assertEquals(0, legacyFoodComponentReferences.get());
        assertNull(FoodItemRegistryIsolationTest.class.getResource(
                "/ru/feytox/etherology/registry/item/EFoodComponents.class"
        ));
    }

    @Test
    void fabricAttachesTheSharedFoodRegistryBeforeLegacyItemsInitialize()
            throws IOException {
        Set<String> owners = Set.of(
                SHARED_FOOD_ITEMS,
                "ru/feytox/etherology/registry/item/EItems"
        );

        assertEquals(
                List.of(
                        SHARED_FOOD_ITEMS + "#register",
                        "ru/feytox/etherology/registry/item/EItems#registerItems"
                ),
                referencedMethods(ETHEROLOGY, "initialize", owners)
        );
    }

    private static List<String> referencedMethods(
            String resourceName,
            String methodName,
            Set<String> owners
    ) throws IOException {
        List<String> invocations = new ArrayList<>();
        InputStream classStream = FoodItemRegistryIsolationTest.class
                .getClassLoader()
                .getResourceAsStream(resourceName);
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
                            if (owners.contains(owner)) {
                                invocations.add(owner + "#" + name);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return invocations;
    }

    private static ClassReader classReader(String resourceName) throws IOException {
        InputStream classStream = FoodItemRegistryIsolationTest.class
                .getClassLoader()
                .getResourceAsStream(resourceName);
        assertNotNull(classStream);
        try (classStream) {
            return new ClassReader(classStream);
        }
    }
}
