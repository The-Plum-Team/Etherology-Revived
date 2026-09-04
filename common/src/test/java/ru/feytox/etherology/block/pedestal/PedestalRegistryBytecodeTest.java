package ru.feytox.etherology.block.pedestal;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PedestalRegistryBytecodeTest {

    private static final String BLOCKS =
            "ru/feytox/etherology/registry/block/SharedPedestalBlocks";
    private static final String ITEMS =
            "ru/feytox/etherology/registry/item/SharedPedestalBlockItems";
    private static final String BLOCK_ENTITIES =
            "ru/feytox/etherology/registry/block/SharedPedestalBlockEntities";
    private static final String BOOTSTRAP =
            "ru/feytox/etherology/bootstrap/EtherologyBootstrap";
    private static final String BLOCK =
            "ru/feytox/etherology/block/pedestal/PedestalBlock";
    private static final String BLOCK_ENTITY =
            "ru/feytox/etherology/block/pedestal/PedestalBlockEntity";
    private static final String DEFERRED_REGISTER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String REGISTRY_SUPPLIER =
            "dev/architectury/registry/registries/RegistrySupplier";

    @Test
    void blockRegistryOwnsOnlyTheLazyCanonicalPedestalId() throws IOException {
        PedestalClassFile.ClassShape shape = PedestalClassFile.shape(BLOCKS);
        assertEquals(List.of("BLOCKS", "PEDESTAL"), new ArrayList<>(shape.fields().keySet()));
        assertRegistryField(
                shape,
                "BLOCKS",
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "L" + DEFERRED_REGISTER + ";",
                "L" + DEFERRED_REGISTER + "<Lnet/minecraft/block/Block;>;"
        );
        assertRegistryField(
                shape,
                "PEDESTAL",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "L" + REGISTRY_SUPPLIER + ";",
                "L" + REGISTRY_SUPPLIER + "<L" + BLOCK + ";>;"
        );

        PedestalClassFile.MethodTrace initializer = PedestalClassFile.trace(
                BLOCKS,
                "<clinit>"
        );
        assertEquals(List.of("pedestal"), initializer.stringConstants());
        assertEquals(
                List.of("BLOCK", "BLOCKS", "BLOCKS", "PEDESTAL"),
                initializer.fieldInstructions().stream()
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );
        assertEquals(List.of("create", "register"), invocationNames(initializer));
        assertEquals(1, initializer.dynamicInvocations().size());
        assertEquals(
                List.of(new Handle(
                        Opcodes.H_NEWINVOKESPECIAL,
                        BLOCK,
                        "<init>",
                        "()V",
                        false
                )),
                initializer.dynamicInvocations().get(0).handles()
        );
        assertNoEagerSupplierGet(initializer);
        assertRegisterOnlyAttaches(BLOCKS);
    }

    @Test
    void blockItemRegistryBindsTheSameIdAndVanillaBlockItemMap()
            throws IOException {
        PedestalClassFile.ClassShape shape = PedestalClassFile.shape(ITEMS);
        assertEquals(List.of("ITEMS", "PEDESTAL_ITEM"), new ArrayList<>(shape.fields().keySet()));
        assertRegistryField(
                shape,
                "ITEMS",
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "L" + DEFERRED_REGISTER + ";",
                "L" + DEFERRED_REGISTER + "<Lnet/minecraft/item/Item;>;"
        );
        assertRegistryField(
                shape,
                "PEDESTAL_ITEM",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "L" + REGISTRY_SUPPLIER + ";",
                "L" + REGISTRY_SUPPLIER + "<Lnet/minecraft/item/BlockItem;>;"
        );

        PedestalClassFile.MethodTrace initializer = PedestalClassFile.trace(
                ITEMS,
                "<clinit>"
        );
        assertEquals(List.of("pedestal"), initializer.stringConstants());
        assertEquals(
                List.of("ITEM", "ITEMS", "ITEMS", "PEDESTAL_ITEM"),
                initializer.fieldInstructions().stream()
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );
        assertEquals(List.of("create", "register"), invocationNames(initializer));
        assertNoEagerSupplierGet(initializer);

        PedestalClassFile.MethodTrace factory = soleLambda(ITEMS);
        assertEquals(
                List.of("get", "<init>", "<init>", "appendBlocks"),
                invocationNames(factory)
        );
        assertEquals(
                List.of("PEDESTAL", "BLOCK_ITEMS"),
                factory.fieldInstructions().stream()
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );
        assertEquals(
                List.of(
                        new PedestalClassFile.TypeInstruction(
                                Opcodes.NEW,
                                "net/minecraft/item/BlockItem"
                        ),
                        new PedestalClassFile.TypeInstruction(
                                Opcodes.CHECKCAST,
                                "net/minecraft/block/Block"
                        ),
                        new PedestalClassFile.TypeInstruction(
                                Opcodes.NEW,
                                "net/minecraft/item/Item$Settings"
                        )
                ),
                factory.typeInstructions()
        );
        assertRegisterOnlyAttaches(ITEMS);
    }

    @Test
    void blockEntityRegistryBindsTheExactTypeIdToOnlyThePedestal()
            throws IOException {
        PedestalClassFile.ClassShape shape = PedestalClassFile.shape(BLOCK_ENTITIES);
        assertEquals(
                List.of("BLOCK_ENTITIES", "PEDESTAL"),
                new ArrayList<>(shape.fields().keySet())
        );
        assertRegistryField(
                shape,
                "BLOCK_ENTITIES",
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "L" + DEFERRED_REGISTER + ";",
                "L" + DEFERRED_REGISTER
                        + "<Lnet/minecraft/block/entity/BlockEntityType<*>;>;"
        );
        assertRegistryField(
                shape,
                "PEDESTAL",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "L" + REGISTRY_SUPPLIER + ";",
                "L" + REGISTRY_SUPPLIER
                        + "<Lnet/minecraft/block/entity/BlockEntityType"
                        + "<L" + BLOCK_ENTITY + ";>;>;"
        );

        PedestalClassFile.MethodTrace initializer = PedestalClassFile.trace(
                BLOCK_ENTITIES,
                "<clinit>"
        );
        assertEquals(List.of("pedestal_block_entity"), initializer.stringConstants());
        assertEquals(
                List.of(
                        "BLOCK_ENTITY_TYPE",
                        "BLOCK_ENTITIES",
                        "BLOCK_ENTITIES",
                        "PEDESTAL"
                ),
                initializer.fieldInstructions().stream()
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );
        assertEquals(List.of("create", "register"), invocationNames(initializer));
        assertNoEagerSupplierGet(initializer);

        PedestalClassFile.MethodTrace factory = soleLambda(BLOCK_ENTITIES);
        assertEquals(
                List.of("get", "create", "build"),
                invocationNames(factory)
        );
        assertEquals(
                List.of("PEDESTAL"),
                factory.fieldInstructions().stream()
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );
        assertEquals(List.of(1, 0), factory.integerConstants());
        assertEquals(1, factory.dynamicInvocations().size());
        assertEquals(
                List.of(new Handle(
                        Opcodes.H_NEWINVOKESPECIAL,
                        BLOCK_ENTITY,
                        "<init>",
                        "(Lnet/minecraft/util/math/BlockPos;"
                                + "Lnet/minecraft/block/BlockState;)V",
                        false
                )),
                factory.dynamicInvocations().get(0).handles()
        );
        assertTrue(factory.opcodes().contains(Opcodes.ACONST_NULL));
        assertRegisterOnlyAttaches(BLOCK_ENTITIES);
    }

    @Test
    void bootstrapAttachesPedestalBlockThenItemThenBlockEntityExactlyOnce()
            throws IOException {
        PedestalClassFile.MethodTrace bootstrap = PedestalClassFile.trace(
                BOOTSTRAP,
                "initialize"
        );
        Set<String> pedestalOwners = Set.of(BLOCKS, ITEMS, BLOCK_ENTITIES);
        List<String> pedestalCalls = bootstrap.invocations().stream()
                .filter(invocation -> pedestalOwners.contains(invocation.owner()))
                .map(PedestalClassFile.Invocation::qualifiedName)
                .toList();

        assertEquals(
                List.of(
                        BLOCKS + "#register()V",
                        ITEMS + "#register()V",
                        BLOCK_ENTITIES + "#register()V"
                ),
                pedestalCalls
        );
    }

    private static PedestalClassFile.MethodTrace soleLambda(String owner)
            throws IOException {
        List<String> lambdaMethods = PedestalClassFile.shape(owner).methods().keySet().stream()
                .filter(method -> method.startsWith("lambda$"))
                .toList();
        assertEquals(1, lambdaMethods.size(), owner);
        String key = lambdaMethods.get(0);
        int descriptorStart = key.indexOf('(');
        return PedestalClassFile.trace(
                owner,
                key.substring(0, descriptorStart),
                key.substring(descriptorStart)
        );
    }

    private static void assertRegisterOnlyAttaches(String owner) throws IOException {
        PedestalClassFile.MethodTrace register = PedestalClassFile.trace(owner, "register");
        assertEquals(List.of("attach"), invocationNames(register), owner);
        assertEquals(DEFERRED_REGISTER, register.invocations().get(0).owner(), owner);
    }

    private static void assertNoEagerSupplierGet(
            PedestalClassFile.MethodTrace initializer
    ) {
        assertFalse(initializer.invocations().stream().anyMatch(
                invocation -> invocation.owner().equals(REGISTRY_SUPPLIER)
                        && invocation.name().equals("get")
        ));
    }

    private static void assertRegistryField(
            PedestalClassFile.ClassShape shape,
            String name,
            int access,
            String descriptor,
            String signature
    ) {
        PedestalClassFile.FieldDefinition field = shape.fields().get(name);
        assertEquals(access, field.access(), name);
        assertEquals(descriptor, field.descriptor(), name);
        assertEquals(signature, field.signature(), name);
    }

    private static List<String> invocationNames(PedestalClassFile.MethodTrace trace) {
        return trace.invocations().stream()
                .map(PedestalClassFile.Invocation::name)
                .toList();
    }
}
