package dev.theplumteam.etherology.baseline.fabric;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RenderLifecycleBytecodeTest {

    private static final String BASE_PACKAGE =
            "dev/theplumteam/etherology/baseline/fabric/";
    private static final String MIXIN = BASE_PACKAGE + "mixin/GameRendererMixin";
    private static final String HARNESS = BASE_PACKAGE + "OriginalPhaseZeroHarness";
    private static final String CONTROLLER = BASE_PACKAGE + "ScenarioController";
    private static final String MINECRAFT_CLIENT =
            "net/minecraft/client/MinecraftClient";
    private static final String INJECT_DESCRIPTOR =
            "Lorg/spongepowered/asm/mixin/injection/Inject;";

    @Test
    void gameRendererWrapsRenderingWithStartingAndCompletedHooks()
            throws IOException {
        Map<String, RenderHook> hooks = renderHooks();
        RenderHook starting = hooks.get(
                "etherologyOriginalBaseline$onRenderStarting"
        );
        RenderHook completed = hooks.get(
                "etherologyOriginalBaseline$onRenderCompleted"
        );

        assertEquals("render", starting.targetMethod);
        assertEquals("HEAD", starting.injectionPoint);
        assertEquals(
                List.of(new Invocation(HARNESS, "onGameRenderStarting")),
                starting.invocations
        );
        assertEquals("render", completed.targetMethod);
        assertEquals("TAIL", completed.injectionPoint);
        assertEquals(
                List.of(new Invocation(HARNESS, "onGameRenderCompleted")),
                completed.invocations
        );
    }

    @Test
    void publicHarnessRelaysBothRenderHooksToTheActiveController()
            throws IOException {
        List<Invocation> starting = methodInvocations(
                HARNESS,
                "onGameRenderStarting"
        );
        assertTrue(starting.contains(new Invocation(
                MINECRAFT_CLIENT,
                "getInstance"
        )));
        assertTrue(starting.contains(new Invocation(
                CONTROLLER,
                "onGameRenderStarting"
        )));

        List<Invocation> completed = methodInvocations(
                HARNESS,
                "onGameRenderCompleted"
        );
        assertTrue(completed.contains(new Invocation(
                CONTROLLER,
                "onGameRenderCompleted"
        )));
    }

    private static Map<String, RenderHook> renderHooks() throws IOException {
        Map<String, RenderHook> hooks = new LinkedHashMap<>();
        new ClassReader(classBytes(MIXIN)).accept(
                new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(
                            int access,
                            String name,
                            String descriptor,
                            String signature,
                            String[] exceptions
                    ) {
                        if (!name.startsWith("etherologyOriginalBaseline$")) {
                            return null;
                        }
                        RenderHook hook = new RenderHook();
                        hooks.put(name, hook);
                        return renderHookVisitor(hook);
                    }
                },
                ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
        );
        return hooks;
    }

    private static MethodVisitor renderHookVisitor(RenderHook hook) {
        return new MethodVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(
                    String descriptor,
                    boolean visible
            ) {
                if (!INJECT_DESCRIPTOR.equals(descriptor)) return null;
                return injectAnnotationVisitor(hook);
            }

            @Override
            public void visitMethodInsn(
                    int opcode,
                    String owner,
                    String name,
                    String descriptor,
                    boolean isInterface
            ) {
                hook.invocations.add(new Invocation(owner, name));
            }
        };
    }

    private static AnnotationVisitor injectAnnotationVisitor(RenderHook hook) {
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override
            public void visit(String name, Object value) {
                if ("method".equals(name)) {
                    hook.targetMethod = value.toString();
                }
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                if ("method".equals(name)) return methodArrayVisitor(hook);
                if ("at".equals(name)) return atArrayVisitor(hook);
                return null;
            }
        };
    }

    private static AnnotationVisitor methodArrayVisitor(RenderHook hook) {
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override
            public void visit(String name, Object value) {
                hook.targetMethod = value.toString();
            }
        };
    }

    private static AnnotationVisitor atArrayVisitor(RenderHook hook) {
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(
                    String name,
                    String descriptor
            ) {
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String name, Object value) {
                        if ("value".equals(name)) {
                            hook.injectionPoint = value.toString();
                        }
                    }
                };
            }
        };
    }

    private static List<Invocation> methodInvocations(
            String className,
            String methodName
    ) throws IOException {
        List<Invocation> invocations = new ArrayList<>();
        new ClassReader(classBytes(className)).accept(
                new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(
                            int access,
                            String name,
                            String descriptor,
                            String signature,
                            String[] exceptions
                    ) {
                        if (!methodName.equals(name)) return null;
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(
                                    int opcode,
                                    String owner,
                                    String name,
                                    String descriptor,
                                    boolean isInterface
                            ) {
                                invocations.add(new Invocation(owner, name));
                            }
                        };
                    }
                },
                ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
        );
        return invocations;
    }

    private static byte[] classBytes(String className) throws IOException {
        try (InputStream input = RenderLifecycleBytecodeTest.class
                .getClassLoader()
                .getResourceAsStream(className + ".class")) {
            if (input == null) throw new IOException("Class is missing: " + className);
            return input.readAllBytes();
        }
    }

    private static final class RenderHook {

        private String targetMethod;
        private String injectionPoint;
        private final List<Invocation> invocations = new ArrayList<>();
    }

    private record Invocation(String owner, String name) {
    }
}
