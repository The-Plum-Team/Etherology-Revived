package ru.feytox.etherology.forge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import ru.feytox.etherology.forge.ItemRegistryTestArtifacts.Artifact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;
import static ru.feytox.etherology.forge.EbonyArmorBytecodeTest.*;
import static ru.feytox.etherology.forge.ItemRegistryTestArtifacts.*;
import static ru.feytox.etherology.forge.PedestalBytecodeAssertions.*;
import static ru.feytox.etherology.forge.SharedItemCatalogAssertions.singleMethod;

final class EbonyArmorCrossArtifactTest {

    private static final String LEGACY = PREFIX + "registry/item/ArmorItems";
    private static final List<String> ARMOR_IDS = SLOTS.stream()
            .map(slot -> "ebony_" + slot.toLowerCase(Locale.ROOT)).toList();
    private static final List<String> TRIMS = List.of(
            "quartz", "iron", "netherite", "redstone", "copper", "gold", "emerald", "diamond", "lapis", "amethyst"
    );
    private static final List<List<String>> CRAFTING_PATTERNS = List.of(
            List.of("XXX", "X X"), List.of("X X", "XXX", "XXX"),
            List.of("XXX", "X X", "X X"), List.of("X X", "X X")
    );
    private static final Map<String, String> TEXTURES = Map.of(
            "item/ebony_helmet.png", "66cec1a18a5dfb4107b5a3ef5f4690b135cac9d267cd2234fb64cbc741d15777",
            "item/ebony_chestplate.png", "ba0edb23faf7db33842d7a944c75c15d07f3db11a5e37185f57c92242ba43b25",
            "item/ebony_leggings.png", "2ee1975265f6f0926370d5e810eff6c5ba29afbe230dd34b34cc8f8df9cae864",
            "item/ebony_boots.png", "5d1a3f21cd41a9e720cdfa6c6ede63d3880883e51a29959d6e6f3147c4f06aa6",
            "models/armor/ebony_layer_1.png", "6f4cd5cb066650064158f4e19383ad096210717c506588ce9d3c2f238021a4fa",
            "models/armor/ebony_layer_2.png", "894a36c0169aed5dcd6e1bf195c89a437e06dc4787c9b305c6eb3200fb94a7ab"
    );
    private static List<Artifact> artifacts;
    private static Path root;

    @BeforeAll
    static void loadArtifacts() throws IOException {
        artifacts = ItemRegistryTestArtifacts.load("etherology.ebonyArmor");
        root = repositoryRoot();
    }

    @Test
    void allSixArtifactsShareTheCanonicalArmorMaterialAndExactFourItemFactories()
            throws IOException {
        List<String> owners = List.of(CATALOG, ARMOR, MATERIAL);
        for (String owner : owners) {
            requireRegularFile(root.resolve("common/src/main/java/" + owner + ".java"));
            assertFalse(Files.exists(root.resolve("src/main/java/" + owner + ".java")));
        }
        for (Artifact artifact : artifacts) {
            try (JarFile jar = artifact.open()) {
                for (String owner : owners) {
                    assertEquals(1, jar.stream().filter(entry -> entry.getName().equals(owner + ".class")).count());
                    String constants = new String(bytes(jar, owner + ".class"), StandardCharsets.ISO_8859_1);
                    for (String forbidden : List.of("net/fabricmc/", "net/minecraftforge/", "lombok/",
                            LEGACY, PREFIX + "client/", PREFIX + "util/misc/EIdentifier")) {
                        assertFalse(constants.contains(forbidden), owner + ":" + forbidden);
                    }
                }
                assertCatalog(readClass(jar, CATALOG), artifact.description().equals("fabricProductionJar"));
                for (var entry : jar.stream().filter(entry -> entry.getName().startsWith(PREFIX)
                        && entry.getName().endsWith(".class")).toList()) {
                    var owner = readClass(jar, entry.getName().substring(0, entry.getName().length() - 6));
                    for (var method : owner.methods) {
                        for (AbstractInsnNode instruction : method.instructions) {
                            if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW
                                    && type.desc.equals(ARMOR)) {
                                assertEquals(CATALOG, owner.name);
                                assertTrue(method.name.startsWith("lambda$"));
                            }
                        }
                    }
                }
                if (artifact.fabricApplication()) {
                    SharedItemCatalogAssertions.assertAliases(readClass(jar, LEGACY), CATALOG,
                            SLOTS.stream().map(slot -> "EBONY_" + slot).toList());
                } else {
                    assertNull(jar.getEntry(LEGACY + ".class"));
                }
                String initializer = artifact.fabricApplication() ? PREFIX + "Etherology"
                        : PREFIX + "bootstrap/EtherologyBootstrap";
                List<String> calls = calls(singleMethod(readClass(jar, initializer), "initialize"));
                String attach = CATALOG + "#register()V";
                assertEquals(1, calls.stream().filter(attach::equals).count());
                assertTrue(calls.indexOf(attach) > calls.indexOf(PREFIX + "registry/item/SharedMaterialItems#register()V"));
                if (artifact.fabricApplication()) {
                    assertTrue(calls.indexOf(attach) < calls.indexOf(PREFIX + "registry/item/EItems#registerItems()V"));
                }
            }
        }
    }

    @Test
    void bothLoadersKeepTheFourCraftingRecipesAndNowCloseBothNineItemRecyclingRecipes()
            throws IOException {
        List<String> recycled = new ArrayList<>(ARMOR_IDS);
        recycled.addAll(List.of("ebony_axe", "ebony_pickaxe", "ebony_hoe", "ebony_shovel", "ebony_sword"));
        for (Artifact artifact : artifacts) {
            try (JarFile jar = artifact.open()) {
                for (int index = 0; index < ARMOR_IDS.size(); index++) {
                    String id = ARMOR_IDS.get(index);
                    String recipe = "data/etherology/recipes/" + id + ".json";
                    assertGeneratedEntry(artifact, jar, recipe);
                    assertAdvancement(artifact, jar, id, "combat");
                    if (!artifact.includesAssets()) continue;
                    JsonObject crafting = json(jar, recipe);
                    assertEquals("minecraft:crafting_shaped", crafting.get("type").getAsString());
                    assertEquals(CRAFTING_PATTERNS.get(index), crafting.getAsJsonArray("pattern")
                            .asList().stream().map(JsonElement::getAsString).toList());
                    assertEquals("etherology:ebony_ingot", crafting.getAsJsonObject("key")
                            .getAsJsonObject("X").get("item").getAsString());
                    assertEquals("etherology:" + id, crafting.getAsJsonObject("result").get("item").getAsString());
                }
                for (String process : List.of("smelting", "blasting")) {
                    String id = "ebony_nugget_from_" + process;
                    String recipe = "data/etherology/recipes/" + id + ".json";
                    assertGeneratedEntry(artifact, jar, recipe);
                    assertAdvancement(artifact, jar, id, "misc");
                    if (!artifact.includesAssets()) continue;
                    JsonObject recycling = json(jar, recipe);
                    assertEquals("minecraft:" + process, recycling.get("type").getAsString());
                    assertEquals(recycled.stream().map(item -> "etherology:" + item).toList(),
                            recycling.getAsJsonArray("ingredient").asList().stream()
                                    .map(entry -> entry.getAsJsonObject().get("item").getAsString()).toList());
                    assertEquals("etherology:ebony_nugget", recycling.get("result").getAsString());
                    assertEquals(0.1F, recycling.get("experience").getAsFloat());
                    assertEquals(process.equals("smelting") ? 200 : 100, recycling.get("cookingtime").getAsInt());
                }
            }
        }
    }

    @Test
    void allFortyFourArmorItemModelsKeepTheirExactVanillaTrimOverrides() throws IOException {
        for (Artifact artifact : artifacts) {
            try (JarFile jar = artifact.open()) {
                for (String id : ARMOR_IDS) {
                    String model = "assets/etherology/models/item/" + id + ".json";
                    assertGeneratedEntry(artifact, jar, model);
                    for (String trim : TRIMS) {
                        assertGeneratedEntry(artifact, jar,
                                "assets/etherology/models/item/" + id + "_" + trim + "_trim.json");
                    }
                    if (!artifact.includesAssets()) continue;
                    JsonObject base = json(jar, model);
                    assertEquals("minecraft:item/generated", base.get("parent").getAsString());
                    assertEquals("etherology:item/" + id, base.getAsJsonObject("textures").get("layer0").getAsString());
                    var overrides = base.getAsJsonArray("overrides");
                    assertEquals(10, overrides.size());
                    for (int index = 0; index < TRIMS.size(); index++) {
                        JsonObject override = overrides.get(index).getAsJsonObject();
                        assertEquals("etherology:item/" + id + "_" + TRIMS.get(index) + "_trim",
                                override.get("model").getAsString());
                        assertEquals((index + 1) / 10.0, override.getAsJsonObject("predicate")
                                .get("trim_type").getAsDouble(), 0.000001);
                    }
                }
            }
        }
    }

    @Test
    void armorTexturesAndBothTranslationsArePreservedInBothLoaderApplications()
            throws IOException, NoSuchAlgorithmException {
        for (Artifact artifact : artifacts) {
            try (JarFile jar = artifact.open()) {
                for (var texture : TEXTURES.entrySet()) {
                    String entry = "assets/etherology/textures/" + texture.getKey();
                    if (!artifact.includesAssets()) {
                        assertNull(jar.getEntry(entry));
                        continue;
                    }
                    assertArrayEquals(Files.readAllBytes(root.resolve("src/client/resources/" + entry)), bytes(jar, entry));
                    assertEquals(texture.getValue(), HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-256").digest(bytes(jar, entry))));
                }
                if (!artifact.includesAssets()) continue;
                for (String language : List.of("en_us", "ru_ru")) {
                    JsonObject translations = json(jar, "assets/etherology/lang/" + language + ".json");
                    for (String id : ARMOR_IDS) {
                        assertFalse(translations.get("item.etherology." + id).getAsString().isBlank());
                    }
                }
            }
        }
    }

    private static void assertAdvancement(Artifact artifact, JarFile jar, String id, String category)
            throws IOException {
        String entry = "data/etherology/advancements/recipes/" + category + "/" + id + ".json";
        assertGeneratedEntry(artifact, jar, entry);
        if (artifact.includesAssets()) {
            JsonObject advancement = json(jar, entry);
            assertEquals(List.of("etherology:" + id), advancement.getAsJsonObject("rewards")
                    .getAsJsonArray("recipes").asList().stream().map(JsonElement::getAsString).toList());
            assertEquals("etherology:" + id, advancement.getAsJsonObject("criteria")
                    .getAsJsonObject("has_the_recipe").getAsJsonObject("conditions").get("recipe").getAsString());
        }
    }

    private static void assertGeneratedEntry(Artifact artifact, JarFile jar, String entry) throws IOException {
        if (artifact.includesAssets()) {
            assertArrayEquals(Files.readAllBytes(root.resolve("src/main/generated/" + entry)), bytes(jar, entry));
        } else {
            assertNull(jar.getEntry(entry));
        }
    }
}
