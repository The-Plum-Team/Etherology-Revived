package ru.feytox.etherology.recipes;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FabricRecipeResultComponentBackendTest {

    private static final String BACKEND_RESOURCE =
            "/ru/feytox/etherology/recipes/FabricRecipeResultComponentBackend.class";
    private static final String SHARED_SERIALIZER_RESOURCE =
            "ru/feytox/etherology/recipes/FeyRecipeSerializer.class";
    private static final String SHARED_ALCHEMY_SERIALIZER_RESOURCE =
            "ru/feytox/etherology/recipes/alchemy/AlchemyRecipeSerializer.class";
    private static final String LEGACY_MATRIX_SERIALIZER_RESOURCE =
            "/ru/feytox/etherology/recipes/matrix/MatrixRecipeSerializer.class";

    @Test
    void unsupportedComponentsRemainVisible() {
        FabricRecipeResultComponentBackend backend =
                FabricRecipeResultComponentBackend.INSTANCE;

        JsonObject unsupported = new JsonObject();
        unsupported.addProperty("minecraft:custom_name", "name");
        JsonSyntaxException unsupportedException = assertThrows(
                JsonSyntaxException.class,
                () -> backend.readComponents(unsupported, null)
        );
        assertEquals(
                "Unsupported 1.20.1 recipe result component 'minecraft:custom_name'",
                unsupportedException.getMessage()
        );
    }

    @Test
    void adapterDelegatesOnlyStaffCodecAndItemDataInBothDirections()
            throws IOException {
        List<String> readOperations = operations("readComponents");
        assertTrue(readOperations.contains(
                "GETSTATIC:ru/feytox/etherology/magic/staff/StaffComponent#CODEC"
        ));
        assertTrue(readOperations.contains(
                "GETSTATIC:ru/feytox/etherology/registry/misc/ComponentTypes#STAFF"
        ));
        assertTrue(readOperations.contains(
                "ru/feytox/etherology/util/misc/ItemDataKey#set"
        ));

        List<String> writeOperations = operations("writeComponents");
        assertTrue(writeOperations.contains(
                "GETSTATIC:ru/feytox/etherology/registry/misc/ComponentTypes#STAFF"
        ));
        assertTrue(writeOperations.contains(
                "ru/feytox/etherology/util/misc/ItemDataKey#get"
        ));
    }

    @Test
    void adapterIsOneSingletonWithOnlyTheNarrowSharedContract()
            throws IOException {
        List<String> interfaces = new ArrayList<>();
        int[] singletonAccess = {-1};
        reader(BACKEND_RESOURCE).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] implementedInterfaces
            ) {
                if (implementedInterfaces != null) {
                    interfaces.addAll(List.of(implementedInterfaces));
                }
            }

            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if (name.equals("INSTANCE")) singletonAccess[0] = access;
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG
                | ClassReader.SKIP_FRAMES);

        assertEquals(
                List.of("ru/feytox/etherology/recipes/RecipeResultComponentBackend"),
                interfaces
        );
        int requiredAccess = Opcodes.ACC_PUBLIC
                | Opcodes.ACC_STATIC
                | Opcodes.ACC_FINAL;
        assertEquals(requiredAccess, singletonAccess[0] & requiredAccess);
        assertSame(
                FabricRecipeResultComponentBackend.INSTANCE,
                FabricRecipeResultComponentBackend.INSTANCE
        );
    }

    @Test
    void fabricRuntimeUsesOneSharedBaseAndAlchemySerializerCopy()
            throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        assertEquals(
                1,
                Collections.list(classLoader.getResources(
                        SHARED_SERIALIZER_RESOURCE
                )).size()
        );
        assertEquals(
                1,
                Collections.list(classLoader.getResources(
                        SHARED_ALCHEMY_SERIALIZER_RESOURCE
                )).size()
        );

        List<String> superClasses = new ArrayList<>();
        reader(LEGACY_MATRIX_SERIALIZER_RESOURCE).accept(
                new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(
                            int version,
                            int access,
                            String name,
                            String signature,
                            String superName,
                            String[] interfaces
                    ) {
                        superClasses.add(superName);
                    }
                },
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG
                        | ClassReader.SKIP_FRAMES
        );
        assertEquals(
                List.of("ru/feytox/etherology/recipes/FeyRecipeSerializer"),
                superClasses
        );
    }

    private static List<String> operations(String methodName) throws IOException {
        List<String> operations = new ArrayList<>();
        reader(BACKEND_RESOURCE).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(methodName)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        operations.add(opcodeName(opcode) + ":" + owner + "#" + name);
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        operations.add(owner + "#" + name);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return operations;
    }

    private static String opcodeName(int opcode) {
        if (opcode == Opcodes.GETSTATIC) return "GETSTATIC";
        if (opcode == Opcodes.PUTSTATIC) return "PUTSTATIC";
        return Integer.toString(opcode);
    }

    private static ClassReader reader(String resource) throws IOException {
        InputStream stream = FabricRecipeResultComponentBackendTest.class
                .getResourceAsStream(resource);
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return new ClassReader(stream);
        }
    }
}
