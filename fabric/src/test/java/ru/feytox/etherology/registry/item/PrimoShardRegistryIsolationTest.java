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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class PrimoShardRegistryIsolationTest {

    private static final String LEGACY_ITEMS =
            "/ru/feytox/etherology/registry/item/EItems.class";
    private static final String FABRIC_INITIALIZER =
            "/ru/feytox/etherology/Etherology.class";
    private static final String SHARED_PRIMO_SHARD_ITEMS =
            "ru/feytox/etherology/registry/item/SharedPrimoShardItems";
    private static final String LEGACY_ITEMS_OWNER =
            "ru/feytox/etherology/registry/item/EItems";
    private static final String PRIMO_SHARD =
            "ru/feytox/etherology/item/PrimoShard";
    private static final String REGISTRY_SUPPLIER =
            "dev/architectury/registry/registries/RegistrySupplier";

    private static final Map<String, String> EXACT_ALIASES = exactAliases();
    private static final Set<String> ITEM_IDS = Set.of(
            "primoshard_keta",
            "primoshard_rella",
            "primoshard_clos",
            "primoshard_via"
    );

    @Test
    void legacyFieldsAreExactSharedSupplierAliasesWithoutDuplicateOwnership()
            throws IOException {
        List<FieldInfo> aliasFields = new ArrayList<>();
        List<String> aliasEvents = new ArrayList<>();
        Set<String> duplicateIds = new LinkedHashSet<>();
        AtomicInteger constructions = new AtomicInteger();

        reader(LEGACY_ITEMS).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if (EXACT_ALIASES.containsKey(name)) {
                    aliasFields.add(new FieldInfo(access, name, descriptor));
                }
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
                if (!name.equals("<clinit>")) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    private String pendingAlias;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (ITEM_IDS.contains(value)) {
                            duplicateIds.add((String) value);
                        }
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW && type.equals(PRIMO_SHARD)) {
                            constructions.incrementAndGet();
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String fieldName,
                            String fieldDescriptor
                    ) {
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals(SHARED_PRIMO_SHARD_ITEMS)
                                && EXACT_ALIASES.containsKey(fieldName)) {
                            pendingAlias = fieldName;
                            aliasEvents.add(
                                    "SharedPrimoShardItems#" + fieldName
                            );
                        }
                        if (opcode == Opcodes.PUTSTATIC
                                && owner.equals(LEGACY_ITEMS_OWNER)
                                && fieldName.equals(pendingAlias)) {
                            aliasEvents.add("EItems#" + fieldName);
                            pendingAlias = null;
                        }
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        if (pendingAlias != null
                                && owner.equals(REGISTRY_SUPPLIER)
                                && name.equals("get")) {
                            aliasEvents.add("RegistrySupplier#get" + descriptor);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        List<FieldInfo> expectedFields = EXACT_ALIASES.keySet().stream()
                .map(name -> new FieldInfo(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        name,
                        "Lnet/minecraft/item/Item;"
                ))
                .toList();
        assertEquals(expectedFields, aliasFields);
        assertEquals(expectedAliasEvents(), aliasEvents);
        assertEquals(Set.of(), duplicateIds);
        assertEquals(0, constructions.get());
    }

    @Test
    void fabricAttachesSharedPrimoShardsOnceBeforeAlchemyAndLegacyItems()
            throws IOException {
        Set<String> relevantOwners = Set.of(
                "ru/feytox/etherology/registry/item/SharedLensItems",
                SHARED_PRIMO_SHARD_ITEMS,
                "ru/feytox/etherology/registry/misc/SharedAlchemyRecipes",
                LEGACY_ITEMS_OWNER
        );
        List<String> calls = new ArrayList<>();
        reader(FABRIC_INITIALIZER).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("initialize")) {
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
                        if (relevantOwners.contains(owner)) {
                            calls.add(owner + "#" + name + descriptor);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(
                List.of(
                        "ru/feytox/etherology/registry/item/SharedLensItems"
                                + "#register()V",
                        SHARED_PRIMO_SHARD_ITEMS + "#register()V",
                        "ru/feytox/etherology/registry/misc/SharedAlchemyRecipes"
                                + "#register()V",
                        LEGACY_ITEMS_OWNER + "#registerItems()V"
                ),
                calls
        );
    }

    private static Map<String, String> exactAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("PRIMOSHARD_KETA", "primoshard_keta");
        aliases.put("PRIMOSHARD_RELLA", "primoshard_rella");
        aliases.put("PRIMOSHARD_CLOS", "primoshard_clos");
        aliases.put("PRIMOSHARD_VIA", "primoshard_via");
        return aliases;
    }

    private static List<String> expectedAliasEvents() {
        List<String> events = new ArrayList<>();
        EXACT_ALIASES.keySet().forEach(fieldName -> {
            events.add("SharedPrimoShardItems#" + fieldName);
            events.add("RegistrySupplier#get()Ljava/lang/Object;");
            events.add("EItems#" + fieldName);
        });
        return events;
    }

    private static ClassReader reader(String resource) throws IOException {
        InputStream stream = PrimoShardRegistryIsolationTest.class
                .getResourceAsStream(resource);
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return new ClassReader(stream);
        }
    }

    private record FieldInfo(int access, String name, String descriptor) {
    }
}
