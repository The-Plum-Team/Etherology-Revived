package ru.feytox.etherology.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PedestalForgeResourcesTest {

    private static final Set<String> PEDESTAL_DATA = Set.of(
            "data/etherology/advancements/recipes/decorations/pedestal.json",
            "data/etherology/loot_tables/blocks/pedestal.json",
            "data/etherology/recipes/pedestal.json"
    );
    private static final Pattern QUOTED_STRING = Pattern.compile("\"([^\"]+)\"");

    @Test
    void allDedicatedVisualsAndBothLanguageAliasesMatchCanonicalBytes()
            throws IOException {
        Path repositoryRoot = PedestalBytecodeAssertions.repositoryRoot();
        Path assetRoot = repositoryRoot.resolve(
                "src/client/resources/assets/etherology"
        );
        List<Path> pedestalAssets;
        try (Stream<Path> files = Files.walk(assetRoot)) {
            pedestalAssets = files
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().contains("pedestal"))
                    .sorted()
                    .toList();
        }
        assertEquals(59, pedestalAssets.size());
        for (Path canonicalPath : pedestalAssets) {
            assertFalse(Files.isSymbolicLink(canonicalPath), canonicalPath.toString());
            String relativePath = assetRoot.relativize(canonicalPath)
                    .toString()
                    .replace(canonicalPath.getFileSystem().getSeparator(), "/");
            assertArrayEquals(
                    Files.readAllBytes(canonicalPath),
                    PedestalBytecodeAssertions.readResource(
                            "assets/etherology/" + relativePath
                    ),
                    relativePath
            );
        }

        assertLanguageAlias(
                repositoryRoot,
                "src/client/resources/assets/etherology/lang/en_us.json",
                "Pedestal"
        );
        assertLanguageAlias(
                repositoryRoot,
                "src/main/generated/assets/etherology/lang/ru_ru.json",
                "Пьедестал"
        );
    }

    @Test
    void pedestalRecipeLootAndAdvancementMatchCanonicalBytesAndSemantics()
            throws IOException {
        Path repositoryRoot = PedestalBytecodeAssertions.repositoryRoot();
        for (String resource : PEDESTAL_DATA) {
            Path canonical = PedestalBytecodeAssertions.requireRegularFile(
                    repositoryRoot.resolve("src/main/generated").resolve(resource)
            );
            assertArrayEquals(
                    Files.readAllBytes(canonical),
                    PedestalBytecodeAssertions.readResource(resource),
                    resource
            );
        }

        JsonObject recipe = parseObject("data/etherology/recipes/pedestal.json");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals("misc", recipe.get("category").getAsString());
        assertEquals(
                List.of("S", "#", "S"),
                stringValues(recipe.getAsJsonArray("pattern"))
        );
        JsonObject recipeKey = recipe.getAsJsonObject("key");
        assertEquals(
                "etherology:polished_slitherite",
                recipeKey.getAsJsonObject("#").get("item").getAsString()
        );
        assertEquals(
                "etherology:polished_slitherite_slab",
                recipeKey.getAsJsonObject("S").get("item").getAsString()
        );
        JsonObject result = recipe.getAsJsonObject("result");
        assertEquals("etherology:pedestal", result.get("item").getAsString());
        assertEquals(2, result.get("count").getAsInt());
        assertTrue(recipe.get("show_notification").getAsBoolean());

        JsonObject loot = parseObject(
                "data/etherology/loot_tables/blocks/pedestal.json"
        );
        assertEquals("minecraft:block", loot.get("type").getAsString());
        JsonArray pools = loot.getAsJsonArray("pools");
        assertEquals(1, pools.size());
        JsonObject pool = pools.get(0).getAsJsonObject();
        assertEquals(1.0, pool.get("rolls").getAsDouble());
        assertEquals(0.0, pool.get("bonus_rolls").getAsDouble());
        assertEquals(
                "minecraft:survives_explosion",
                pool.getAsJsonArray("conditions")
                        .get(0)
                        .getAsJsonObject()
                        .get("condition")
                        .getAsString()
        );
        JsonObject lootEntry = pool.getAsJsonArray("entries")
                .get(0)
                .getAsJsonObject();
        assertEquals("minecraft:item", lootEntry.get("type").getAsString());
        assertEquals("etherology:pedestal", lootEntry.get("name").getAsString());

        JsonObject advancement = parseObject(
                "data/etherology/advancements/recipes/decorations/pedestal.json"
        );
        assertEquals("minecraft:recipes/root", advancement.get("parent").getAsString());
        JsonObject criteria = advancement.getAsJsonObject("criteria");
        assertEquals(Set.of("has_slitherite", "has_the_recipe"), criteria.keySet());
        assertEquals(
                "etherology:pedestal",
                criteria.getAsJsonObject("has_the_recipe")
                        .getAsJsonObject("conditions")
                        .get("recipe")
                        .getAsString()
        );
        assertEquals(
                "etherology:slitherite",
                criteria.getAsJsonObject("has_slitherite")
                        .getAsJsonObject("conditions")
                        .getAsJsonArray("items")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("items")
                        .get(0)
                        .getAsString()
        );
        assertEquals(
                "etherology:pedestal",
                advancement.getAsJsonObject("rewards")
                        .getAsJsonArray("recipes")
                        .get(0)
                        .getAsString()
        );
    }

    @Test
    void pickaxeTagCarriesThePedestalAndForgeAcceptsExactlyItsThreeDataFiles()
            throws IOException {
        JsonObject pickaxeTag = parseObject(
                "data/minecraft/tags/blocks/mineable/pickaxe.json"
        );
        long pedestalEntries = 0;
        for (JsonElement element : pickaxeTag.getAsJsonArray("values")) {
            if (tagId(element).equals("etherology:pedestal")) {
                pedestalEntries++;
            }
        }
        assertEquals(1, pedestalEntries);

        Path buildFile = PedestalBytecodeAssertions.requireRegularFile(
                PedestalBytecodeAssertions.repositoryRoot().resolve(
                        "forge/build.gradle.kts"
                )
        );
        String build = Files.readString(buildFile);
        String pedestalDeclaration = section(
                build,
                "val canonicalPedestalDataEntries = setOf(",
                "val acceptedForgeDirectDataEntries = setOf("
        );
        Set<String> declaredEntries = new LinkedHashSet<>();
        Matcher quotedStrings = QUOTED_STRING.matcher(pedestalDeclaration);
        while (quotedStrings.find()) {
            declaredEntries.add("data/" + quotedStrings.group(1));
        }
        assertEquals(PEDESTAL_DATA, declaredEntries);

        String acceptedData = section(
                build,
                "val acceptedForgeDirectDataEntries = setOf(",
                "val acceptedForgeArtifactDataEntries ="
        );
        assertEquals(1, occurrences(acceptedData, "canonicalPedestalDataEntries"));

        String forgeResources = section(build, "sourceSets {", "configurations {");
        assertEquals(
                1,
                occurrences(
                        forgeResources,
                        "canonicalPedestalDataEntries.forEach { entry ->"
                )
        );
        assertTrue(Pattern.compile(
                "canonicalPedestalDataEntries\\.forEach\\s*\\{\\s*entry\\s*->"
                        + "\\s*include\\(\"data/\\$entry\"\\)\\s*}",
                Pattern.MULTILINE
        ).matcher(forgeResources).find());
    }

    private static void assertLanguageAlias(
            Path repositoryRoot,
            String repositoryPath,
            String expectedValue
    ) throws IOException {
        Path languagePath = PedestalBytecodeAssertions.requireRegularFile(
                repositoryRoot.resolve(repositoryPath)
        );
        String resource = repositoryPath.substring(
                repositoryPath.indexOf("assets/")
        );
        assertArrayEquals(
                Files.readAllBytes(languagePath),
                PedestalBytecodeAssertions.readResource(resource),
                resource
        );
        JsonObject language = JsonParser.parseString(
                Files.readString(languagePath)
        ).getAsJsonObject();
        assertEquals(expectedValue, language.get("block.etherology.pedestal").getAsString());
    }

    private static JsonObject parseObject(String resource) throws IOException {
        return JsonParser.parseString(
                PedestalBytecodeAssertions.readTextResource(resource)
        ).getAsJsonObject();
    }

    private static List<String> stringValues(JsonArray array) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            values.add(element.getAsString());
        }
        return values;
    }

    private static String tagId(JsonElement tagEntry) {
        if (tagEntry.isJsonPrimitive()) {
            return tagEntry.getAsString();
        }
        return tagEntry.getAsJsonObject().get("id").getAsString();
    }

    private static String section(String text, String start, String end) {
        int startIndex = text.indexOf(start);
        assertTrue(startIndex >= 0, start);
        int endIndex = text.indexOf(end, startIndex + start.length());
        assertTrue(endIndex > startIndex, end);
        return text.substring(startIndex, endIndex);
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
