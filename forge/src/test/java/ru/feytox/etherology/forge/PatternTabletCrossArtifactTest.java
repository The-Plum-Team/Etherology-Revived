package ru.feytox.etherology.forge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import ru.feytox.etherology.forge.ItemRegistryTestArtifacts.Artifact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;
import static ru.feytox.etherology.forge.ItemRegistryTestArtifacts.*;
import static ru.feytox.etherology.forge.PatternTabletBytecodeTest.*;
import static ru.feytox.etherology.forge.PedestalBytecodeAssertions.*;
import static ru.feytox.etherology.forge.SharedItemCatalogAssertions.singleMethod;

final class PatternTabletCrossArtifactTest {

    private static final String CONFIG = "etherology.common.mixins.json";
    private static final String LEGACY_ITEMS = PREFIX + "registry/item/EItems";
    private static final List<String> SHARED_CLASSES = List.of(
            CATALOG, TABLET, STYLES, ACCESSOR, LOOT, TRADE
    );
    private static final List<String> TEXTURE_HASHES = List.of(
            "fd3144b6c0f03465324a5206424a1c0be9ca65f4c49dc05133c8c933bf733d02",
            "27d542df519abfe806618ee8423447afc6b345627ee936a2c51897fd54749d78",
            "86d93537e93a5c419b835def10c3e42f8683b61c175453a6e592a8f46c6676b9",
            "263361756b9be91d9b585692b6608c075f96bb7b15db0311cd834cad5de60baa",
            "08df8a601a8bc4c4faec984695cd5f2a19267c2c53ae6633105c9941c7bb8f00",
            "1c04a08cfc3d02b4b00eed2c5c02c6484dc251117896579600ccb69be75a0a2f",
            "8cad64f354bc437d641331abad3651e9af06c2a43f6a491427d4671bc8613548"
    );
    private static List<Artifact> artifacts;
    private static Path root;

    @BeforeAll
    static void loadArtifacts() throws IOException {
        artifacts = ItemRegistryTestArtifacts.load("etherology.patternTablets");
        root = repositoryRoot();
    }

    @Test
    void everyArtifactHasOneSharedImplementationAndOnlyTheSharedCatalogConstructsTablets()
            throws IOException {
        for (Artifact artifact : artifacts) {
            try (JarFile jar = artifact.open()) {
                for (String owner : SHARED_CLASSES) {
                    assertEquals(1, jar.stream().filter(entry ->
                            entry.getName().equals(owner + ".class")).count(),
                            artifact.description() + ":" + owner);
                    String constants = new String(bytes(jar, owner + ".class"),
                            StandardCharsets.ISO_8859_1);
                    for (String forbidden : List.of("net/fabricmc/", "net/minecraftforge/",
                            LEGACY_ITEMS, PREFIX + "client/")) {
                        assertFalse(constants.contains(forbidden), owner + ":" + forbidden);
                    }
                }
                assertCatalog(readClass(jar, CATALOG));
                for (var entry : jar.stream().filter(entry -> entry.getName().startsWith(PREFIX)
                        && entry.getName().endsWith(".class")).toList()) {
                    ClassNode owner = readClass(jar,
                            entry.getName().substring(0, entry.getName().length() - 6));
                    for (MethodNode method : owner.methods) {
                        for (AbstractInsnNode instruction : method.instructions) {
                            if (instruction instanceof TypeInsnNode type
                                    && type.getOpcode() == Opcodes.NEW && type.desc.equals(TABLET)) {
                                assertEquals(CATALOG, owner.name);
                                assertTrue(method.name.startsWith("lambda$"));
                            }
                        }
                    }
                }
                if (artifact.fabricApplication()) {
                    SharedItemCatalogAssertions.assertAliases(readClass(jar, LEGACY_ITEMS), CATALOG,
                            STYLE_NAMES.stream().map(style -> style + "_PATTERN_TABLET").toList());
                } else {
                    assertNull(jar.getEntry(LEGACY_ITEMS + ".class"));
                }
            }
        }
        for (String owner : SHARED_CLASSES) {
            requireRegularFile(root.resolve("common/src/main/java/" + owner + ".java"));
            assertFalse(Files.exists(root.resolve("src/main/java/" + owner + ".java")));
        }
    }

    @Test
    void bothLoadersAttachTabletsBeforeConsumersAndInstallBothAcquisitionHooksOnce()
            throws IOException {
        for (Artifact artifact : artifacts) {
            try (JarFile jar = artifact.open()) {
                String owner = artifact.fabricApplication() ? PREFIX + "Etherology"
                        : PREFIX + "bootstrap/EtherologyBootstrap";
                List<String> invocations = calls(singleMethod(readClass(jar, owner), "initialize"));
                String registration = CATALOG + "#register()V";
                int tabletIndex = invocations.indexOf(registration);
                assertTrue(tabletIndex >= 0, artifact.description());
                assertEquals(1, invocations.stream().filter(registration::equals).count());
                for (String acquisition : List.of(LOOT, TRADE)) {
                    String hook = acquisition + "#registerAll()V";
                    assertEquals(1, invocations.stream().filter(hook::equals).count());
                    assertTrue(invocations.indexOf(hook) > tabletIndex);
                }
                if (artifact.fabricApplication()) {
                    assertTrue(invocations.indexOf(LEGACY_ITEMS + "#registerItems()V") > tabletIndex);
                }
            }
        }
    }

    @Test
    void sharedMixinIsSelectedOnceAndAllFourVanillaAccessorsAreRemapped()
            throws IOException {
        byte[] configBytes = Files.readAllBytes(root.resolve("common/src/main/resources/" + CONFIG));
        for (Artifact artifact : artifacts) {
            try (JarFile jar = artifact.open()) {
                assertArrayEquals(configBytes, bytes(jar, CONFIG));
                JsonObject config = json(jar, CONFIG);
                assertTrue(config.get("required").getAsBoolean());
                assertEquals("ru.feytox.etherology.mixin", config.get("package").getAsString());
                assertEquals(List.of("SmithingTemplateItemAccessor"),
                        config.getAsJsonArray("mixins").asList().stream()
                                .map(JsonElement::getAsString).toList());
                assertFalse(config.has("refmap"));
                if (artifact.fabricApplication()) {
                    assertEquals(1, json(jar, "fabric.mod.json").getAsJsonArray("mixins")
                            .asList().stream().filter(JsonElement::isJsonPrimitive)
                            .map(JsonElement::getAsString).filter(CONFIG::equals).count());
                    assertFalse(json(jar, "etherology.mixins.json").getAsJsonArray("mixins")
                            .asList().stream().map(JsonElement::getAsString)
                            .anyMatch("SmithingTemplateItemAccessor"::equals));
                } else if (artifact.includesAssets()) {
                    String mixins = jar.getManifest().getMainAttributes().getValue("MixinConfigs");
                    assertNotNull(mixins);
                    assertEquals(1, Arrays.stream(mixins.split(","))
                            .map(String::trim).filter(CONFIG::equals).count());
                    String accessTransformer = "META-INF/accesstransformer.cfg";
                    assertArrayEquals(Files.readAllBytes(root.resolve(
                                    "forge/src/main/resources/" + accessTransformer)),
                            bytes(jar, accessTransformer));
                }
                boolean remapped = artifact.description().equals("fabricProductionJar");
                ClassNode accessor = readClass(jar, ACCESSOR);
                assertEquals(List.of(Type.getObjectType(remapped ? "net/minecraft/class_8052"
                                : "net/minecraft/item/SmithingTemplateItem")),
                        annotationValue(requireClassAnnotation(accessor,
                                "Lorg/spongepowered/asm/mixin/Mixin;"), "value"));
                Map<String, String> targets = remapped ? Map.of(
                        "getIngredientsText", "field_41977", "getAppliesToText", "field_41978",
                        "getDescriptionFormatting", "field_41975", "getTitleFormatting", "field_41974"
                ) : Map.of(
                        "getIngredientsText", "INGREDIENTS_TEXT", "getAppliesToText", "APPLIES_TO_TEXT",
                        "getDescriptionFormatting", "DESCRIPTION_FORMATTING", "getTitleFormatting", "TITLE_FORMATTING"
                );
                assertEquals(4, accessor.methods.size());
                for (var target : targets.entrySet()) {
                    MethodNode method = singleMethod(accessor, target.getKey());
                    assertTrue((method.access & Opcodes.ACC_STATIC) != 0);
                    assertEquals(target.getValue(), annotationValue(requireMethodAnnotation(method,
                            "Lorg/spongepowered/asm/mixin/gen/Accessor;"), "value"));
                }
            }
        }
    }

    @Test
    void bothLoaderApplicationsPreserveAllSevenModelsTexturesAndTranslations()
            throws IOException, NoSuchAlgorithmException {
        for (Artifact artifact : artifacts) {
            try (JarFile jar = artifact.open()) {
                for (int index = 0; index < STYLE_NAMES.size(); index++) {
                    String id = STYLE_NAMES.get(index).toLowerCase(Locale.ROOT) + "_pattern_tablet";
                    String model = "assets/etherology/models/item/" + id + ".json";
                    String texture = "assets/etherology/textures/item/" + id + ".png";
                    if (!artifact.includesAssets()) {
                        assertNull(jar.getEntry(model));
                        assertNull(jar.getEntry(texture));
                        continue;
                    }
                    assertArrayEquals(Files.readAllBytes(root.resolve("src/main/generated/" + model)),
                            bytes(jar, model));
                    assertEquals("minecraft:item/generated", json(jar, model).get("parent").getAsString());
                    assertEquals("etherology:item/" + id,
                            json(jar, model).getAsJsonObject("textures").get("layer0").getAsString());
                    assertArrayEquals(Files.readAllBytes(root.resolve("src/client/resources/" + texture)),
                            bytes(jar, texture));
                    assertEquals(TEXTURE_HASHES.get(index), HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-256").digest(bytes(jar, texture))));
                    for (String locale : List.of("en_us", "ru_ru")) {
                        JsonObject language = json(jar, "assets/etherology/lang/" + locale + ".json");
                        assertFalse(language.get("item.etherology." + id).getAsString().isBlank());
                        for (String suffix : List.of("", ".applies_to", ".ingredients")) {
                            assertFalse(language.get("item.etherology.pattern_tablet" + suffix)
                                    .getAsString().isBlank());
                        }
                    }
                }
            }
        }
    }

    @Test
    void everyArtifactPreservesTheSharedAspectInheritanceForAllSevenTablets()
            throws IOException {
        String data = "data/etherology/etherology/aspects/etherology.json";
        for (Artifact artifact : artifacts) {
            try (JarFile jar = artifact.open()) {
                assertArrayEquals(Files.readAllBytes(root.resolve("common/src/main/resources/" + data)),
                        bytes(jar, data));
                JsonObject aspects = json(jar, data);
                assertEquals(JsonParser.parseString("{\"solista\":5,\"iskil\":4}"),
                        aspects.get("etherology:traditional_pattern_tablet"));
                for (String style : STYLE_NAMES.subList(0, 6)) {
                    assertEquals(JsonParser.parseString("{\"parent\":\"etherology:traditional_pattern_tablet\"}"),
                            aspects.get("etherology:" + style.toLowerCase(Locale.ROOT) + "_pattern_tablet"));
                }
            }
        }
    }
}
