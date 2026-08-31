package ru.feytox.etherology.forge;

import com.google.gson.JsonArray;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ParticleRegistryResourcesTest {

    private static final String SHARED_PARTICLES =
            "ru/feytox/etherology/registry/particle/SharedParticleTypes.class";
    private static final String SHARED_PARTICLES_OWNER =
            "ru/feytox/etherology/registry/particle/SharedParticleTypes";
    private static final String LEGACY_PARTICLES =
            "ru/feytox/etherology/registry/particle/EtherParticleTypes.class";
    private static final String LEGACY_PARTICLES_OWNER =
            "ru/feytox/etherology/registry/particle/EtherParticleTypes";
    private static final String REGISTRY_SUPPLIER =
            "dev/architectury/registry/registries/RegistrySupplier";
    private static final String PARTICLE_JSON_PREFIX =
            "assets/etherology/particles/";
    private static final String PARTICLE_TEXTURE_PREFIX =
            "assets/etherology/textures/particle/";

    private static final List<String> PARTICLE_FIELDS = List.of(
            "LIGHT",
            "STEAM",
            "SPARK",
            "ELECTRICITY1",
            "ELECTRICITY2",
            "ITEM",
            "RISING",
            "VITAL",
            "SHOCKWAVE",
            "GLINT",
            "ENERGY_ABSORPTION",
            "ARMILLARY_SPHERE",
            "HAZE",
            "ALCHEMY",
            "ETHER_STAR",
            "ETHER_DOT",
            "RESONATION",
            "LIGHTNING_BOLT",
            "SCALABLE_SWEEP",
            "REDSTONE_FLASH",
            "REDSTONE_STREAM",
            "SEAL"
    );
    private static final List<String> PARTICLE_IDS = List.of(
            "light",
            "steam",
            "spark",
            "electricity1",
            "electricity2",
            "item",
            "rising",
            "vital",
            "shockwave",
            "glint_particle",
            "energy_absorption",
            "armillary_sphere",
            "haze",
            "alchemy",
            "ether_star",
            "ether_dot",
            "resonation",
            "lightning_bolt",
            "scalable_sweep",
            "redstone_flash",
            "redstone_stream",
            "seal"
    );
    private static final Set<String> PARTICLE_JSONS = Set.of(
            "alchemy.json",
            "armillary_sphere.json",
            "electricity1.json",
            "electricity2.json",
            "energy_absorption.json",
            "ether_dot.json",
            "ether_star.json",
            "glint_particle.json",
            "haze.json",
            "light.json",
            "lightning_bolt.json",
            "redstone_flash.json",
            "redstone_stream.json",
            "resonation.json",
            "rising.json",
            "scalable_sweep.json",
            "seal.json",
            "shockwave.json",
            "spark.json",
            "steam.json",
            "vital.json"
    );
    private static final Set<String> SEAL_TEXTURES = Set.of(
            "assets/etherology/textures/block/keta_seal.png",
            "assets/etherology/textures/block/keta_seal_light.png",
            "assets/etherology/textures/block/rella_seal.png",
            "assets/etherology/textures/block/rella_seal_light.png",
            "assets/etherology/textures/block/via_seal.png",
            "assets/etherology/textures/block/via_seal_light.png",
            "assets/etherology/textures/block/clos_seal.png",
            "assets/etherology/textures/block/clos_seal_light.png"
    );
    private static final List<String> MOVED_CLASSES = List.of(
            SHARED_PARTICLES,
            "ru/feytox/etherology/magic/seal/SealType.class",
            "ru/feytox/etherology/util/misc/RGBColor.class",
            "ru/feytox/etherology/particle/effects/misc/FeyParticleEffect.class",
            "ru/feytox/etherology/particle/effects/misc/"
                    + "FeyParticleEffect$DummyConstructor.class",
            "ru/feytox/etherology/particle/effects/misc/FeyParticleType.class",
            "ru/feytox/etherology/particle/effects/ElectricityParticleEffect.class",
            "ru/feytox/etherology/particle/effects/ItemParticleEffect.class",
            "ru/feytox/etherology/particle/effects/LightParticleEffect.class",
            "ru/feytox/etherology/particle/effects/MovingParticleEffect.class",
            "ru/feytox/etherology/particle/effects/ScalableParticleEffect.class",
            "ru/feytox/etherology/particle/effects/SealParticleEffect.class",
            "ru/feytox/etherology/particle/effects/SimpleParticleEffect.class",
            "ru/feytox/etherology/particle/effects/SparkParticleEffect.class",
            "ru/feytox/etherology/particle/subtype/ElectricitySubtype.class",
            "ru/feytox/etherology/particle/subtype/LightSubtype.class",
            "ru/feytox/etherology/particle/subtype/SparkSubtype.class"
    );

    @Test
    void everyProductionArtifactHasOneExactCommonOwner() throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entries = jar.stream().map(JarEntry::getName).toList();
                for (String movedClass : MOVED_CLASSES) {
                    assertEquals(1, count(entries, movedClass), artifact.description());
                }
                assertEquals(0, count(entries, LEGACY_PARTICLES), artifact.description());
                assertExactSharedCatalog(
                        artifact.description(),
                        classReader(jar, SHARED_PARTICLES)
                );
                assertNoUnsafeCommonDependencies(artifact.description(), jar);
                assertAllConsumersResolveSuppliers(artifact.description(), jar, entries);
                assertEquals(
                        artifact.fabricApplication() ? 1 : 0,
                        count(
                                entries,
                                "ru/feytox/etherology/client/registry/"
                                        + "ClientParticleRegistry.class"
                        ),
                        artifact.description()
                );
            }
        }
    }

    @Test
    void packagedParticleDefinitionsAndTexturesMatchCanonicalBytes()
            throws IOException {
        Path repositoryRoot = requiredPath("etherology.particles.repositoryRoot");
        Path assetRoot = repositoryRoot.resolve("src/client/resources/assets/etherology");
        Path jsonRoot = assetRoot.resolve("particles");
        Path textureRoot = assetRoot.resolve("textures/particle");

        Set<String> canonicalJsonNames = regularFileNames(jsonRoot, ".json");
        assertEquals(PARTICLE_JSONS, canonicalJsonNames);
        assertFalse(canonicalJsonNames.contains("item.json"));

        Set<String> allCanonicalParticleTextures = regularRelativeEntries(
                repositoryRoot.resolve("src/client/resources"),
                textureRoot,
                ".png"
        );
        assertEquals(134, allCanonicalParticleTextures.size());

        Set<String> referencedParticleTextures = referencedParticleTextures(jsonRoot);
        assertEquals(130, referencedParticleTextures.size());
        assertTrue(allCanonicalParticleTextures.containsAll(referencedParticleTextures));

        for (String sealTexture : SEAL_TEXTURES) {
            assertRegularCanonicalResource(repositoryRoot, sealTexture);
        }

        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                Set<String> packagedJsons = entriesBelow(jar, PARTICLE_JSON_PREFIX, ".json");
                Set<String> packagedParticleTextures = entriesBelow(
                        jar,
                        PARTICLE_TEXTURE_PREFIX,
                        ".png"
                );
                Set<String> packagedSealTextures = entriesNamed(jar, SEAL_TEXTURES);

                if (!artifact.includesAssets()) {
                    assertEquals(Set.of(), packagedJsons, artifact.description());
                    assertEquals(Set.of(), packagedParticleTextures, artifact.description());
                    assertEquals(Set.of(), packagedSealTextures, artifact.description());
                    continue;
                }

                assertEquals(
                        prefixed(PARTICLE_JSON_PREFIX, PARTICLE_JSONS),
                        packagedJsons,
                        artifact.description()
                );
                assertEquals(
                        allCanonicalParticleTextures,
                        packagedParticleTextures,
                        artifact.description()
                );
                assertEquals(SEAL_TEXTURES, packagedSealTextures, artifact.description());

                for (String entry : packagedJsons) {
                    assertCanonicalBytes(repositoryRoot, jar, entry, artifact.description());
                }
                for (String entry : allCanonicalParticleTextures) {
                    assertCanonicalBytes(repositoryRoot, jar, entry, artifact.description());
                }
                for (String entry : SEAL_TEXTURES) {
                    assertCanonicalBytes(repositoryRoot, jar, entry, artifact.description());
                }
            }
        }
    }

    @Test
    void canonicalFabricSourcesNoLongerOwnMovedClassesOrTheLegacyRegistry()
            throws IOException {
        Path root = requiredPath("etherology.particles.repositoryRoot");
        List<String> legacySources = new ArrayList<>();
        legacySources.add(
                "src/main/java/ru/feytox/etherology/registry/particle/"
                        + "EtherParticleTypes.java"
        );
        legacySources.add("src/main/java/ru/feytox/etherology/magic/seal/SealType.java");
        legacySources.add("src/main/java/ru/feytox/etherology/util/misc/RGBColor.java");
        for (String movedClass : MOVED_CLASSES) {
            if (!movedClass.startsWith("ru/feytox/etherology/particle/")
                    || movedClass.contains("$")) {
                continue;
            }
            legacySources.add("src/main/java/" + movedClass.replace(".class", ".java"));
        }

        for (String legacySource : legacySources) {
            assertFalse(
                    Files.exists(root.resolve(legacySource), LinkOption.NOFOLLOW_LINKS),
                    legacySource
            );
        }

        assertTrue(Files.isRegularFile(root.resolve(
                "common/src/main/java/ru/feytox/etherology/registry/particle/"
                        + "SharedParticleTypes.java"
        )));
        assertNull(classLoaderResource(LEGACY_PARTICLES));
        for (String movedClass : MOVED_CLASSES) {
            assertNotNull(classLoaderResource(movedClass), movedClass);
        }
    }

    private static void assertExactSharedCatalog(String description, ClassReader reader) {
        List<String> publicSupplierFields = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if ((access & Opcodes.ACC_PUBLIC) != 0
                        && descriptor.equals(
                                "Ldev/architectury/registry/registries/RegistrySupplier;"
                        )) {
                    publicSupplierFields.add(name);
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
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String stringValue) {
                            ids.add(stringValue);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(PARTICLE_FIELDS, publicSupplierFields, description);
        assertEquals(PARTICLE_IDS, ids, description);
    }

    private static void assertNoUnsafeCommonDependencies(
            String description,
            JarFile jar
    ) throws IOException {
        Set<String> forbiddenOwners = Set.of(
                "ru/feytox/etherology/registry/item/EItems",
                "ru/feytox/etherology/registry/block/EBlocks",
                "ru/feytox/etherology/util/misc/EIdentifier"
        );
        for (String classEntry : MOVED_CLASSES) {
            Set<String> owners = referencedOwners(classReader(jar, classEntry));
            assertFalse(
                    owners.stream().anyMatch(owner ->
                            owner.startsWith("net/minecraft/client/")
                                    || owner.startsWith("net/fabricmc/")
                                    || forbiddenOwners.contains(owner)
                    ),
                    description + ": " + classEntry + " -> " + owners
            );
        }
    }

    private static void assertAllConsumersResolveSuppliers(
            String description,
            JarFile jar,
            List<String> entries
    ) throws IOException {
        List<String> unresolved = new ArrayList<>();
        List<String> classInitializerReads = new ArrayList<>();
        List<String> legacyReferences = new ArrayList<>();
        int[] reads = {0};
        int[] resolutions = {0};

        for (String entry : entries) {
            if (!entry.endsWith(".class")) {
                continue;
            }
            classReader(jar, entry).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String methodName,
                        String descriptor,
                        String signature,
                        String[] exceptions
                ) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        private String pendingField;

                        @Override
                        public void visitFieldInsn(
                                int opcode,
                                String owner,
                                String name,
                                String descriptor
                        ) {
                            if (owner.equals(LEGACY_PARTICLES_OWNER)) {
                                legacyReferences.add(entry + "#" + methodName);
                            }
                            if (opcode == Opcodes.GETSTATIC
                                    && owner.equals(SHARED_PARTICLES_OWNER)
                                    && PARTICLE_FIELDS.contains(name)) {
                                if (pendingField != null) {
                                    unresolved.add(entry + "#" + methodName
                                            + ":" + pendingField);
                                }
                                pendingField = name;
                                reads[0]++;
                                if (methodName.equals("<clinit>")) {
                                    classInitializerReads.add(entry + ":" + name);
                                }
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
                            if (owner.equals(LEGACY_PARTICLES_OWNER)) {
                                legacyReferences.add(entry + "#" + methodName);
                            }
                            if (pendingField == null) {
                                return;
                            }
                            if (owner.equals(REGISTRY_SUPPLIER) && name.equals("get")) {
                                resolutions[0]++;
                            } else {
                                unresolved.add(entry + "#" + methodName
                                        + ":" + pendingField);
                            }
                            pendingField = null;
                        }

                        @Override
                        public void visitEnd() {
                            if (pendingField != null) {
                                unresolved.add(entry + "#" + methodName
                                        + ":" + pendingField);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        assertEquals(List.of(), unresolved, description);
        assertEquals(List.of(), classInitializerReads, description);
        assertEquals(List.of(), legacyReferences, description);
        assertEquals(reads[0], resolutions[0], description);
        assertTrue(reads[0] >= 2, description);
    }

    private static Set<String> referencedOwners(ClassReader reader) {
        Set<String> owners = new LinkedHashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
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
                    public void visitTypeInsn(int opcode, String type) {
                        owners.add(type);
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        owners.add(owner);
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        owners.add(owner);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return owners;
    }

    private static Set<String> referencedParticleTextures(Path jsonRoot)
            throws IOException {
        Set<String> textures = new LinkedHashSet<>();
        for (String jsonName : PARTICLE_JSONS.stream().sorted().toList()) {
            Path jsonPath = jsonRoot.resolve(jsonName);
            assertTrue(Files.isRegularFile(jsonPath), jsonPath.toString());
            assertFalse(Files.isSymbolicLink(jsonPath), jsonPath.toString());
            try (InputStream stream = Files.newInputStream(jsonPath);
                    InputStreamReader reader = new InputStreamReader(
                            stream,
                            StandardCharsets.UTF_8
                    )) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                assertEquals(Set.of("textures"), json.keySet(), jsonName);
                JsonArray textureArray = json.getAsJsonArray("textures");
                assertFalse(textureArray.isEmpty(), jsonName);
                for (JsonElement element : textureArray) {
                    String id = element.getAsString();
                    int separator = id.indexOf(':');
                    assertTrue(separator > 0, id);
                    String namespace = id.substring(0, separator);
                    String path = id.substring(separator + 1);
                    assertTrue(namespace.equals("etherology")
                            || namespace.equals("minecraft"), id);
                    if (namespace.equals("etherology")) {
                        textures.add(PARTICLE_TEXTURE_PREFIX + path + ".png");
                    }
                }
            }
        }
        return textures;
    }

    private static Set<String> regularFileNames(Path directory, String suffix)
            throws IOException {
        assertTrue(Files.isDirectory(directory), directory.toString());
        Set<String> names = new LinkedHashSet<>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.sorted().toList()) {
                if (!path.getFileName().toString().endsWith(suffix)) {
                    continue;
                }
                assertTrue(Files.isRegularFile(path), path.toString());
                assertFalse(Files.isSymbolicLink(path), path.toString());
                names.add(path.getFileName().toString());
            }
        }
        return names;
    }

    private static Set<String> regularRelativeEntries(
            Path resourceRoot,
            Path directory,
            String suffix
    ) throws IOException {
        assertTrue(Files.isDirectory(directory), directory.toString());
        Set<String> entries = new LinkedHashSet<>();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted().toList()) {
                if (!path.getFileName().toString().endsWith(suffix)) {
                    continue;
                }
                assertTrue(Files.isRegularFile(path), path.toString());
                assertFalse(Files.isSymbolicLink(path), path.toString());
                entries.add(resourceRoot.relativize(path).toString().replace('\\', '/'));
            }
        }
        return entries;
    }

    private static Set<String> entriesBelow(
            JarFile jar,
            String prefix,
            String suffix
    ) {
        Set<String> entries = new LinkedHashSet<>();
        jar.stream()
                .map(JarEntry::getName)
                .filter(name -> name.startsWith(prefix) && name.endsWith(suffix))
                .forEach(entries::add);
        return entries;
    }

    private static Set<String> entriesNamed(JarFile jar, Set<String> names) {
        Set<String> entries = new LinkedHashSet<>();
        jar.stream()
                .map(JarEntry::getName)
                .filter(names::contains)
                .forEach(entries::add);
        return entries;
    }

    private static Set<String> prefixed(String prefix, Set<String> names) {
        Set<String> entries = new LinkedHashSet<>();
        names.stream().sorted().map(prefix::concat).forEach(entries::add);
        return entries;
    }

    private static void assertRegularCanonicalResource(Path root, String entry) {
        Path path = root.resolve("src/client/resources").resolve(entry);
        assertTrue(Files.isRegularFile(path), path.toString());
        assertFalse(Files.isSymbolicLink(path), path.toString());
    }

    private static void assertCanonicalBytes(
            Path root,
            JarFile jar,
            String entry,
            String description
    ) throws IOException {
        Path canonical = root.resolve("src/client/resources").resolve(entry);
        assertTrue(Files.isRegularFile(canonical), canonical.toString());
        JarEntry jarEntry = jar.getJarEntry(entry);
        assertNotNull(jarEntry, description + ": " + entry);
        try (InputStream stream = jar.getInputStream(jarEntry)) {
            assertArrayEquals(
                    Files.readAllBytes(canonical),
                    stream.readAllBytes(),
                    description + ": " + entry
            );
        }
    }

    private static ClassReader classReader(JarFile jar, String entry)
            throws IOException {
        JarEntry jarEntry = jar.getJarEntry(entry);
        assertNotNull(jarEntry, "Missing packaged class " + entry);
        try (InputStream stream = jar.getInputStream(jarEntry)) {
            return new ClassReader(stream);
        }
    }

    private static List<Artifact> artifacts() {
        return List.of(
                new Artifact(
                        "Common JAR",
                        requiredPath("etherology.particles.commonJar"),
                        false,
                        false
                ),
                new Artifact(
                        "Fabric-transformed Common JAR",
                        requiredPath("etherology.particles.fabricTransformedCommonJar"),
                        false,
                        false
                ),
                new Artifact(
                        "Forge-transformed Common JAR",
                        requiredPath("etherology.particles.forgeTransformedCommonJar"),
                        false,
                        false
                ),
                new Artifact(
                        "Fabric development JAR",
                        requiredPath("etherology.particles.fabricDevelopmentJar"),
                        true,
                        true
                ),
                new Artifact(
                        "Fabric production JAR",
                        requiredPath("etherology.particles.fabricProductionJar"),
                        true,
                        true
                ),
                new Artifact(
                        "Forge shadow JAR",
                        requiredPath("etherology.particles.forgeShadowJar"),
                        false,
                        true
                )
        );
    }

    private static Path requiredPath(String propertyName) {
        String property = System.getProperty(propertyName);
        assertNotNull(property, "Missing Gradle test property " + propertyName);
        return Path.of(property);
    }

    private static java.net.URL classLoaderResource(String entry) {
        return ParticleRegistryResourcesTest.class.getClassLoader().getResource(entry);
    }

    private static long count(List<String> entries, String expected) {
        return entries.stream().filter(expected::equals).count();
    }

    private record Artifact(
            String description,
            Path path,
            boolean fabricApplication,
            boolean includesAssets
    ) {

        private JarFile open() throws IOException {
            assertTrue(Files.isRegularFile(path), description + " is missing");
            assertFalse(Files.isSymbolicLink(path), description + " is linked");
            return new JarFile(path.toFile());
        }
    }
}
