package ru.feytox.etherology.recipes;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AlchemyRecipeFoundationBytecodeTest {

    private static final List<String> SHARED_RESOURCES = List.of(
            "/ru/feytox/etherology/recipes/FeyInputRecipe.class",
            "/ru/feytox/etherology/recipes/FeyRecipe.class",
            "/ru/feytox/etherology/recipes/FeyRecipeSerializer.class",
            "/ru/feytox/etherology/recipes/FeyRecipeSerializer$1.class",
            "/ru/feytox/etherology/recipes/FeyRecipeJsonProvider.class",
            "/ru/feytox/etherology/recipes/RecipeResultComponentBackend.class",
            "/ru/feytox/etherology/recipes/RecipeResultComponents.class",
            "/ru/feytox/etherology/recipes/RecipeResultComponents$UnavailableBackend.class",
            "/ru/feytox/etherology/recipes/alchemy/AlchemyRecipe.class",
            "/ru/feytox/etherology/recipes/alchemy/AlchemyRecipeInventory.class",
            "/ru/feytox/etherology/recipes/alchemy/AlchemyRecipeSerializer.class"
    );
    private static final List<String> FORBIDDEN_REFERENCES = List.of(
            "net/fabricmc/",
            "net/minecraftforge/",
            "lombok/",
            "ru/feytox/etherology/Etherology",
            "ru/feytox/etherology/util/misc/EIdentifier",
            "ru/feytox/etherology/magic/staff/StaffComponent",
            "ru/feytox/etherology/registry/misc/ComponentTypes"
    );

    @Test
    void sharedFoundationHasNoLoaderLombokOrLegacyComponentDependencies()
            throws IOException {
        Set<String> forbidden = new LinkedHashSet<>();
        for (String resource : SHARED_RESOURCES) {
            reader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visit(
                        int version,
                        int access,
                        String name,
                        String signature,
                        String superName,
                        String[] interfaces
                ) {
                    check(signature, forbidden);
                    check(superName, forbidden);
                    if (interfaces != null) {
                        for (String interfaceName : interfaces) {
                            check(interfaceName, forbidden);
                        }
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
                    check(descriptor, forbidden);
                    check(signature, forbidden);
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
                    check(descriptor, forbidden);
                    check(signature, forbidden);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            check(type, forbidden);
                        }

                        @Override
                        public void visitFieldInsn(
                                int opcode,
                                String owner,
                                String name,
                                String descriptor
                        ) {
                            check(owner, forbidden);
                            check(descriptor, forbidden);
                        }

                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String name,
                                String descriptor,
                                boolean isInterface
                        ) {
                            check(owner, forbidden);
                            check(descriptor, forbidden);
                        }

                        @Override
                        public void visitInvokeDynamicInsn(
                                String name,
                                String descriptor,
                                Handle bootstrapMethodHandle,
                                Object... bootstrapMethodArguments
                        ) {
                            check(descriptor, forbidden);
                            check(bootstrapMethodHandle.getOwner(), forbidden);
                            check(bootstrapMethodHandle.getDesc(), forbidden);
                            for (Object argument : bootstrapMethodArguments) {
                                if (argument instanceof Handle handle) {
                                    check(handle.getOwner(), forbidden);
                                    check(handle.getDesc(), forbidden);
                                } else if (argument instanceof Type type) {
                                    check(type.getDescriptor(), forbidden);
                                }
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        assertEquals(Set.of(), forbidden);
    }

    @Test
    void alchemyRecipeRetainsIdentityEqualityAndTheCanonicalInterfaces()
            throws IOException {
        Set<String> recipeInterfaces = new LinkedHashSet<>();
        Set<String> declaredEquality = new LinkedHashSet<>();
        reader("/ru/feytox/etherology/recipes/alchemy/AlchemyRecipe.class")
                .accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(
                            int version,
                            int access,
                            String name,
                            String signature,
                            String superName,
                            String[] interfaces
                    ) {
                        if (interfaces != null) {
                            recipeInterfaces.addAll(List.of(interfaces));
                        }
                    }

                    @Override
                    public MethodVisitor visitMethod(
                            int access,
                            String name,
                            String descriptor,
                            String signature,
                            String[] exceptions
                    ) {
                        if (name.equals("equals") || name.equals("hashCode")) {
                            declaredEquality.add(name + descriptor);
                        }
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG
                        | ClassReader.SKIP_FRAMES);

        assertEquals(
                Set.of("ru/feytox/etherology/recipes/FeyInputRecipe"),
                recipeInterfaces
        );
        assertEquals(Set.of(), declaredEquality);
    }

    @Test
    void compiledMatchingStillChecksItemCountThenEveryAspectMinimum()
            throws IOException {
        String recipe = "/ru/feytox/etherology/recipes/alchemy/AlchemyRecipe.class";
        assertEquals(
                List.of(
                        "ru/feytox/etherology/recipes/alchemy/"
                                + "AlchemyRecipeInventory#stack"
                                + "()Lnet/minecraft/item/ItemStack;",
                        "net/minecraft/recipe/Ingredient#test"
                                + "(Lnet/minecraft/item/ItemStack;)Z",
                        "ru/feytox/etherology/recipes/alchemy/"
                                + "AlchemyRecipeInventory#stack"
                                + "()Lnet/minecraft/item/ItemStack;",
                        "net/minecraft/item/ItemStack#getCount()I",
                        "ru/feytox/etherology/recipes/alchemy/"
                                + "AlchemyRecipeInventory#cauldronAspects"
                                + "()Lru/feytox/etherology/magic/aspects/"
                                + "AspectContainer;",
                        "ru/feytox/etherology/magic/aspects/"
                                + "AspectContainer#getAspects"
                                + "()Lcom/google/common/collect/ImmutableMap;",
                        "ru/feytox/etherology/magic/aspects/"
                                + "AspectContainer#getAspects"
                                + "()Lcom/google/common/collect/ImmutableMap;",
                        "com/google/common/collect/ImmutableMap#entrySet"
                                + "()Lcom/google/common/collect/ImmutableSet;",
                        "com/google/common/collect/ImmutableMap#get"
                                + "(Ljava/lang/Object;)Ljava/lang/Object;"
                ),
                selectedCalls(
                        recipe,
                        "matches",
                        "(Lru/feytox/etherology/recipes/alchemy/"
                                + "AlchemyRecipeInventory;Lnet/minecraft/world/World;)Z"
                )
        );

        assertEquals(
                List.of("net/minecraft/item/ItemStack#copy"
                        + "()Lnet/minecraft/item/ItemStack;"),
                selectedCalls(
                        recipe,
                        "getOutput",
                        "()Lnet/minecraft/item/ItemStack;"
                )
        );
        assertEquals(
                List.of("net/minecraft/item/ItemStack#EMPTY"),
                selectedFields(
                        recipe,
                        "craft",
                        "(Lru/feytox/etherology/recipes/alchemy/"
                                + "AlchemyRecipeInventory;Lnet/minecraft/registry/"
                                + "DynamicRegistryManager;)Lnet/minecraft/item/ItemStack;"
                )
        );
    }

    @Test
    void compiledJsonAndPacketPathsRetainEveryCanonicalBoundary()
            throws IOException {
        String serializer = "/ru/feytox/etherology/recipes/alchemy/"
                + "AlchemyRecipeSerializer.class";
        List<String> jsonRead = allCalls(
                serializer,
                "read",
                "(Lnet/minecraft/util/Identifier;Lcom/google/gson/JsonObject;)"
                        + "Lru/feytox/etherology/recipes/alchemy/AlchemyRecipe;"
        );
        assertContainsCalls(jsonRead, List.of(
                "net/minecraft/recipe/Ingredient#fromJson",
                "net/minecraft/util/JsonHelper#getInt",
                "ru/feytox/etherology/magic/aspects/Aspect#get",
                "net/minecraft/util/JsonHelper#asInt",
                "ru/feytox/etherology/recipes/alchemy/AlchemyRecipe#<init>",
                "ru/feytox/etherology/recipes/alchemy/"
                        + "AlchemyRecipeSerializer#readItemStack"
        ));

        List<String> packetRead = allCalls(
                serializer,
                "read",
                "(Lnet/minecraft/util/Identifier;Lnet/minecraft/network/"
                        + "PacketByteBuf;)Lru/feytox/etherology/recipes/alchemy/"
                        + "AlchemyRecipe;"
        );
        assertContainsCalls(packetRead, List.of(
                "net/minecraft/recipe/Ingredient#fromPacket",
                "net/minecraft/network/PacketByteBuf#readVarInt",
                "net/minecraft/network/PacketByteBuf#readMap",
                "net/minecraft/network/PacketByteBuf#readItemStack",
                "ru/feytox/etherology/recipes/alchemy/AlchemyRecipe#<init>"
        ));

        List<String> packetWrite = allCalls(
                serializer,
                "write",
                "(Lnet/minecraft/network/PacketByteBuf;Lru/feytox/etherology/"
                        + "recipes/alchemy/AlchemyRecipe;)V"
        );
        assertContainsCalls(packetWrite, List.of(
                "net/minecraft/recipe/Ingredient#write",
                "net/minecraft/network/PacketByteBuf#writeVarInt",
                "net/minecraft/network/PacketByteBuf#writeMap",
                "net/minecraft/network/PacketByteBuf#writeItemStack"
        ));

        List<String> jsonWrite = allCalls(
                serializer,
                "writeJson",
                "(Lcom/google/gson/JsonObject;Lru/feytox/etherology/recipes/"
                        + "alchemy/AlchemyRecipe;)V"
        );
        assertContainsCalls(jsonWrite, List.of(
                "net/minecraft/recipe/Ingredient#toJson",
                "ru/feytox/etherology/magic/aspects/AspectContainer#getAspects",
                "ru/feytox/etherology/recipes/alchemy/"
                        + "AlchemyRecipeSerializer#writeItemStack"
        ));

        String base = "/ru/feytox/etherology/recipes/FeyRecipeSerializer.class";
        assertContainsCalls(allCalls(
                base,
                "readItemStack",
                "(Lcom/google/gson/JsonObject;Ljava/lang/String;)"
                        + "Lnet/minecraft/item/ItemStack;"
        ), List.of(
                "ru/feytox/etherology/recipes/RecipeResultComponents#read"
        ));
        assertContainsCalls(allCalls(
                base,
                "writeItemStack",
                "(Lcom/google/gson/JsonObject;Ljava/lang/String;"
                        + "Lnet/minecraft/item/ItemStack;)V"
        ), List.of(
                "ru/feytox/etherology/recipes/RecipeResultComponents#write"
        ));
    }

    private static void assertContainsCalls(
            List<String> actual,
            List<String> expected
    ) {
        for (String call : expected) {
            assertTrue(
                    actual.stream().anyMatch(actualCall ->
                            actualCall.startsWith(call)),
                    call + " missing from " + actual
            );
        }
    }

    private static List<String> selectedCalls(
            String resource,
            String methodName,
            String methodDescriptor
    ) throws IOException {
        return allCalls(resource, methodName, methodDescriptor).stream()
                .filter(call -> call.startsWith("ru/feytox/etherology/")
                        || call.startsWith("net/minecraft/recipe/Ingredient#")
                        || call.startsWith("net/minecraft/item/ItemStack#")
                        || call.startsWith("com/google/common/collect/ImmutableMap#"))
                .toList();
    }

    private static List<String> allCalls(
            String resource,
            String methodName,
            String methodDescriptor
    ) throws IOException {
        List<String> calls = new java.util.ArrayList<>();
        reader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(methodName)
                        || !descriptor.equals(methodDescriptor)) {
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
                        calls.add(owner + "#" + name + descriptor);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return calls;
    }

    private static List<String> selectedFields(
            String resource,
            String methodName,
            String methodDescriptor
    ) throws IOException {
        List<String> fields = new java.util.ArrayList<>();
        reader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(methodName)
                        || !descriptor.equals(methodDescriptor)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (owner.startsWith("net/minecraft/")
                                || owner.startsWith("ru/feytox/etherology/")) {
                            fields.add(owner + "#" + name);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return fields;
    }

    private static void check(String reference, Set<String> forbidden) {
        if (reference == null) return;
        for (String candidate : FORBIDDEN_REFERENCES) {
            if (reference.contains(candidate)) forbidden.add(reference);
        }
    }

    private static ClassReader reader(String resource) throws IOException {
        InputStream stream = AlchemyRecipeFoundationBytecodeTest.class
                .getResourceAsStream(resource);
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return new ClassReader(stream);
        }
    }
}
