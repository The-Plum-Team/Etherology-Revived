package ru.feytox.etherology.forge;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static ru.feytox.etherology.forge.PedestalBytecodeAssertions.*;

final class SharedItemCatalogAssertions {

    private static final String DEFERRED_REGISTER = "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String SUPPLIER = "dev/architectury/registry/registries/RegistrySupplier";

    private SharedItemCatalogAssertions() {
    }

    static MethodNode singleMethod(ClassNode owner, String name) {
        List<MethodNode> methods = owner.methods.stream()
                .filter(method -> method.name.equals(name)).toList();
        assertEquals(1, methods.size(), owner.name + "#" + name);
        return methods.get(0);
    }

    static List<String> fieldsFrom(MethodNode method, Set<String> owners) {
        return Arrays.stream(method.instructions.toArray())
                .filter(FieldInsnNode.class::isInstance)
                .map(FieldInsnNode.class::cast)
                .filter(field -> owners.contains(field.owner))
                .map(field -> field.name).toList();
    }

    static Map<String, MethodNode> assertFactories(
            ClassNode catalog, String subtype, Map<String, String> expectedIds
    ) {
        assertEquals(expectedIds.size() + 1, catalog.fields.size());
        assertEquals("ITEMS", catalog.fields.get(0).name);
        List<String> expectedFields = new ArrayList<>(expectedIds.keySet());
        assertEquals(expectedFields, catalog.fields.subList(1, catalog.fields.size()).stream()
                .map(field -> field.name).toList());
        for (var field : catalog.fields.subList(1, catalog.fields.size())) {
            assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                    field.access);
            assertEquals("L" + SUPPLIER + "<L" + subtype + ";>;", field.signature);
        }

        Map<String, String> ids = new LinkedHashMap<>();
        Map<String, MethodNode> factories = new LinkedHashMap<>();
        String pendingId = null;
        Handle pendingFactory = null;
        int registrations = 0;
        for (AbstractInsnNode instruction : requireMethod(catalog, "<clinit>", "()V").instructions) {
            if (instruction instanceof LdcInsnNode constant && constant.cst instanceof String id) {
                pendingId = id;
            } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                List<Handle> handles = Arrays.stream(dynamic.bsmArgs)
                        .filter(Handle.class::isInstance).map(Handle.class::cast).toList();
                assertEquals(1, handles.size());
                pendingFactory = handles.get(0);
            } else if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(DEFERRED_REGISTER) && call.name.equals("register")) {
                assertNotNull(pendingId);
                assertNotNull(pendingFactory);
                registrations++;
            } else if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.PUTSTATIC && field.owner.equals(catalog.name)
                    && !field.name.equals("ITEMS")) {
                assertNotNull(pendingFactory);
                assertEquals(catalog.name, pendingFactory.getOwner());
                assertNull(ids.put(field.name, pendingId));
                assertNull(factories.put(field.name, requireMethod(catalog,
                        pendingFactory.getName(), pendingFactory.getDesc())));
                pendingId = null;
                pendingFactory = null;
            }
        }
        assertEquals(expectedIds.size(), registrations);
        assertEquals(expectedIds, ids);
        assertEquals(expectedFields, new ArrayList<>(ids.keySet()));
        for (MethodNode method : catalog.methods) {
            assertFalse(calls(method).stream().anyMatch(call -> call.startsWith(SUPPLIER + "#get")));
        }
        assertEquals(List.of(DEFERRED_REGISTER + "#attach()V"),
                calls(requireMethod(catalog, "register", "()V")));
        return factories;
    }

    static void assertAliases(ClassNode legacy, String catalog, List<String> expectedFields) {
        for (String name : expectedFields) {
            var matches = legacy.fields.stream().filter(field -> field.name.equals(name)).toList();
            assertEquals(1, matches.size());
            var field = matches.get(0);
            assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, field.access);
            assertTrue(List.of("Lnet/minecraft/item/Item;", "Lnet/minecraft/class_1792;").contains(field.desc));
        }
        String pendingField = null;
        int supplierGets = 0;
        List<String> aliases = new ArrayList<>();
        for (AbstractInsnNode instruction : requireMethod(legacy, "<clinit>", "()V").instructions) {
            if (instruction instanceof FieldInsnNode field && field.owner.equals(catalog)
                    && field.getOpcode() == Opcodes.GETSTATIC) {
                assertNull(pendingField);
                pendingField = field.name;
                supplierGets = 0;
            } else if (instruction instanceof MethodInsnNode call && pendingField != null
                    && call.owner.equals(SUPPLIER) && call.name.equals("get")) {
                supplierGets++;
            } else if (instruction instanceof FieldInsnNode field && field.owner.equals(legacy.name)
                    && field.getOpcode() == Opcodes.PUTSTATIC && expectedFields.contains(field.name)) {
                assertEquals(field.name, pendingField);
                assertEquals(1, supplierGets);
                aliases.add(field.name);
                pendingField = null;
            }
        }
        assertNull(pendingField);
        assertEquals(expectedFields, aliases);
    }
}
