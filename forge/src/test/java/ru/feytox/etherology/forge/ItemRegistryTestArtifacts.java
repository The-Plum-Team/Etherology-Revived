package ru.feytox.etherology.forge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static ru.feytox.etherology.forge.PedestalBytecodeAssertions.requireRegularFile;

final class ItemRegistryTestArtifacts {

    private ItemRegistryTestArtifacts() {
    }

    static List<Artifact> load(String propertyPrefix) throws IOException {
        return List.of(
                artifact(propertyPrefix, "commonJar", false, false),
                artifact(propertyPrefix, "fabricTransformedCommonJar", false, false),
                artifact(propertyPrefix, "forgeTransformedCommonJar", false, false),
                artifact(propertyPrefix, "fabricDevelopmentJar", true, true),
                artifact(propertyPrefix, "fabricProductionJar", true, true),
                artifact(propertyPrefix, "forgeShadowJar", true, false)
        );
    }

    private static Artifact artifact(
            String prefix, String suffix, boolean includesAssets, boolean fabricApplication
    ) throws IOException {
        String property = prefix + "." + suffix;
        String value = System.getProperty(property);
        assertNotNull(value, property);
        return new Artifact(
                requireRegularFile(Path.of(value)), suffix, includesAssets, fabricApplication
        );
    }

    static byte[] bytes(JarFile jar, String entryName) throws IOException {
        var entry = jar.getJarEntry(entryName);
        assertNotNull(entry, jar.getName() + ":" + entryName);
        try (var input = jar.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    static JsonObject json(JarFile jar, String entryName) throws IOException {
        return JsonParser.parseString(new String(bytes(jar, entryName), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    static ClassNode readClass(JarFile jar, String owner) throws IOException {
        ClassNode result = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes(jar, owner + ".class")).accept(result, ClassReader.SKIP_DEBUG);
        return result;
    }

    record Artifact(
            Path path, String description, boolean includesAssets, boolean fabricApplication
    ) {
        JarFile open() throws IOException {
            return new JarFile(path.toFile());
        }
    }
}
