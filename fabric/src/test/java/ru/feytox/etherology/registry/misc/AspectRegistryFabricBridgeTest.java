package ru.feytox.etherology.registry.misc;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AspectRegistryFabricBridgeTest {

    private static final String LEGACY_REGISTRIES =
            "ru/feytox/etherology/registry/misc/RegistriesRegistry";
    private static final String SHARED_REGISTRIES =
            "ru/feytox/etherology/registry/misc/SharedAspectRegistries";
    private static final String REGISTRY_PART =
            "ru/feytox/etherology/magic/aspects/AspectRegistryPart";

    @Test
    void legacyFieldAliasesSharedKeyAndBootstrapKeepsSyncedReloadSemantics()
            throws IOException {
        ClassTrace trace = trace(LEGACY_REGISTRIES);
        assertTrue(trace.fieldEvents().contains(
                "GETSTATIC " + SHARED_REGISTRIES + "#ASPECTS"
        ));
        assertTrue(trace.fieldEvents().contains(
                "PUTSTATIC " + LEGACY_REGISTRIES + "#ASPECTS"
        ));

        List<String> registerCalls = trace.invocations("registerAll");
        assertEquals(
                3,
                registerCalls.stream().filter(call -> call.equals(
                        "net/fabricmc/fabric/api/event/registry/"
                                + "DynamicRegistries#registerSynced"
                )).count()
        );
        assertTrue(trace.fieldEvents("registerAll").contains(
                "GETSTATIC net/fabricmc/fabric/api/event/lifecycle/v1/"
                        + "ServerLifecycleEvents#START_DATA_PACK_RELOAD"
        ));
        assertTrue(registerCalls.contains(
                "net/fabricmc/fabric/api/event/Event#register"
        ));

        assertTrue(trace.fieldEvents("registerAll").contains(
                "GETSTATIC " + LEGACY_REGISTRIES + "#ASPECTS"
        ));
        assertTrue(trace.fieldEvents("registerAll").contains(
                "GETSTATIC " + REGISTRY_PART + "#CODEC"
        ));
        assertTrue(trace.invocations("lambda$registerAll$0").contains(
                "ru/feytox/etherology/data/aspects/AspectsLoader#clearCache"
        ));
    }

    @Test
    void canonicalFabricInitializerStillAttachesTheLegacyRegistryBridge()
            throws IOException {
        ClassTrace trace = trace("ru/feytox/etherology/Etherology");
        List<String> calls = trace.invocations("initialize");
        assertEquals(
                1,
                calls.stream().filter(call -> call.equals(
                        LEGACY_REGISTRIES + "#registerAll"
                )).count()
        );
    }

    private static ClassTrace trace(String className) throws IOException {
        List<String> allFieldEvents = new ArrayList<>();
        java.util.Map<String, List<String>> methodFieldEvents =
                new java.util.LinkedHashMap<>();
        java.util.Map<String, List<String>> invocations =
                new java.util.LinkedHashMap<>();
        try (InputStream stream = AspectRegistryFabricBridgeTest.class
                .getResourceAsStream("/" + className + ".class")) {
            assertNotNull(stream, className);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        Object value
                ) {
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
                    List<String> fields = methodFieldEvents.computeIfAbsent(
                            name,
                            ignored -> new ArrayList<>()
                    );
                    List<String> calls = invocations.computeIfAbsent(
                            name,
                            ignored -> new ArrayList<>()
                    );
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitFieldInsn(
                                int opcode,
                                String owner,
                                String fieldName,
                                String fieldDescriptor
                        ) {
                            String event = opcodeName(opcode) + " " + owner
                                    + "#" + fieldName;
                            fields.add(event);
                            allFieldEvents.add(event);
                        }

                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String calledName,
                                String calledDescriptor,
                                boolean isInterface
                        ) {
                            calls.add(owner + "#" + calledName);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return new ClassTrace(allFieldEvents, methodFieldEvents, invocations);
    }

    private static String opcodeName(int opcode) {
        return switch (opcode) {
            case Opcodes.GETSTATIC -> "GETSTATIC";
            case Opcodes.PUTSTATIC -> "PUTSTATIC";
            case Opcodes.GETFIELD -> "GETFIELD";
            case Opcodes.PUTFIELD -> "PUTFIELD";
            default -> Integer.toString(opcode);
        };
    }

    private record ClassTrace(
            List<String> fieldEvents,
            java.util.Map<String, List<String>> methodFieldEvents,
            java.util.Map<String, List<String>> methodInvocations
    ) {

        private List<String> fieldEvents(String method) {
            return methodFieldEvents.getOrDefault(method, List.of());
        }

        private List<String> invocations(String method) {
            return methodInvocations.getOrDefault(method, List.of());
        }
    }
}
