package ru.feytox.etherology.block.pedestal;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class PedestalClassFile {

    private PedestalClassFile() {
    }

    static byte[] bytes(String className) throws IOException {
        try (InputStream stream = PedestalClassFile.class.getResourceAsStream(
                "/" + className + ".class"
        )) {
            assertNotNull(stream, "Missing class resource " + className);
            return stream.readAllBytes();
        }
    }

    static ClassShape shape(String className) throws IOException {
        AtomicInteger access = new AtomicInteger();
        List<String> superNames = new ArrayList<>();
        List<String> interfaces = new ArrayList<>();
        Map<String, FieldDefinition> fields = new LinkedHashMap<>();
        Map<String, MethodDefinition> methods = new LinkedHashMap<>();
        reader(className).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int classAccess,
                    String name,
                    String signature,
                    String superName,
                    String[] implementedInterfaces
            ) {
                access.set(classAccess);
                superNames.add(superName);
                interfaces.addAll(List.of(implementedInterfaces));
            }

            @Override
            public FieldVisitor visitField(
                    int fieldAccess,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                fields.put(
                        name,
                        new FieldDefinition(
                                fieldAccess,
                                descriptor,
                                signature,
                                value
                        )
                );
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
                methods.put(
                        name + descriptor,
                        new MethodDefinition(methodAccess, signature)
                );
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(1, superNames.size(), className);
        return new ClassShape(
                access.get(),
                superNames.get(0),
                interfaces,
                fields,
                methods
        );
    }

    static MethodTrace trace(String className, String methodName) throws IOException {
        List<String> matchingDescriptors = shape(className).methods().keySet().stream()
                .filter(method -> method.startsWith(methodName + "("))
                .map(method -> method.substring(methodName.length()))
                .toList();
        assertEquals(
                1,
                matchingDescriptors.size(),
                className + "#" + methodName + " is overloaded or absent"
        );
        return trace(className, methodName, matchingDescriptors.get(0));
    }

    static MethodTrace trace(
            String className,
            String methodName,
            String methodDescriptor
    ) throws IOException {
        List<MethodTrace> traces = new ArrayList<>();
        reader(className).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(methodName) || !descriptor.equals(methodDescriptor)) {
                    return null;
                }

                MethodTrace trace = new MethodTrace();
                traces.add(trace);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitInsn(int opcode) {
                        trace.opcodes.add(opcode);
                        Integer integer = integerConstant(opcode);
                        if (integer != null) {
                            trace.integerConstants.add(integer);
                        }
                        Double doubleValue = doubleConstant(opcode);
                        if (doubleValue != null) {
                            trace.doubleConstants.add(doubleValue);
                        }
                    }

                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        trace.opcodes.add(opcode);
                        if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                            trace.integerConstants.add(operand);
                        }
                    }

                    @Override
                    public void visitVarInsn(int opcode, int variable) {
                        trace.variableInstructions.add(
                                new VariableInstruction(opcode, variable)
                        );
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        trace.typeInstructions.add(new TypeInstruction(opcode, type));
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        trace.fieldInstructions.add(
                                new FieldInstruction(opcode, owner, name, descriptor)
                        );
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        trace.invocations.add(
                                new Invocation(opcode, owner, name, descriptor)
                        );
                    }

                    @Override
                    public void visitInvokeDynamicInsn(
                            String name,
                            String descriptor,
                            Handle bootstrapMethodHandle,
                            Object... bootstrapMethodArguments
                    ) {
                        List<Handle> handles = new ArrayList<>();
                        for (Object argument : bootstrapMethodArguments) {
                            if (argument instanceof Handle handle) {
                                handles.add(handle);
                            }
                        }
                        trace.dynamicInvocations.add(
                                new DynamicInvocation(name, descriptor, handles)
                        );
                    }

                    @Override
                    public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) {
                        trace.jumpOpcodes.add(opcode);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String stringValue) {
                            trace.stringConstants.add(stringValue);
                        } else if (value instanceof Integer integerValue) {
                            trace.integerConstants.add(integerValue);
                        } else if (value instanceof Double doubleValue) {
                            trace.doubleConstants.add(doubleValue);
                        } else if (value instanceof Float floatValue) {
                            trace.floatConstants.add(floatValue);
                        } else if (value instanceof Type typeValue) {
                            trace.typeConstants.add(typeValue.getInternalName());
                        }
                    }

                    @Override
                    public void visitIincInsn(int variable, int increment) {
                        trace.increments.add(new Increment(variable, increment));
                    }

                    @Override
                    public void visitTableSwitchInsn(
                            int minimum,
                            int maximum,
                            org.objectweb.asm.Label defaultLabel,
                            org.objectweb.asm.Label... labels
                    ) {
                        trace.tableSwitches.add(
                                new TableSwitch(minimum, maximum, labels.length)
                        );
                    }

                    @Override
                    public void visitLookupSwitchInsn(
                            org.objectweb.asm.Label defaultLabel,
                            int[] keys,
                            org.objectweb.asm.Label[] labels
                    ) {
                        trace.lookupSwitchKeys.add(List.of(
                                java.util.Arrays.stream(keys).boxed().toArray(Integer[]::new)
                        ));
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(
                1,
                traces.size(),
                "Missing method " + className + "#" + methodName + methodDescriptor
        );
        return traces.get(0);
    }

    private static ClassReader reader(String className) throws IOException {
        return new ClassReader(bytes(className));
    }

    private static Integer integerConstant(int opcode) {
        if (opcode == Opcodes.ICONST_M1) {
            return -1;
        }
        if (opcode >= Opcodes.ICONST_0 && opcode <= Opcodes.ICONST_5) {
            return opcode - Opcodes.ICONST_0;
        }
        return null;
    }

    private static Double doubleConstant(int opcode) {
        if (opcode == Opcodes.DCONST_0) {
            return 0.0;
        }
        if (opcode == Opcodes.DCONST_1) {
            return 1.0;
        }
        return null;
    }

    record ClassShape(
            int access,
            String superName,
            List<String> interfaces,
            Map<String, FieldDefinition> fields,
            Map<String, MethodDefinition> methods
    ) {
    }

    record FieldDefinition(
            int access,
            String descriptor,
            String signature,
            Object value
    ) {
    }

    record MethodDefinition(int access, String signature) {
    }

    record Invocation(int opcode, String owner, String name, String descriptor) {

        String qualifiedName() {
            return owner + "#" + name + descriptor;
        }
    }

    record FieldInstruction(int opcode, String owner, String name, String descriptor) {

        String qualifiedName() {
            return owner + "#" + name;
        }
    }

    record TypeInstruction(int opcode, String type) {
    }

    record VariableInstruction(int opcode, int variable) {
    }

    record DynamicInvocation(String name, String descriptor, List<Handle> handles) {
    }

    record Increment(int variable, int increment) {
    }

    record TableSwitch(int minimum, int maximum, int labelCount) {
    }

    static final class MethodTrace {

        private final List<Integer> opcodes = new ArrayList<>();
        private final List<Integer> integerConstants = new ArrayList<>();
        private final List<Double> doubleConstants = new ArrayList<>();
        private final List<Float> floatConstants = new ArrayList<>();
        private final List<String> stringConstants = new ArrayList<>();
        private final List<String> typeConstants = new ArrayList<>();
        private final List<Integer> jumpOpcodes = new ArrayList<>();
        private final List<List<Integer>> lookupSwitchKeys = new ArrayList<>();
        private final List<Invocation> invocations = new ArrayList<>();
        private final List<FieldInstruction> fieldInstructions = new ArrayList<>();
        private final List<TypeInstruction> typeInstructions = new ArrayList<>();
        private final List<VariableInstruction> variableInstructions = new ArrayList<>();
        private final List<DynamicInvocation> dynamicInvocations = new ArrayList<>();
        private final List<Increment> increments = new ArrayList<>();
        private final List<TableSwitch> tableSwitches = new ArrayList<>();

        List<Integer> opcodes() {
            return List.copyOf(opcodes);
        }

        List<Integer> integerConstants() {
            return List.copyOf(integerConstants);
        }

        List<Double> doubleConstants() {
            return List.copyOf(doubleConstants);
        }

        List<Float> floatConstants() {
            return List.copyOf(floatConstants);
        }

        List<String> stringConstants() {
            return List.copyOf(stringConstants);
        }

        List<String> typeConstants() {
            return List.copyOf(typeConstants);
        }

        List<Integer> jumpOpcodes() {
            return List.copyOf(jumpOpcodes);
        }

        List<List<Integer>> lookupSwitchKeys() {
            return List.copyOf(lookupSwitchKeys);
        }

        List<Invocation> invocations() {
            return List.copyOf(invocations);
        }

        List<FieldInstruction> fieldInstructions() {
            return List.copyOf(fieldInstructions);
        }

        List<TypeInstruction> typeInstructions() {
            return List.copyOf(typeInstructions);
        }

        List<VariableInstruction> variableInstructions() {
            return List.copyOf(variableInstructions);
        }

        List<DynamicInvocation> dynamicInvocations() {
            return List.copyOf(dynamicInvocations);
        }

        List<Increment> increments() {
            return List.copyOf(increments);
        }

        List<TableSwitch> tableSwitches() {
            return List.copyOf(tableSwitches);
        }
    }
}
