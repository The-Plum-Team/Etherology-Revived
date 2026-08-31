package ru.feytox.etherology.forge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MaterialItemRegistryResourcesTest {

    private static final String SHARED_MATERIAL_ITEMS =
            "ru/feytox/etherology/registry/item/SharedMaterialItems.class";
    private static final String SHARED_MATERIAL_ITEMS_OWNER =
            "ru/feytox/etherology/registry/item/SharedMaterialItems";
    private static final String LEGACY_E_ITEMS =
            "ru/feytox/etherology/registry/item/EItems.class";
    private static final String LEGACY_E_ITEMS_OWNER =
            "ru/feytox/etherology/registry/item/EItems";
    private static final String LEGACY_DECO_BLOCK_ITEMS =
            "ru/feytox/etherology/registry/item/DecoBlockItems.class";
    private static final String BOOTSTRAP =
            "ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String FABRIC_INITIALIZER =
            "ru/feytox/etherology/Etherology.class";
    private static final String REGISTRY_SUPPLIER_DESCRIPTOR =
            "Ldev/architectury/registry/registries/RegistrySupplier;";

    private static final Map<String, MaterialItem> MATERIALS = materials();

    @Test
    void everyArtifactHasOneExactSharedOwnerAndNoLegacyFields() throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entries = jar.stream().map(JarEntry::getName).toList();
                assertEquals(
                        1,
                        entries.stream().filter(SHARED_MATERIAL_ITEMS::equals).count(),
                        artifact.description()
                );
                assertSharedCatalog(
                        artifact.description(),
                        classReader(jar, SHARED_MATERIAL_ITEMS)
                );
                assertNoForbiddenOwnerReferences(
                        artifact.description(),
                        classReader(jar, SHARED_MATERIAL_ITEMS)
                );

                if (artifact.fabricApplication()) {
                    assertLegacyFieldsAbsent(
                            artifact.description(),
                            classReader(jar, LEGACY_E_ITEMS),
                            Set.of("ETHEROSCOPE", "THUJA_OIL")
                    );
                    assertLegacyFieldsAbsent(
                            artifact.description(),
                            classReader(jar, LEGACY_DECO_BLOCK_ITEMS),
                            Set.of(
                                    "AZEL_INGOT",
                                    "AZEL_NUGGET",
                                    "ETHRIL_INGOT",
                                    "ETHRIL_NUGGET",
                                    "EBONY_INGOT",
                                    "EBONY_NUGGET",
                                    "ENRICHED_ATTRAHITE",
                                    "RAW_AZEL",
                                    "ATTRAHITE_BRICK",
                                    "BINDER",
                                    "EBONY",
                                    "RESONATING_WAND"
                            )
                    );
                } else {
                    assertFalse(entries.contains(LEGACY_E_ITEMS), artifact.description());
                    assertFalse(
                            entries.contains(LEGACY_DECO_BLOCK_ITEMS),
                            artifact.description()
                    );
                }
            }
        }
    }

    @Test
    void eachLoaderAttachesTheSharedCatalogExactlyOnce() throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                String initializer = artifact.fabricApplication()
                        ? FABRIC_INITIALIZER
                        : BOOTSTRAP;
                List<String> invocations = methodInvocations(
                        classReader(jar, initializer),
                        artifact.fabricApplication() ? "initialize" : "initialize"
                );
                String sharedRegistration = SHARED_MATERIAL_ITEMS_OWNER + "#register()V";
                assertEquals(
                        1,
                        invocations.stream().filter(sharedRegistration::equals).count(),
                        artifact.description()
                );
                if (artifact.fabricApplication()) {
                    String legacyRegistration =
                            LEGACY_E_ITEMS_OWNER + "#registerItems()V";
                    assertEquals(
                            1,
                            invocations.stream().filter(legacyRegistration::equals).count(),
                            artifact.description()
                    );
                    assertTrue(
                            invocations.indexOf(sharedRegistration)
                                    < invocations.indexOf(legacyRegistration),
                            artifact.description()
                    );
                }
            }
        }
    }

    @Test
    void packagedModelsTexturesAndEnglishNamesAreExact() throws IOException {
        Path repositoryRoot = requiredPath("etherology.materialItems.repositoryRoot");
        Path modelRoot = repositoryRoot.resolve(
                "src/main/generated/assets/etherology/models/item"
        );
        Path textureRoot = repositoryRoot.resolve(
                "src/client/resources/assets/etherology/textures/item"
        );
        Path languageFile = repositoryRoot.resolve(
                "src/client/resources/assets/etherology/lang/en_us.json"
        );
        requireRegularFile(languageFile);

        for (MaterialItem material : MATERIALS.values()) {
            requireRegularFile(modelRoot.resolve(material.id() + ".json"));
            requireRegularFile(textureRoot.resolve(material.id() + ".png"));
        }

        JsonObject canonicalLanguage = parseObject(Files.readString(languageFile));
        for (MaterialItem material : MATERIALS.values()) {
            assertEquals(
                    material.englishName(),
                    canonicalLanguage.get("item.etherology." + material.id()).getAsString(),
                    material.id()
            );
        }

        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                for (MaterialItem material : MATERIALS.values()) {
                    assertResource(
                            artifact,
                            jar,
                            "assets/etherology/models/item/" + material.id() + ".json",
                            modelRoot.resolve(material.id() + ".json")
                    );
                    assertResource(
                            artifact,
                            jar,
                            "assets/etherology/textures/item/" + material.id() + ".png",
                            textureRoot.resolve(material.id() + ".png")
                    );
                }

                JarEntry languageEntry = jar.getJarEntry("assets/etherology/lang/en_us.json");
                if (!artifact.includesAssets()) {
                    assertEquals(null, languageEntry, artifact.description());
                    continue;
                }
                assertNotNull(languageEntry, artifact.description());
                JsonObject packagedLanguage = parseObject(new String(
                        jar.getInputStream(languageEntry).readAllBytes(),
                        StandardCharsets.UTF_8
                ));
                for (MaterialItem material : MATERIALS.values()) {
                    assertEquals(
                            material.englishName(),
                            packagedLanguage.get("item.etherology." + material.id())
                                    .getAsString(),
                            artifact.description() + ":" + material.id()
                    );
                }
            }
        }
    }

    private static void assertSharedCatalog(String description, ClassReader reader) {
        Map<String, String> fields = new LinkedHashMap<>();
        List<String> registrationIds = new ArrayList<>();
        List<String> unsafeInvocations = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                fields.put(name, descriptor);
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
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (name.equals("<clinit>")
                                && value instanceof String stringValue
                                && MATERIALS.containsKey(stringValue)) {
                            registrationIds.add(stringValue);
                        }
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String invokedName,
                            String invokedDescriptor,
                            boolean isInterface
                    ) {
                        if (owner.equals("net/minecraft/registry/Registry")
                                || owner.equals(
                                        "dev/architectury/registry/registries/RegistrySupplier"
                                ) && invokedName.equals("get")) {
                            unsafeInvocations.add(owner + "#" + invokedName);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        List<String> expectedFields = new ArrayList<>();
        expectedFields.add("ITEMS");
        expectedFields.addAll(MATERIALS.values().stream()
                .map(MaterialItem::fieldName)
                .toList());
        assertEquals(expectedFields, new ArrayList<>(fields.keySet()), description);
        for (MaterialItem material : MATERIALS.values()) {
            assertEquals(
                    REGISTRY_SUPPLIER_DESCRIPTOR,
                    fields.get(material.fieldName()),
                    description + ":" + material.fieldName()
            );
        }
        assertEquals(new ArrayList<>(MATERIALS.keySet()), registrationIds, description);
        assertEquals(List.of(), unsafeInvocations, description);
    }

    private static void assertNoForbiddenOwnerReferences(
            String description,
            ClassReader reader
    ) {
        Set<String> forbidden = new LinkedHashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces
            ) {
                check(name);
                check(superName);
                checkDescriptor(signature);
                if (interfaces != null) {
                    for (String interfaceName : interfaces) check(interfaceName);
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
                checkDescriptor(descriptor);
                checkDescriptor(signature);
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
                checkDescriptor(descriptor);
                checkDescriptor(signature);
                if (exceptions != null) {
                    for (String exception : exceptions) check(exception);
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        check(type);
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        check(owner);
                        checkDescriptor(descriptor);
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        check(owner);
                        checkDescriptor(descriptor);
                    }
                };
            }

            private void check(String owner) {
                if (owner != null && (owner.startsWith("net/fabricmc/")
                        || owner.startsWith("net/minecraftforge/")
                        || owner.startsWith("ru/feytox/etherology/client/"))) {
                    forbidden.add(owner);
                }
            }

            private void checkDescriptor(String descriptor) {
                if (descriptor != null && (descriptor.contains("Lnet/fabricmc/")
                        || descriptor.contains("Lnet/minecraftforge/")
                        || descriptor.contains("Lru/feytox/etherology/client/"))) {
                    forbidden.add(descriptor);
                }
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(Set.of(), forbidden, description);
    }

    private static void assertLegacyFieldsAbsent(
            String description,
            ClassReader reader,
            Set<String> forbiddenFields
    ) {
        Set<String> present = new LinkedHashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if (forbiddenFields.contains(name)) present.add(name);
                return null;
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(Set.of(), present, description);
    }

    private static List<String> methodInvocations(ClassReader reader, String methodName) {
        List<String> invocations = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
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
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        invocations.add(owner + "#" + name + descriptor);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return invocations;
    }

    private static void assertResource(
            Artifact artifact,
            JarFile jar,
            String entryName,
            Path canonicalFile
    ) throws IOException {
        JarEntry entry = jar.getJarEntry(entryName);
        if (!artifact.includesAssets()) {
            assertEquals(null, entry, artifact.description() + ":" + entryName);
            return;
        }
        assertNotNull(entry, artifact.description() + ":" + entryName);
        assertArrayEquals(
                Files.readAllBytes(canonicalFile),
                jar.getInputStream(entry).readAllBytes(),
                artifact.description() + ":" + entryName
        );
    }

    private static JsonObject parseObject(String json) {
        JsonElement element = JsonParser.parseString(json);
        assertTrue(element.isJsonObject());
        return element.getAsJsonObject();
    }

    private static ClassReader classReader(JarFile jar, String entryName) throws IOException {
        JarEntry entry = jar.getJarEntry(entryName);
        assertNotNull(entry, entryName);
        try (var input = jar.getInputStream(entry)) {
            return new ClassReader(input);
        }
    }

    private static Path requiredPath(String propertyName) throws IOException {
        String value = System.getProperty(propertyName);
        assertNotNull(value, propertyName);
        Path path = Path.of(value);
        assertTrue(Files.exists(path, LinkOption.NOFOLLOW_LINKS), path.toString());
        assertFalse(Files.isSymbolicLink(path), path.toString());
        return path.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private static void requireRegularFile(Path path) {
        assertTrue(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), path.toString());
        assertFalse(Files.isSymbolicLink(path), path.toString());
    }

    private static List<Artifact> artifacts() throws IOException {
        return List.of(
                artifact("commonJar", "common JAR", false, false),
                artifact(
                        "fabricTransformedCommonJar",
                        "Fabric-transformed common JAR",
                        false,
                        false
                ),
                artifact(
                        "forgeTransformedCommonJar",
                        "Forge-transformed common JAR",
                        false,
                        false
                ),
                artifact(
                        "fabricDevelopmentJar",
                        "Fabric development JAR",
                        true,
                        true
                ),
                artifact(
                        "fabricProductionJar",
                        "Fabric remapped production JAR",
                        true,
                        true
                ),
                artifact("forgeShadowJar", "Forge shadow JAR", true, false)
        );
    }

    private static Artifact artifact(
            String suffix,
            String description,
            boolean includesAssets,
            boolean fabricApplication
    ) throws IOException {
        Path path = requiredPath("etherology.materialItems." + suffix);
        requireRegularFile(path);
        return new Artifact(path, description, includesAssets, fabricApplication);
    }

    private static Map<String, MaterialItem> materials() {
        Map<String, MaterialItem> materials = new LinkedHashMap<>();
        add(materials, "ETHEROSCOPE", "etheroscope", "Etheroscope");
        add(materials, "THUJA_OIL", "thuja_oil", "Thuja Oil");
        add(materials, "AZEL_INGOT", "azel_ingot", "Azel Ingot");
        add(materials, "AZEL_NUGGET", "azel_nugget", "Azel Nugget");
        add(materials, "ETHRIL_INGOT", "ethril_ingot", "Ethril Ingot");
        add(materials, "ETHRIL_NUGGET", "ethril_nugget", "Ethril Nugget");
        add(materials, "EBONY_INGOT", "ebony_ingot", "Ebony Ingot");
        add(materials, "EBONY_NUGGET", "ebony_nugget", "Ebony Nugget");
        add(
                materials,
                "ENRICHED_ATTRAHITE",
                "enriched_attrahite",
                "Enriched Attrahite"
        );
        add(materials, "RAW_AZEL", "raw_azel", "Raw Azel");
        add(materials, "ATTRAHITE_BRICK", "attrahite_brick", "Attrahite Brick");
        add(materials, "BINDER", "binder", "Binder");
        add(materials, "EBONY", "ebony", "Ebony");
        add(materials, "RESONATING_WAND", "resonating_wand", "Resonating Wand");
        return Collections.unmodifiableMap(materials);
    }

    private static void add(
            Map<String, MaterialItem> materials,
            String fieldName,
            String id,
            String englishName
    ) {
        materials.put(id, new MaterialItem(fieldName, id, englishName));
    }

    private record MaterialItem(String fieldName, String id, String englishName) {
    }

    private record Artifact(
            Path path,
            String description,
            boolean includesAssets,
            boolean fabricApplication
    ) {

        private JarFile open() throws IOException {
            return new JarFile(path.toFile());
        }
    }
}
