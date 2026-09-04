package ru.feytox.etherology.forge;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AspectFoundationCrossArtifactTest {

    private static final String CLASS_PREFIX = "ru/feytox/etherology/";
    private static final String ASPECT =
            CLASS_PREFIX + "magic/aspects/Aspect.class";
    private static final String ASPECT_OWNER =
            CLASS_PREFIX + "magic/aspects/Aspect";
    private static final String ETHEROLOGY_ASPECT =
            CLASS_PREFIX + "magic/aspects/EtherologyAspect.class";
    private static final String ASPECT_CONTAINER =
            CLASS_PREFIX + "magic/aspects/AspectContainer.class";
    private static final String ASPECT_CONTAINER_OWNER =
            CLASS_PREFIX + "magic/aspects/AspectContainer";
    private static final String ALCHEMY_SERIALIZER =
            CLASS_PREFIX + "recipes/alchemy/AlchemyRecipeSerializer.class";
    private static final String FABRIC_INITIALIZER = CLASS_PREFIX + "Etherology.class";

    private static final Set<String> SHARED_CLASSES = Set.of(
            ASPECT,
            ETHEROLOGY_ASPECT,
            ASPECT_CONTAINER
    );
    private static final List<String> ASPECT_NAMES = List.of(
            "RELLA", "ETHA", "DIZORD", "VACUO", "NETHA", "GRAVIA", "MOUNTA",
            "SOWORDA", "CLOS", "ENN", "ANEMA", "VIBRA", "MATERRA", "SOLISTA",
            "DEFENTA", "FELKA", "VIA", "FLIMA", "AREA", "CHAOS", "GEMA",
            "DOGMA", "HENDALL", "STRALFA", "KETA", "MORA", "MEMO", "DEVO",
            "SECRA", "ISKIL", "ALCHEMA", "GROSEAL", "VITER", "TALO", "AZU",
            "FRADO", "SOCE", "PLANTA", "LUMOS", "NOX"
    );
    private static final List<String> LEGACY_SOURCE_PATHS = List.of(
            "src/main/java/ru/feytox/etherology/magic/aspects/Aspect.java",
            "src/main/java/ru/feytox/etherology/magic/aspects/EtherologyAspect.java",
            "src/main/java/ru/feytox/etherology/magic/aspects/AspectContainer.java"
    );

    @Test
    void everyArtifactContainsOneExactSharedAspectInventory() throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entries = jar.stream().map(JarEntry::getName).toList();
                for (String sharedClass : SHARED_CLASSES) {
                    assertEquals(
                            1,
                            entries.stream().filter(sharedClass::equals).count(),
                            artifact.description() + ":" + sharedClass
                    );
                }
                assertEquals(
                        SHARED_CLASSES,
                        entries.stream()
                                .filter(AspectFoundationCrossArtifactTest::isSharedClass)
                                .collect(java.util.stream.Collectors.toSet()),
                        artifact.description()
                );
                assertEquals(
                        1,
                        entries.stream().filter(ALCHEMY_SERIALIZER::equals).count(),
                        artifact.description()
                );

                if (artifact.fabricApplication()) {
                    assertEquals(
                            1,
                            entries.stream().filter(FABRIC_INITIALIZER::equals).count(),
                            artifact.description()
                    );
                } else {
                    assertFalse(entries.contains(FABRIC_INITIALIZER),
                            artifact.description());
                }
            }
        }
    }

    @Test
    void canonicalEnumOrderAndContainerSurfaceSurviveEveryTransform()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                assertEquals(
                        ASPECT_NAMES,
                        enumConstants(classReader(jar, ASPECT)),
                        artifact.description()
                );
                Set<String> methods = publicStableContainerMethods(
                        classReader(jar, ASPECT_CONTAINER)
                );
                assertEquals(expectedStableContainerMethods(), methods,
                        artifact.description());
            }
        }
    }

    @Test
    void sharedTypesStayLoaderNeutralWithoutCallingLegacyFabricHelpers()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                for (String sharedClass : SHARED_CLASSES) {
                    Set<String> forbidden = forbiddenReferences(
                            classReader(jar, sharedClass)
                    );
                    assertEquals(Set.of(), forbidden,
                            artifact.description() + ":" + sharedClass);
                }
            }
        }
    }

    @Test
    void loggerCategoryAndIdentityEqualityRemainExplicitAcrossArtifacts()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> loggerCategories = new ArrayList<>();
                List<String> equalityMethods = new ArrayList<>();
                classReader(jar, ASPECT_CONTAINER).accept(
                        new ClassVisitor(Opcodes.ASM9) {
                            @Override
                            public MethodVisitor visitMethod(
                                    int access,
                                    String name,
                                    String descriptor,
                                    String signature,
                                    String[] exceptions
                            ) {
                                if (name.equals("equals") || name.equals("hashCode")) {
                                    equalityMethods.add(name + descriptor);
                                }
                                return new MethodVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visitLdcInsn(Object value) {
                                        if (value.equals(
                                                "ru.feytox.etherology.Etherology"
                                        )) {
                                            loggerCategories.add(value.toString());
                                        }
                                    }
                                };
                            }
                        },
                        ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
                );
                assertEquals(List.of(), equalityMethods, artifact.description());
                assertEquals(
                        List.of("ru.feytox.etherology.Etherology"),
                        loggerCategories,
                        artifact.description()
                );
            }
        }
    }

    @Test
    void onlyCommonSourceRootOwnsTheThreeCanonicalFqns() throws IOException {
        Path repositoryRoot = requiredPath("etherology.aspectFoundation.repositoryRoot");
        assertTrue(Files.isDirectory(repositoryRoot, LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.isSymbolicLink(repositoryRoot));

        for (String legacyPath : LEGACY_SOURCE_PATHS) {
            Path path = repositoryRoot.resolve(legacyPath).normalize();
            assertTrue(path.startsWith(repositoryRoot), path.toString());
            assertFalse(Files.exists(path, LinkOption.NOFOLLOW_LINKS), path.toString());
        }
        for (String name : List.of(
                "Aspect.java",
                "EtherologyAspect.java",
                "AspectContainer.java"
        )) {
            Path path = repositoryRoot.resolve(
                    "common/src/main/java/ru/feytox/etherology/magic/aspects/" + name
            ).normalize();
            assertTrue(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS),
                    path.toString());
            assertFalse(Files.isSymbolicLink(path), path.toString());
        }
    }

    private static List<String> enumConstants(ClassReader reader) {
        List<String> constants = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if ((access & Opcodes.ACC_ENUM) != 0
                        && descriptor.equals("L" + ASPECT_OWNER + ";")) {
                    constants.add(name);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return constants;
    }

    private static Set<String> publicStableContainerMethods(ClassReader reader) {
        Set<String> methods = new LinkedHashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if ((access & Opcodes.ACC_PUBLIC) == 0) return null;
                String method = name + descriptor;
                if (expectedStableContainerMethods().contains(method)) {
                    methods.add(method);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return methods;
    }

    private static Set<String> expectedStableContainerMethods() {
        return Set.of(
                "<init>()V",
                "<init>(Ljava/util/Map;)V",
                "<init>(Ljava/util/Map;Z)V",
                "of(L" + ASPECT_OWNER + ";I)L" + ASPECT_CONTAINER_OWNER + ";",
                "isEmpty()Z",
                "add(L" + ASPECT_CONTAINER_OWNER + ";)L"
                        + ASPECT_CONTAINER_OWNER + ";",
                "subtract(L" + ASPECT_CONTAINER_OWNER + ";)L"
                        + ASPECT_CONTAINER_OWNER + ";",
                "getMutableAspects()Lit/unimi/dsi/fastutil/objects/"
                        + "Object2IntOpenHashMap;",
                "map(Ljava/util/function/Function;)L" + ASPECT_CONTAINER_OWNER + ";",
                "clearZeros(Ljava/util/Map;)Ljava/util/Map;",
                "max()Ljava/util/Optional;",
                "sum()Ljava/util/Optional;",
                "sorted(ZI)Ljava/util/List;",
                "getAspects()Lcom/google/common/collect/ImmutableMap;"
        );
    }

    private static Set<String> forbiddenReferences(ClassReader reader) {
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
                check(signature);
                check(superName);
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
                check(descriptor);
                check(signature);
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
                check(descriptor);
                check(signature);
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
                        check(descriptor);
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
                        check(descriptor);
                    }
                };
            }

            private void check(String reference) {
                if (reference == null) return;
                if (reference.contains("net/fabricmc/")
                        || reference.contains("net/minecraftforge/")
                        || reference.contains("lombok/")
                        || reference.contains(CLASS_PREFIX
                                + "util/misc/EIdentifier")
                        || reference.equals(CLASS_PREFIX + "Etherology")) {
                    forbidden.add(reference);
                }
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return forbidden;
    }

    private static boolean isSharedClass(String entry) {
        return SHARED_CLASSES.contains(entry);
    }

    private static ClassReader classReader(JarFile jar, String entryName)
            throws IOException {
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

    private static List<Artifact> artifacts() throws IOException {
        return List.of(
                artifact("commonJar", "common JAR", false),
                artifact(
                        "fabricTransformedCommonJar",
                        "Fabric-transformed common JAR",
                        false
                ),
                artifact(
                        "forgeTransformedCommonJar",
                        "Forge-transformed common JAR",
                        false
                ),
                artifact("fabricDevelopmentJar", "Fabric development JAR", true),
                artifact("fabricProductionJar", "Fabric remapped production JAR", true),
                artifact("forgeShadowJar", "Forge shadow JAR", false)
        );
    }

    private static Artifact artifact(
            String suffix,
            String description,
            boolean fabricApplication
    ) throws IOException {
        Path path = requiredPath("etherology.aspectFoundation." + suffix);
        assertTrue(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), path.toString());
        assertFalse(Files.isSymbolicLink(path), path.toString());
        return new Artifact(path, description, fabricApplication);
    }

    private record Artifact(
            Path path,
            String description,
            boolean fabricApplication
    ) {

        private JarFile open() throws IOException {
            return new JarFile(path.toFile());
        }
    }
}
