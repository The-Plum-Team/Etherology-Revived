package ru.feytox.etherology.registry.world;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorldGenRegistryNamespaceTest {

    private static final String CLASS_RESOURCE = "ru/feytox/etherology/registry/world/WorldGenRegistry.class";
    private static final String IDENTIFIER_OWNER = "ru/feytox/etherology/util/misc/EIdentifier";
    private static final String REGISTRY_OWNER = "net/minecraft/registry/Registry";

    @Test
    void compiledFeatureRegistrationUsesEtherologyIdentifier() throws Exception {
        InputStream classStream = getClass().getClassLoader().getResourceAsStream(CLASS_RESOURCE);
        assertNotNull(classStream);

        AtomicBoolean methodFound = new AtomicBoolean();
        AtomicBoolean projectIdentifierCalled = new AtomicBoolean();
        AtomicBoolean rawStringRegistrationCalled = new AtomicBoolean();
        try (classStream) {
            ClassReader reader = new ClassReader(classStream);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (!name.equals("registerFeature")) {
                        return null;
                    }

                    methodFound.set(true);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            if (owner.equals(IDENTIFIER_OWNER) && name.equals("of")) {
                                projectIdentifierCalled.set(true);
                            }
                            if (owner.equals(REGISTRY_OWNER) && name.equals("register")
                                    && descriptor.contains("Ljava/lang/String;")) {
                                rawStringRegistrationCalled.set(true);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        assertTrue(methodFound.get());
        assertTrue(projectIdentifierCalled.get());
        assertFalse(rawStringRegistrationCalled.get());
    }

    @Test
    void generatedConfiguredFeaturesReferenceEtherologyFeatureTypes() throws Exception {
        String etherRock = readResource(
                "data/etherology/worldgen/configured_feature/ether_rock.json"
        );
        String thujaPatch = readResource(
                "data/etherology/worldgen/configured_feature/patch_thuja.json"
        );

        assertTrue(etherRock.contains("\"type\": \"etherology:ether_rock\""));
        assertFalse(etherRock.contains("\"type\": \"minecraft:ether_rock\""));
        assertTrue(thujaPatch.contains("\"type\": \"etherology:thuja\""));
        assertFalse(thujaPatch.contains("\"type\": \"minecraft:thuja\""));
    }

    private String readResource(String resourceName) throws Exception {
        try (InputStream resource = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertNotNull(resource);
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
