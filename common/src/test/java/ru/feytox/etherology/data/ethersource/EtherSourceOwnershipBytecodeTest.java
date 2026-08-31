package ru.feytox.etherology.data.ethersource;

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

final class EtherSourceOwnershipBytecodeTest {

    private static final String ETHER_SOURCES =
            "ru/feytox/etherology/data/ethersource/EtherSources";
    private static final String ETHER_SOURCE_LOADER =
            "ru/feytox/etherology/data/ethersource/EtherSourceLoader";
    private static final String DESERIALIZER =
            "ru/feytox/etherology/data/ethersource/EtherSourcesDeserializer";
    private static final String RESOURCE_RELOADERS =
            "ru/feytox/etherology/registry/misc/ResourceReloaders";
    private static final String BOOTSTRAP =
            "ru/feytox/etherology/bootstrap/EtherologyBootstrap";
    private static final String IDENTIFIER_DESCRIPTOR =
            "Lnet/minecraft/util/Identifier;";
    private static final String LOADER_DESCRIPTOR =
            "L" + ETHER_SOURCE_LOADER + ";";

    @Test
    void keepsOnlyTheConsumerFacingEtherSourceQueriesPublic() throws IOException {
        ClassShape shape = shape(ETHER_SOURCES);

        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                shape.access()
        );
        assertEquals(Map.of(), shape.fields());
        assertEquals(
                Map.of(
                        "<init>()V", Opcodes.ACC_PRIVATE,
                        "getEtherFuel(Lnet/minecraft/item/Item;)F",
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                        "isEtherSource(Lnet/minecraft/item/Item;)Z",
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
                ),
                shape.methods()
        );
    }

    @Test
    void keepsLoaderStatePrivateAndDeserializationPackageInternal() throws IOException {
        ClassShape loader = shape(ETHER_SOURCE_LOADER);
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                loader.access()
        );
        assertEquals(
                Map.of(
                        "LOGGER", Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        "GSON", Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        "INSTANCE", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        "etherItems", Opcodes.ACC_PRIVATE,
                        "loaded", Opcodes.ACC_PRIVATE
                ),
                loader.fields()
        );
        assertEquals(LOADER_DESCRIPTOR, loader.fieldDescriptors().get("INSTANCE"));
        assertEquals(
                "Ljava/util/Map<Lnet/minecraft/util/Identifier;Ljava/lang/Float;>;",
                loader.fieldSignatures().get("etherItems")
        );
        assertEquals(Opcodes.ACC_PRIVATE, loader.methods().get("<init>()V"));
        assertEquals(0, loader.methods().get("getEtherItems()Ljava/util/Map;"));
        assertEquals(
                "()Ljava/util/Map<Lnet/minecraft/util/Identifier;Ljava/lang/Float;>;",
                loader.methodSignatures().get("getEtherItems()Ljava/util/Map;")
        );
        assertEquals(
                Opcodes.ACC_PROTECTED,
                loader.methods().get(
                        "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;"
                                + "Lnet/minecraft/util/profiler/Profiler;)V"
                )
        );
        assertEquals(List.of(), loader.publicMethods());

        ClassShape deserializer = shape(DESERIALIZER);
        assertEquals(
                Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                deserializer.access()
        );
        assertEquals(Map.of(), deserializer.fields());
        assertEquals(
                Map.of(
                        "<init>()V", Opcodes.ACC_PRIVATE,
                        "deserialize(Lcom/google/gson/JsonElement;)Ljava/util/Map;",
                        Opcodes.ACC_STATIC,
                        "lambda$deserialize$0(Ljava/util/Map;Ljava/lang/String;"
                                + "Lcom/google/gson/JsonElement;)V",
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC
                ),
                deserializer.methods()
        );
        assertEquals(
                "(Lcom/google/gson/JsonElement;)"
                        + "Ljava/util/Map<Lnet/minecraft/util/Identifier;Ljava/lang/Float;>;",
                deserializer.methodSignatures().get(
                        "deserialize(Lcom/google/gson/JsonElement;)Ljava/util/Map;"
                )
        );
        assertEquals(List.of(), deserializer.publicMethods());
    }

    @Test
    void registersTheExactServerDataListenerOnceAfterSharedRegistries() throws IOException {
        ClassShape reloaders = shape(RESOURCE_RELOADERS);
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                reloaders.access()
        );
        assertEquals(
                Map.of(
                        "ETHER_SOURCES_ID",
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        "registered", Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC
                ),
                reloaders.fields()
        );
        assertEquals(
                IDENTIFIER_DESCRIPTOR,
                reloaders.fieldDescriptors().get("ETHER_SOURCES_ID")
        );
        assertEquals(Opcodes.ACC_PRIVATE, reloaders.methods().get("<init>()V"));
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED,
                reloaders.methods().get("registerServerData()V")
        );
        assertEquals(List.of("registerServerData()V"), reloaders.publicMethods());

        Map<String, List<String>> invocations = invocationsByMethod(RESOURCE_RELOADERS);
        assertEquals(
                List.of(
                        "net/minecraft/util/Identifier#of(Ljava/lang/String;Ljava/lang/String;)"
                                + IDENTIFIER_DESCRIPTOR
                ),
                invocations.get("<clinit>()V")
        );
        assertEquals(
                List.of(
                        "dev/architectury/registry/ReloadListenerRegistry#register"
                                + "(Lnet/minecraft/resource/ResourceType;"
                                + "Lnet/minecraft/resource/ResourceReloader;"
                                + IDENTIFIER_DESCRIPTOR + ")V"
                ),
                invocations.get("registerServerData()V")
        );
        assertEquals(
                List.of("etherology", "ether_sources"),
                stringsByMethod(RESOURCE_RELOADERS).get("<clinit>()V")
        );

        List<String> fieldEvents = fieldEventsByMethod(RESOURCE_RELOADERS).get(
                "registerServerData()V"
        );
        assertEquals(
                List.of(
                        "GETSTATIC " + RESOURCE_RELOADERS + "#registered:Z",
                        "GETSTATIC net/minecraft/resource/ResourceType#SERVER_DATA:"
                                + "Lnet/minecraft/resource/ResourceType;",
                        "GETSTATIC " + ETHER_SOURCE_LOADER + "#INSTANCE:"
                                + LOADER_DESCRIPTOR,
                        "GETSTATIC " + RESOURCE_RELOADERS + "#ETHER_SOURCES_ID:"
                                + IDENTIFIER_DESCRIPTOR,
                        "PUTSTATIC " + RESOURCE_RELOADERS + "#registered:Z"
                ),
                fieldEvents
        );

        List<String> bootstrapInvocations = invocationsByMethod(BOOTSTRAP).get(
                "initialize(Lru/feytox/etherology/bootstrap/PlatformRegistrar;)V"
        );
        int lootConditions = bootstrapInvocations.indexOf(
                "ru/feytox/etherology/registry/misc/SharedLootConditions#register()V"
        );
        int resourceReloaders = bootstrapInvocations.indexOf(
                RESOURCE_RELOADERS + "#registerServerData()V"
        );
        int lifecycle = bootstrapInvocations.indexOf(
                "ru/feytox/etherology/bootstrap/BootstrapLifecycle#initialize"
                        + "(Lru/feytox/etherology/bootstrap/PlatformRegistrar;)V"
        );

        assertTrue(lootConditions >= 0);
        assertEquals(lootConditions + 1, resourceReloaders);
        assertEquals(resourceReloaders + 1, lifecycle);
        assertEquals(1, count(
                bootstrapInvocations,
                RESOURCE_RELOADERS + "#registerServerData()V"
        ));
    }

    private static ClassShape shape(String className) throws IOException {
        AtomicInteger access = new AtomicInteger();
        Map<String, Integer> fields = new LinkedHashMap<>();
        Map<String, String> fieldDescriptors = new LinkedHashMap<>();
        Map<String, String> fieldSignatures = new LinkedHashMap<>();
        Map<String, Integer> methods = new LinkedHashMap<>();
        Map<String, String> methodSignatures = new LinkedHashMap<>();
        List<String> publicMethods = new ArrayList<>();
        reader(className).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int classAccess,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces
            ) {
                access.set(classAccess);
            }

            @Override
            public FieldVisitor visitField(
                    int fieldAccess,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                fields.put(name, fieldAccess);
                fieldDescriptors.put(name, descriptor);
                fieldSignatures.put(name, signature);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(
                    int methodAccess,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                String key = name + descriptor;
                methods.put(key, methodAccess);
                methodSignatures.put(key, signature);
                if ((methodAccess & Opcodes.ACC_PUBLIC) != 0) {
                    publicMethods.add(key);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new ClassShape(
                access.get(),
                fields,
                fieldDescriptors,
                fieldSignatures,
                methods,
                methodSignatures,
                publicMethods
        );
    }

    private static Map<String, List<String>> invocationsByMethod(String className)
            throws IOException {
        Map<String, List<String>> invocations = new LinkedHashMap<>();
        reader(className).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                List<String> methodInvocations = new ArrayList<>();
                invocations.put(name + descriptor, methodInvocations);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String methodName,
                            String methodDescriptor,
                            boolean isInterface
                    ) {
                        methodInvocations.add(
                                owner + "#" + methodName + methodDescriptor
                        );
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return invocations;
    }

    private static Map<String, List<String>> stringsByMethod(String className)
            throws IOException {
        Map<String, List<String>> strings = new LinkedHashMap<>();
        reader(className).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                List<String> methodStrings = new ArrayList<>();
                strings.put(name + descriptor, methodStrings);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String stringValue) {
                            methodStrings.add(stringValue);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return strings;
    }

    private static Map<String, List<String>> fieldEventsByMethod(String className)
            throws IOException {
        Map<String, List<String>> fieldEvents = new LinkedHashMap<>();
        reader(className).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                List<String> methodFieldEvents = new ArrayList<>();
                fieldEvents.put(name + descriptor, methodFieldEvents);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String fieldName,
                            String fieldDescriptor
                    ) {
                        methodFieldEvents.add(
                                opcodeName(opcode) + " " + owner + "#" + fieldName
                                        + ":" + fieldDescriptor
                        );
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return fieldEvents;
    }

    private static int count(List<String> values, String expected) {
        int matches = 0;
        for (String value : values) {
            if (value.equals(expected)) {
                matches++;
            }
        }
        return matches;
    }

    private static String opcodeName(int opcode) {
        return switch (opcode) {
            case Opcodes.GETSTATIC -> "GETSTATIC";
            case Opcodes.PUTSTATIC -> "PUTSTATIC";
            case Opcodes.GETFIELD -> "GETFIELD";
            case Opcodes.PUTFIELD -> "PUTFIELD";
            default -> throw new IllegalArgumentException("Unexpected field opcode " + opcode);
        };
    }

    private static ClassReader reader(String className) throws IOException {
        String resource = "/" + className + ".class";
        InputStream stream = EtherSourceOwnershipBytecodeTest.class.getResourceAsStream(
                resource
        );
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return new ClassReader(stream);
        }
    }

    private record ClassShape(
            int access,
            Map<String, Integer> fields,
            Map<String, String> fieldDescriptors,
            Map<String, String> fieldSignatures,
            Map<String, Integer> methods,
            Map<String, String> methodSignatures,
            List<String> publicMethods
    ) {
    }
}
