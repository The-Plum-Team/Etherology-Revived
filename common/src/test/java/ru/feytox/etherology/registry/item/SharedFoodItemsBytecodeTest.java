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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SharedFoodItemsBytecodeTest {

    private static final String FOOD_COMPONENTS_CLASS =
            "/ru/feytox/etherology/registry/item/SharedFoodComponents.class";
    private static final String FOOD_ITEMS_CLASS =
            "/ru/feytox/etherology/registry/item/SharedFoodItems.class";
    private static final String BOOTSTRAP_CLASS =
            "/ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String FOOD_COMPONENTS_OWNER =
            "ru/feytox/etherology/registry/item/SharedFoodComponents";
    private static final String FOOD_ITEMS_OWNER =
            "ru/feytox/etherology/registry/item/SharedFoodItems";
    private static final String SHARED_DEFERRED_REGISTER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String REGISTRY_SUPPLIER =
            "dev/architectury/registry/registries/RegistrySupplier";

    @Test
    void definesTheExactForestLanternCrumbNutrition() throws IOException {
        AtomicInteger classAccess = new AtomicInteger();
        List<FieldInfo> fields = new ArrayList<>();
        ClassReader reader = classReader(FOOD_COMPONENTS_CLASS);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces
            ) {
                classAccess.set(access);
            }

            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                fields.add(new FieldInfo(access, name, descriptor, signature));
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue((classAccess.get() & Opcodes.ACC_PUBLIC) != 0);
        assertTrue((classAccess.get() & Opcodes.ACC_FINAL) != 0);
        assertEquals(
                List.of(new FieldInfo(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        "FOREST_LANTERN_CRUMB",
                        "Lnet/minecraft/item/FoodComponent;",
                        null
                )),
                fields
        );
        assertEquals(
                List.of(
                        "NEW FoodComponent$Builder",
                        "FoodComponent$Builder#<init>",
                        "INT:3",
                        "FoodComponent$Builder#hunger",
                        "FLOAT:2.0",
                        "FoodComponent$Builder#saturationModifier",
                        "FoodComponent$Builder#build",
                        "SharedFoodComponents#FOREST_LANTERN_CRUMB:PUTSTATIC"
                ),
                relevantEventsByMethod(FOOD_COMPONENTS_CLASS).get("<clinit>()V")
        );
    }

    @Test
    void declaresOneLazyVanillaFoodItemWithDefaultSettings() throws IOException {
        AtomicInteger classAccess = new AtomicInteger();
        List<FieldInfo> fields = new ArrayList<>();
        Map<String, String> registrations = new LinkedHashMap<>();
        AtomicInteger supplierGets = new AtomicInteger();
        AtomicInteger deferredRegistrations = new AtomicInteger();
        ClassReader reader = classReader(FOOD_ITEMS_CLASS);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces
            ) {
                classAccess.set(access);
            }

            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                fields.add(new FieldInfo(access, name, descriptor, signature));
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
                    private String pendingId;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (name.equals("<clinit>") && value instanceof String id) {
                            pendingId = id;
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String fieldName,
                            String fieldDescriptor
                    ) {
                        if (name.equals("<clinit>")
                                && opcode == Opcodes.PUTSTATIC
                                && owner.equals(FOOD_ITEMS_OWNER)
                                && pendingId != null) {
                            registrations.put(fieldName, pendingId);
                            pendingId = null;
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
                        if (owner.equals(REGISTRY_SUPPLIER)
                                && invocationName.equals("get")) {
                            supplierGets.incrementAndGet();
                        }
                        if (owner.equals(SHARED_DEFERRED_REGISTER)
                                && invocationName.equals("register")) {
                            deferredRegistrations.incrementAndGet();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue((classAccess.get() & Opcodes.ACC_PUBLIC) != 0);
        assertTrue((classAccess.get() & Opcodes.ACC_FINAL) != 0);
        assertEquals(expectedFoodItemFields(), fields);
        assertEquals(Map.of("FOREST_LANTERN_CRUMB", "forest_lantern_crumb"), registrations);
        assertEquals(1, deferredRegistrations.get());
        assertEquals(0, supplierGets.get());

        List<String> factoryEvents = relevantEventsByMethod(FOOD_ITEMS_CLASS)
                .values()
                .stream()
                .filter(events -> events.contains("Item#<init>"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                List.of(
                        "NEW Item",
                        "NEW Item$Settings",
                        "Item$Settings#<init>",
                        "SharedFoodComponents#FOREST_LANTERN_CRUMB:GETSTATIC",
                        "Item$Settings#food",
                        "Item#<init>"
                ),
                factoryEvents
        );
    }

    @Test
    void attachesOnceAfterMaterialsAndBeforeBlockEntities() throws IOException {
        List<String> bootstrapInvocations = methodInvocations(
                BOOTSTRAP_CLASS,
                "initialize"
        );
        String materialRegistration =
                "ru/feytox/etherology/registry/item/SharedMaterialItems#register()V";
        String foodRegistration = FOOD_ITEMS_OWNER + "#register()V";
        String blockEntityRegistration =
                "ru/feytox/etherology/registry/block/SharedBlockEntities#register()V";
        int foodIndex = bootstrapInvocations.indexOf(foodRegistration);

        assertTrue(foodIndex > 0);
        assertEquals(materialRegistration, bootstrapInvocations.get(foodIndex - 1));
        assertEquals(blockEntityRegistration, bootstrapInvocations.get(foodIndex + 1));
        assertEquals(
                1,
                bootstrapInvocations.stream()
                        .filter(foodRegistration::equals)
                        .count()
        );
        assertEquals(
                List.of(SHARED_DEFERRED_REGISTER + "#attach()V"),
                methodInvocations(FOOD_ITEMS_CLASS, "register")
        );
    }

    private static List<FieldInfo> expectedFoodItemFields() {
        return List.of(
                new FieldInfo(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        "ITEMS",
                        "Lru/feytox/etherology/registry/SharedDeferredRegister;",
                        "Lru/feytox/etherology/registry/SharedDeferredRegister"
                                + "<Lnet/minecraft/item/Item;>;"
                ),
                new FieldInfo(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        "FOREST_LANTERN_CRUMB",
                        "Ldev/architectury/registry/registries/RegistrySupplier;",
                        "Ldev/architectury/registry/registries/RegistrySupplier"
                                + "<Lnet/minecraft/item/Item;>;"
                )
        );
    }

    private static Map<String, List<String>> relevantEventsByMethod(String resource)
            throws IOException {
        Map<String, List<String>> eventsByMethod = new LinkedHashMap<>();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                List<String> events = new ArrayList<>();
                eventsByMethod.put(name + descriptor, events);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW
                                && type.equals("net/minecraft/item/FoodComponent$Builder")) {
                            events.add("NEW FoodComponent$Builder");
                        }
                        if (opcode == Opcodes.NEW && type.equals("net/minecraft/item/Item")) {
                            events.add("NEW Item");
                        }
                        if (opcode == Opcodes.NEW
                                && type.equals("net/minecraft/item/Item$Settings")) {
                            events.add("NEW Item$Settings");
                        }
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.ICONST_3) {
                            events.add("INT:3");
                        }
                        if (opcode == Opcodes.FCONST_2) {
                            events.add("FLOAT:2.0");
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (owner.equals(FOOD_COMPONENTS_OWNER)
                                && name.equals("FOREST_LANTERN_CRUMB")) {
                            String operation = opcode == Opcodes.PUTSTATIC
                                    ? "PUTSTATIC"
                                    : "GETSTATIC";
                            events.add(
                                    "SharedFoodComponents#FOREST_LANTERN_CRUMB:"
                                            + operation
                            );
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
                        if (owner.equals("net/minecraft/item/FoodComponent$Builder")) {
                            events.add("FoodComponent$Builder#" + name);
                        }
                        if (owner.equals("net/minecraft/item/Item$Settings")) {
                            events.add("Item$Settings#" + name);
                        }
                        if (owner.equals("net/minecraft/item/Item")
                                && name.equals("<init>")) {
                            events.add("Item#<init>");
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return eventsByMethod;
    }

    private static List<String> methodInvocations(String resource, String methodName)
            throws IOException {
        List<String> invocations = new ArrayList<>();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
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
                        invocations.add(owner + "#" + name + descriptor);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return invocations;
    }

    private static ClassReader classReader(String resource) throws IOException {
        InputStream stream = SharedFoodItemsBytecodeTest.class.getResourceAsStream(resource);
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return new ClassReader(stream);
        }
    }

    private record FieldInfo(
            int access,
            String name,
            String descriptor,
            String signature
    ) {
    }
}
