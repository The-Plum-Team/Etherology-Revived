package ru.feytox.etherology.magic.aspects;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AspectDataRegistryOwnershipTest {

    private static final String CLASS_PREFIX = "ru/feytox/etherology/";
    private static final List<String> MOVED_CLASSES = List.of(
            "data/aspects/AspectsLoader",
            "magic/aspects/AspectContainerId",
            "magic/aspects/AspectContainerType",
            "magic/aspects/AspectEntry",
            "magic/aspects/AspectRegistryPart",
            "magic/aspects/RevelationAspectProvider",
            "registry/misc/SharedAspectRegistries"
    );
    private static final List<String> FORBIDDEN_REFERENCES = List.of(
            "lombok/",
            "net/fabricmc/",
            "net/minecraftforge/",
            "net/neoforged/",
            "ru/feytox/etherology/Etherology",
            "ru/feytox/etherology/registry/misc/RegistriesRegistry"
    );
    private static final List<String> PRODUCTION_ROOTS = List.of(
            "common/src/main/java",
            "src/main/java",
            "fabric/src/main/java",
            "forge/src/main/java"
    );

    @Test
    void sharedRegistryKeyBytecodeConstructsExactlyEtherologyAspects()
            throws IOException {
        List<String> strings = new ArrayList<>();
        List<String> invocations = new ArrayList<>();
        String resource = "/" + CLASS_PREFIX
                + "registry/misc/SharedAspectRegistries.class";
        try (var stream = getClass().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions
                ) {
                    if (!name.equals("<clinit>")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitLdcInsn(Object value) {
                            if (value instanceof String string) strings.add(string);
                        }

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
        }

        assertEquals(List.of("etherology", "aspects"), strings);
        assertEquals(
                List.of(
                        "net/minecraft/util/Identifier#<init>"
                                + "(Ljava/lang/String;Ljava/lang/String;)V",
                        "net/minecraft/registry/RegistryKey#ofRegistry"
                                + "(Lnet/minecraft/util/Identifier;)"
                                + "Lnet/minecraft/registry/RegistryKey;"
                ),
                invocations
        );
    }

    @Test
    void commonIsTheOnlySourceAndBytecodeOwnerOfTheAspectBridge()
            throws IOException {
        Path repositoryRoot = repositoryRoot();

        for (String className : MOVED_CLASSES) {
            String source = className + ".java";
            List<Path> owners = PRODUCTION_ROOTS.stream()
                    .map(root -> repositoryRoot.resolve(root).resolve(
                            CLASS_PREFIX + source
                    ))
                    .filter(Files::isRegularFile)
                    .toList();
            assertEquals(
                    List.of(repositoryRoot
                            .resolve("common/src/main/java")
                            .resolve(CLASS_PREFIX + source)),
                    owners,
                    source
            );

            String resource = CLASS_PREFIX + className + ".class";
            List<URL> bytecodeOwners = Collections.list(
                    getClass().getClassLoader().getResources(resource)
            );
            assertEquals(1, bytecodeOwners.size(), resource);
        }
    }

    @Test
    void commonAspectBridgeHasNoLoaderLombokOrLegacyRootDependencies()
            throws IOException {
        for (String className : MOVED_CLASSES) {
            String resource = "/" + CLASS_PREFIX + className + ".class";
            try (var stream = getClass().getResourceAsStream(resource)) {
                assertNotNull(stream, resource);
                String constants = new String(
                        stream.readAllBytes(),
                        StandardCharsets.ISO_8859_1
                );
                for (String forbidden : FORBIDDEN_REFERENCES) {
                    assertFalse(
                            constants.contains(forbidden),
                            className + " unexpectedly references " + forbidden
                    );
                }
            }
        }
    }

    @Test
    void commonOwnsExactlyTheTwoCanonicalAspectResources() throws IOException {
        Path repositoryRoot = repositoryRoot();
        Path canonicalRoot = repositoryRoot.resolve(
                "common/src/main/resources/data/etherology/etherology/aspects"
        );
        try (var canonicalFiles = Files.list(canonicalRoot)) {
            assertEquals(
                    List.of("etherology.json", "vanilla.json"),
                    canonicalFiles
                            .filter(Files::isRegularFile)
                            .map(path -> path.getFileName().toString())
                            .sorted()
                            .toList()
            );
        }

        Path legacyRoot = repositoryRoot.resolve(
                "src/main/resources/data/etherology/etherology/aspects"
        );
        if (Files.isDirectory(legacyRoot)) {
            try (var legacyFiles = Files.list(legacyRoot)) {
                assertEquals(0, legacyFiles.filter(Files::isRegularFile).count());
            }
        }

        for (String name : List.of("etherology.json", "vanilla.json")) {
            String resource = "data/etherology/etherology/aspects/" + name;
            List<URL> owners = Collections.list(
                    getClass().getClassLoader().getResources(resource)
            );
            assertEquals(1, owners.size(), resource);
        }
    }

    @Test
    void canonicalAspectResourcesRetainTheExactOriginalBytes()
            throws IOException, NoSuchAlgorithmException {
        Map<String, ResourceFingerprint> fingerprints = Map.of(
                "etherology.json",
                new ResourceFingerprint(
                        9_264,
                        "b4ae94e9eb7436112208a2e226c45c182085b13ee1442ef0d6504bdd871fd60e"
                ),
                "vanilla.json",
                new ResourceFingerprint(
                        109_046,
                        "968aa2631020031ac0cc60d0fd31862f28f88d52c48b10b5b91080a94e8c9e97"
                )
        );
        for (Map.Entry<String, ResourceFingerprint> entry
                : fingerprints.entrySet()) {
            String resource = "/data/etherology/etherology/aspects/"
                    + entry.getKey();
            try (var stream = getClass().getResourceAsStream(resource)) {
                assertNotNull(stream, resource);
                byte[] bytes = stream.readAllBytes();
                assertEquals(entry.getValue().size(), bytes.length, resource);
                assertEquals(
                        entry.getValue().sha256(),
                        HexFormat.of().formatHex(
                                MessageDigest.getInstance("SHA-256").digest(bytes)
                        ),
                        resource
                );
            }
        }
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (candidate != null
                && !Files.isRegularFile(candidate.resolve("settings.gradle.kts"))) {
            candidate = candidate.getParent();
        }

        assertNotNull(candidate, "Could not find the Etherology repository root");
        assertTrue(Files.isDirectory(candidate.resolve("common/src/main/java")));
        return candidate;
    }

    private record ResourceFingerprint(int size, String sha256) {
    }
}
