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

final class SharedMaterialItemsBytecodeTest {

    private static final String SHARED_MATERIAL_ITEMS =
            "/ru/feytox/etherology/registry/item/SharedMaterialItems.class";
    private static final String BOOTSTRAP =
            "/ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String OWNER =
            "ru/feytox/etherology/registry/item/SharedMaterialItems";
    private static final String REGISTRY_SUPPLIER =
            "dev/architectury/registry/registries/RegistrySupplier";

    private static final Map<String, String> EXACT_ITEMS = exactItems();

    @Test
    void declaresExactlyTheFourteenMaterialItemsWithoutEagerResolution()
            throws IOException {
        ClassReader reader = classReader(SHARED_MATERIAL_ITEMS);
        List<FieldInfo> fields = new ArrayList<>();
        Map<String, String> registrations = new LinkedHashMap<>();
        AtomicInteger supplierGets = new AtomicInteger();
        AtomicInteger maxCountCalls = new AtomicInteger();
        AtomicInteger maxCountValue = new AtomicInteger(-1);
        AtomicInteger classAccess = new AtomicInteger();

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
                    private int pendingInteger = -1;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (name.equals("<clinit>") && value instanceof String id) {
                            pendingId = id;
                        }
                    }

                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        pendingInteger = operand;
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
                                && owner.equals(OWNER)
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
                        if (owner.equals("net/minecraft/item/Item$Settings")
                                && invocationName.equals("maxCount")) {
                            maxCountCalls.incrementAndGet();
                            maxCountValue.set(pendingInteger);
                        }
                        pendingInteger = -1;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue((classAccess.get() & Opcodes.ACC_PUBLIC) != 0);
        assertTrue((classAccess.get() & Opcodes.ACC_FINAL) != 0);
        assertEquals(expectedFields(), fields);
        assertEquals(EXACT_ITEMS, registrations);
        assertEquals(0, supplierGets.get());
        assertEquals(1, maxCountCalls.get());
        assertEquals(16, maxCountValue.get());
    }

    @Test
    void attachesBetweenTheOtherSharedItemAndBlockEntityOwners()
            throws IOException {
        List<String> invocations = methodInvocations(
                classReader(BOOTSTRAP),
                "initialize"
        );
        int materialIndex = invocations.indexOf(OWNER + "#register()V");

        assertTrue(materialIndex > 0);
        assertEquals(
                "ru/feytox/etherology/registry/item/SharedItems#register()V",
                invocations.get(materialIndex - 1)
        );
        assertEquals(
                "ru/feytox/etherology/registry/block/SharedBlockEntities#register()V",
                invocations.get(materialIndex + 1)
        );
        assertEquals(1, invocations.stream()
                .filter(invocation -> invocation.equals(OWNER + "#register()V"))
                .count());
        assertEquals(
                List.of(
                        "ru/feytox/etherology/registry/SharedDeferredRegister#attach()V"
                ),
                methodInvocations(classReader(SHARED_MATERIAL_ITEMS), "register")
        );
    }

    private static Map<String, String> exactItems() {
        Map<String, String> items = new LinkedHashMap<>();
        items.put("ETHEROSCOPE", "etheroscope");
        items.put("THUJA_OIL", "thuja_oil");
        items.put("AZEL_INGOT", "azel_ingot");
        items.put("AZEL_NUGGET", "azel_nugget");
        items.put("ETHRIL_INGOT", "ethril_ingot");
        items.put("ETHRIL_NUGGET", "ethril_nugget");
        items.put("EBONY_INGOT", "ebony_ingot");
        items.put("EBONY_NUGGET", "ebony_nugget");
        items.put("ENRICHED_ATTRAHITE", "enriched_attrahite");
        items.put("RAW_AZEL", "raw_azel");
        items.put("ATTRAHITE_BRICK", "attrahite_brick");
        items.put("BINDER", "binder");
        items.put("EBONY", "ebony");
        items.put("RESONATING_WAND", "resonating_wand");
        return items;
    }

    private static List<FieldInfo> expectedFields() {
        List<FieldInfo> fields = new ArrayList<>();
        fields.add(new FieldInfo(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "ITEMS",
                "Lru/feytox/etherology/registry/SharedDeferredRegister;",
                "Lru/feytox/etherology/registry/SharedDeferredRegister"
                        + "<Lnet/minecraft/item/Item;>;"
        ));
        EXACT_ITEMS.keySet().forEach(name -> fields.add(new FieldInfo(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                name,
                "Ldev/architectury/registry/registries/RegistrySupplier;",
                "Ldev/architectury/registry/registries/RegistrySupplier"
                        + "<Lnet/minecraft/item/Item;>;"
        )));
        return fields;
    }

    private static ClassReader classReader(String resource) throws IOException {
        InputStream stream = SharedMaterialItemsBytecodeTest.class
                .getResourceAsStream(resource);
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return new ClassReader(stream);
        }
    }

    private static List<String> methodInvocations(
            ClassReader reader,
            String methodName
    ) {
        List<String> invocations = new ArrayList<>();
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
                        invocations.add(owner + "#" + name + descriptor);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return invocations;
    }

    private record FieldInfo(
            int access,
            String name,
            String descriptor,
            String signature
    ) {
    }
}
