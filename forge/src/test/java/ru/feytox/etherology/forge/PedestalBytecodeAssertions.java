package ru.feytox.etherology.forge;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

final class PedestalBytecodeAssertions {

    private PedestalBytecodeAssertions() {
    }

    static ClassNode readClass(String resource) throws IOException {
        try (InputStream classStream = requiredResource(resource)) {
            ClassNode classNode = new ClassNode(Opcodes.ASM9);
            new ClassReader(classStream).accept(classNode, 0);
            return classNode;
        }
    }

    static byte[] readResource(String resource) throws IOException {
        try (InputStream input = requiredResource(resource)) {
            return input.readAllBytes();
        }
    }

    static String readTextResource(String resource) throws IOException {
        return new String(readResource(resource), StandardCharsets.UTF_8);
    }

    static String classConstants(String resource) throws IOException {
        return new String(readResource(resource), StandardCharsets.ISO_8859_1);
    }

    static List<URL> resourceLocations(String resource) throws IOException {
        Enumeration<URL> resources = PedestalBytecodeAssertions.class
                .getClassLoader()
                .getResources(resource);
        return Collections.list(resources);
    }

    static MethodNode requireMethod(
            ClassNode classNode,
            String name,
            String descriptor
    ) {
        return classNode.methods.stream()
                .filter(method -> method.name.equals(name))
                .filter(method -> method.desc.equals(descriptor))
                .findFirst()
                .orElseGet(() -> fail(
                        "Missing method " + classNode.name + "#" + name + descriptor
                ));
    }

    static AnnotationNode requireClassAnnotation(
            ClassNode classNode,
            String descriptor
    ) {
        return requireAnnotation(classAnnotations(classNode), descriptor);
    }

    static AnnotationNode requireMethodAnnotation(
            MethodNode method,
            String descriptor
    ) {
        return requireAnnotation(methodAnnotations(method), descriptor);
    }

    static Object annotationValue(AnnotationNode annotation, String name) {
        assertNotNull(annotation.values, annotation.desc);
        for (int index = 0; index < annotation.values.size(); index += 2) {
            if (annotation.values.get(index).equals(name)) {
                return annotation.values.get(index + 1);
            }
        }
        return fail("Missing annotation value " + annotation.desc + "." + name);
    }

    static List<String> calls(MethodNode method) {
        List<String> calls = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                calls.add(call.owner + "#" + call.name + call.desc);
            }
        }
        return calls;
    }

    static List<String> fieldAccesses(MethodNode method) {
        List<String> fields = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field) {
                fields.add(
                        field.owner + "#" + field.name + field.desc + "@" + field.getOpcode()
                );
            }
        }
        return fields;
    }

    static List<String> methodHandles(MethodNode method) {
        List<String> handles = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof InvokeDynamicInsnNode dynamicCall)) {
                continue;
            }
            for (Object argument : dynamicCall.bsmArgs) {
                if (argument instanceof Handle handle) {
                    handles.add(handle.getOwner() + "#" + handle.getName() + handle.getDesc());
                }
            }
        }
        return handles;
    }

    static List<String> stringConstants(MethodNode method) {
        List<String> constants = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LdcInsnNode constant
                    && constant.cst instanceof String text) {
                constants.add(text);
            }
        }
        return constants;
    }

    static int callIndex(
            MethodNode method,
            String owner,
            String name,
            String descriptor
    ) {
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(owner)
                    && call.name.equals(name)
                    && call.desc.equals(descriptor)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    static int lastCallIndex(
            MethodNode method,
            String owner,
            String name,
            String descriptor
    ) {
        int match = -1;
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(owner)
                    && call.name.equals(name)
                    && call.desc.equals(descriptor)) {
                match = index;
            }
            index++;
        }
        return match;
    }

    static int fieldAccessIndex(
            MethodNode method,
            String owner,
            String name,
            int opcode
    ) {
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && field.owner.equals(owner)
                    && field.name.equals(name)
                    && field.getOpcode() == opcode) {
                return index;
            }
            index++;
        }
        return -1;
    }

    static int typeInstructionIndex(
            MethodNode method,
            int opcode,
            String type
    ) {
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode typeInstruction
                    && typeInstruction.getOpcode() == opcode
                    && typeInstruction.desc.equals(type)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    static long countCalls(
            MethodNode method,
            String owner,
            String name,
            String descriptor
    ) {
        return calls(method).stream()
                .filter(call -> call.equals(owner + "#" + name + descriptor))
                .count();
    }

    static long countFieldAccesses(
            MethodNode method,
            String owner,
            String name,
            int opcode
    ) {
        long count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && field.owner.equals(owner)
                    && field.name.equals(name)
                    && field.getOpcode() == opcode) {
                count++;
            }
        }
        return count;
    }

    static int countOpcodes(MethodNode method, int opcode) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == opcode) {
                count++;
            }
        }
        return count;
    }

    static Path repositoryRoot() throws IOException {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path root = findRepositoryRoot(workingDirectory);
        if (root != null) {
            return root;
        }

        try {
            Path codeLocation = Path.of(
                    PedestalBytecodeAssertions.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );
            root = findRepositoryRoot(codeLocation);
            if (root != null) {
                return root;
            }
        } catch (URISyntaxException exception) {
            throw new IOException("Invalid Pedestal test code-source path", exception);
        }
        return fail("Could not locate the Etherology repository root");
    }

    static Path requireRegularFile(Path path) throws IOException {
        Path normalized = path.normalize();
        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            return fail("Missing regular file " + normalized);
        }
        if (Files.isSymbolicLink(normalized)) {
            return fail("Unexpected symbolic link " + normalized);
        }
        return normalized.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private static InputStream requiredResource(String resource) {
        InputStream input = PedestalBytecodeAssertions.class
                .getClassLoader()
                .getResourceAsStream(resource);
        assertNotNull(input, resource);
        return input;
    }

    private static List<AnnotationNode> classAnnotations(ClassNode classNode) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (classNode.visibleAnnotations != null) {
            annotations.addAll(classNode.visibleAnnotations);
        }
        if (classNode.invisibleAnnotations != null) {
            annotations.addAll(classNode.invisibleAnnotations);
        }
        return annotations;
    }

    private static List<AnnotationNode> methodAnnotations(MethodNode method) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.visibleAnnotations != null) {
            annotations.addAll(method.visibleAnnotations);
        }
        if (method.invisibleAnnotations != null) {
            annotations.addAll(method.invisibleAnnotations);
        }
        return annotations;
    }

    private static AnnotationNode requireAnnotation(
            List<AnnotationNode> annotations,
            String descriptor
    ) {
        return annotations.stream()
                .filter(annotation -> annotation.desc.equals(descriptor))
                .findFirst()
                .orElseGet(() -> fail("Missing annotation " + descriptor));
    }

    private static Path findRepositoryRoot(Path start) throws IOException {
        Path candidate = Files.isDirectory(start, LinkOption.NOFOLLOW_LINKS)
                ? start
                : start.getParent();
        while (candidate != null) {
            if (Files.exists(candidate.resolve(".git"), LinkOption.NOFOLLOW_LINKS)
                    && Files.isRegularFile(
                            candidate.resolve("settings.gradle.kts"),
                            LinkOption.NOFOLLOW_LINKS
                    )) {
                return candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
            }
            candidate = candidate.getParent();
        }
        return null;
    }
}
