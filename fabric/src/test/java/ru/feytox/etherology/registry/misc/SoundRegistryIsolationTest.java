package ru.feytox.etherology.registry.misc;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SoundRegistryIsolationTest {

    private static final String CANONICAL_ETHER_SOUNDS =
            "ru/feytox/etherology/registry/misc/EtherSounds.class";
    private static final String COMMON_BOOTSTRAP =
            "ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String FABRIC_ENTRYPOINT =
            "ru/feytox/etherology/EtherologyFabric.class";
    private static final String FABRIC_INITIALIZER =
            "ru/feytox/etherology/Etherology.class";
    private static final String SHARED_SOUNDS =
            "ru/feytox/etherology/registry/misc/SharedSounds.class";
    private static final Set<String> EXACT_SOUND_IDS = Set.of(
            "electricity_sound",
            "matrix_idle_sound",
            "deflect",
            "bubbles",
            "pouf",
            "ratchet",
            "brewing_dissolution",
            "thunder_zap",
            "tuning_mace",
            "tuning_fork_activate",
            "tuning_fork_tuning",
            "tuning_fork_resonance",
            "broadsword",
            "warp_counter"
    );
    private static final String COMMON_BOOTSTRAP_OWNER =
            "ru/feytox/etherology/bootstrap/EtherologyBootstrap";
    private static final String SHARED_SOUNDS_OWNER =
            "ru/feytox/etherology/registry/misc/SharedSounds";

    @Test
    void sharedOwnerRetainsTheExactSoundRegistryOnTheFabricClasspath()
            throws IOException {
        List<String> registeredIds = methodStringConstants(
                classpathClass(SHARED_SOUNDS),
                "<clinit>"
        );

        assertEquals(EXACT_SOUND_IDS.size(), registeredIds.size());
        assertEquals(EXACT_SOUND_IDS, new HashSet<>(registeredIds));
        assertNull(
                SoundRegistryIsolationTest.class
                        .getClassLoader()
                        .getResource(CANONICAL_ETHER_SOUNDS),
                "Fabric still packages the legacy eager EtherSounds owner"
        );
    }

    @Test
    void fabricEntrypointAttachesTheSharedOwnerExactlyOnce()
            throws IOException {
        ClassReader initializer = classpathClass(FABRIC_INITIALIZER);
        ClassReader entrypoint = classpathClass(FABRIC_ENTRYPOINT);

        assertEquals(
                1,
                invocationCount(
                        initializer,
                        "initialize",
                        "ru/feytox/etherology/registry/misc/SharedSounds",
                        "register"
                )
        );
        assertEquals(
                1,
                invocationCount(
                        entrypoint,
                        "onInitialize",
                        "ru/feytox/etherology/Etherology",
                        "initialize"
                )
        );
        assertFalse(referencesAnyOwner(initializer, Set.of(COMMON_BOOTSTRAP_OWNER)));
        assertFalse(referencesAnyOwner(
                entrypoint,
                Set.of(COMMON_BOOTSTRAP_OWNER, SHARED_SOUNDS_OWNER)
        ));
    }

    @Test
    void commonProductionArtifactOwnsOnlyTheSharedSoundRegistry() throws IOException {
        URL sharedSoundsResource = singleClasspathResource(SHARED_SOUNDS);
        assertEquals("jar", sharedSoundsResource.getProtocol());

        JarURLConnection connection = (JarURLConnection) sharedSoundsResource.openConnection();
        connection.setUseCaches(false);
        try (JarFile commonArtifact = connection.getJarFile()) {
            assertNotNull(commonArtifact.getJarEntry(SHARED_SOUNDS));
            assertNotNull(commonArtifact.getJarEntry(COMMON_BOOTSTRAP));
            assertFalse(commonArtifact.stream().anyMatch(
                    entry -> entry.getName().equals(CANONICAL_ETHER_SOUNDS)
            ));

            List<String> registeredIds = methodStringConstants(
                    artifactClass(commonArtifact, SHARED_SOUNDS),
                    "<clinit>"
            );
            assertEquals(EXACT_SOUND_IDS.size(), registeredIds.size());
            assertEquals(EXACT_SOUND_IDS, new HashSet<>(registeredIds));
            assertEquals(
                    1,
                    invocationCount(
                            artifactClass(commonArtifact, COMMON_BOOTSTRAP),
                            "initialize",
                            "ru/feytox/etherology/registry/misc/SharedSounds",
                            "register"
                    )
            );
        }
    }

    private static ClassReader classpathClass(String classResource) throws IOException {
        InputStream classStream = SoundRegistryIsolationTest.class
                .getClassLoader()
                .getResourceAsStream(classResource);
        assertNotNull(classStream, "Missing classpath class " + classResource);
        try (classStream) {
            return new ClassReader(classStream);
        }
    }

    private static ClassReader artifactClass(JarFile artifact, String classResource)
            throws IOException {
        InputStream classStream = artifact.getInputStream(artifact.getJarEntry(classResource));
        try (classStream) {
            return new ClassReader(classStream);
        }
    }

    private static URL singleClasspathResource(String classResource) throws IOException {
        Enumeration<URL> resources = SoundRegistryIsolationTest.class
                .getClassLoader()
                .getResources(classResource);
        assertTrue(resources.hasMoreElements(), "Missing classpath class " + classResource);
        URL resource = resources.nextElement();
        assertFalse(
                resources.hasMoreElements(),
                "Multiple classpath owners provide " + classResource
        );
        return resource;
    }

    private static List<String> methodStringConstants(
            ClassReader reader,
            String expectedMethodName
    ) {
        List<String> constants = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(expectedMethodName)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String stringValue) {
                            constants.add(stringValue);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return constants;
    }

    private static int invocationCount(
            ClassReader reader,
            String expectedMethodName,
            String expectedOwner,
            String expectedInvocationName
    ) {
        AtomicInteger count = new AtomicInteger();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(expectedMethodName)) {
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
                        if (owner.equals(expectedOwner) && name.equals(expectedInvocationName)) {
                            count.incrementAndGet();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return count.get();
    }

    private static boolean referencesAnyOwner(ClassReader reader, Set<String> excludedOwners) {
        AtomicInteger references = new AtomicInteger();
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
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (excludedOwners.contains(owner)) {
                            references.incrementAndGet();
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
                        if (excludedOwners.contains(owner)) {
                            references.incrementAndGet();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return references.get() > 0;
    }
}
