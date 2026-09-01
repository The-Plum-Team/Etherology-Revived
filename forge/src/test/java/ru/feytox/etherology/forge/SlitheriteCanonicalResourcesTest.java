package ru.feytox.etherology.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SlitheriteCanonicalResourcesTest {

    private static final List<String> IDS = List.of(
            "slitherite",
            "slitherite_stairs",
            "slitherite_slab",
            "slitherite_wall"
    );
    private static final List<String> RECIPE_IDS = List.of(
            "slitherite_slab",
            "slitherite_slab_from_slitherite_stonecutting",
            "slitherite_stairs",
            "slitherite_stairs_from_slitherite_stonecutting",
            "slitherite_wall",
            "slitherite_wall_from_slitherite_stonecutting"
    );
    private static final Map<String, Boolean> PICKAXE_MEMBERS = Map.of(
            "slitherite", true,
            "slitherite_stairs", true,
            "slitherite_slab", true,
            "slitherite_wall", true
    );
    private static final Map<String, Boolean> SLAB_MEMBERS = Map.of(
            "slitherite_slab", true,
            "polished_slitherite_slab", false,
            "polished_slitherite_brick_slab", false
    );
    private static final Map<String, Boolean> STAIR_MEMBERS = Map.of(
            "slitherite_stairs", true,
            "polished_slitherite_stairs", false,
            "polished_slitherite_brick_stairs", false
    );
    private static final Map<String, Boolean> WALL_MEMBERS = Map.of(
            "slitherite_wall", true,
            "polished_slitherite_wall", false,
            "polished_slitherite_brick_wall", false
    );

    @Test
    void canonicalVisualAndLanguageResourcesAreExact() throws IOException {
        Path root = repositoryRoot();
        List<String> generatedAssets = List.of(
                "blockstates/slitherite.json",
                "blockstates/slitherite_stairs.json",
                "blockstates/slitherite_slab.json",
                "blockstates/slitherite_wall.json",
                "models/block/slitherite.json",
                "models/block/slitherite_stairs.json",
                "models/block/slitherite_stairs_inner.json",
                "models/block/slitherite_stairs_outer.json",
                "models/block/slitherite_slab.json",
                "models/block/slitherite_slab_top.json",
                "models/block/slitherite_wall_inventory.json",
                "models/block/slitherite_wall_post.json",
                "models/block/slitherite_wall_side.json",
                "models/block/slitherite_wall_side_tall.json",
                "models/item/slitherite.json",
                "models/item/slitherite_stairs.json",
                "models/item/slitherite_slab.json",
                "models/item/slitherite_wall.json"
        );
        for (String relativePath : generatedAssets) {
            requireCanonicalFile(
                    root.resolve("src/main/generated/assets/etherology")
                            .resolve(relativePath)
            );
        }
        requireCanonicalFile(
                root.resolve("src/client/resources/assets/etherology/textures/block/slitherite.png")
        );

        assertLanguage(
                readObject(root.resolve("src/client/resources/assets/etherology/lang/en_us.json")),
                Map.of(
                        "block.etherology.slitherite", "Slitherite",
                        "block.etherology.slitherite_stairs", "Slitherite Stairs",
                        "block.etherology.slitherite_slab", "Slitherite Slab",
                        "block.etherology.slitherite_wall", "Slitherite Wall"
                )
        );
        assertLanguage(
                readObject(root.resolve("src/main/generated/assets/etherology/lang/ru_ru.json")),
                Map.of(
                        "block.etherology.slitherite", "Слизерит",
                        "block.etherology.slitherite_stairs", "Слизеритовые ступеньки",
                        "block.etherology.slitherite_slab", "Слизеритовая плита",
                        "block.etherology.slitherite_wall", "Слизеритовая ограда"
                )
        );
    }

    @Test
    void canonicalLootRecipesAndAdvancementsAreExact() throws IOException {
        Path root = repositoryRoot().resolve("src/main/generated/data/etherology");
        for (String id : IDS) {
            JsonObject loot = readObject(root.resolve("loot_tables/blocks/" + id + ".json"));
            assertEquals("minecraft:block", loot.get("type").getAsString(), id);
            JsonArray pools = loot.getAsJsonArray("pools");
            assertEquals(1, pools.size(), id);
            JsonObject pool = pools.get(0).getAsJsonObject();
            assertEquals(1.0F, pool.get("rolls").getAsFloat(), id);
            assertEquals(0.0F, pool.get("bonus_rolls").getAsFloat(), id);
            assertEquals(
                    "minecraft:survives_explosion",
                    pool.getAsJsonArray("conditions")
                            .get(0)
                            .getAsJsonObject()
                            .get("condition")
                            .getAsString(),
                    id
            );
            JsonObject entry = pool.getAsJsonArray("entries").get(0).getAsJsonObject();
            assertEquals("minecraft:item", entry.get("type").getAsString(), id);
            assertEquals("etherology:" + id, entry.get("name").getAsString(), id);
        }

        for (String recipeId : RECIPE_IDS) {
            JsonObject recipe = readObject(root.resolve("recipes/" + recipeId + ".json"));
            assertRecipe(recipeId, recipe);

            String category = recipeId.startsWith("slitherite_wall")
                    ? "decorations"
                    : "building_blocks";
            JsonObject advancement = readObject(
                    root.resolve("advancements/recipes/" + category + "/" + recipeId + ".json")
            );
            assertEquals("minecraft:recipes/root", advancement.get("parent").getAsString());
            assertEquals(
                    Set.of("has_slitherite", "has_the_recipe"),
                    advancement.getAsJsonObject("criteria").keySet()
            );
            assertEquals(
                    "etherology:" + recipeId,
                    advancement.getAsJsonObject("rewards")
                            .getAsJsonArray("recipes")
                            .get(0)
                            .getAsString()
            );
            assertFalse(advancement.get("sends_telemetry_event").getAsBoolean());
        }
    }

    @Test
    void selectedTagMembersAreRequiredAndDeferredVariantsRemainOptional()
            throws IOException {
        Path tags = repositoryRoot().resolve("src/main/generated/data/minecraft/tags");
        assertContainsMembers(
                readObject(tags.resolve("blocks/mineable/pickaxe.json")),
                PICKAXE_MEMBERS
        );
        assertEquals(
                SLAB_MEMBERS,
                slitheriteMembers(readObject(tags.resolve("blocks/slabs.json")))
        );
        assertEquals(
                SLAB_MEMBERS,
                slitheriteMembers(readObject(tags.resolve("items/slabs.json")))
        );
        assertEquals(
                STAIR_MEMBERS,
                slitheriteMembers(readObject(tags.resolve("blocks/stairs.json")))
        );
        assertEquals(
                STAIR_MEMBERS,
                slitheriteMembers(readObject(tags.resolve("items/stairs.json")))
        );
        assertEquals(
                WALL_MEMBERS,
                slitheriteMembers(readObject(tags.resolve("blocks/walls.json")))
        );
        assertEquals(
                WALL_MEMBERS,
                slitheriteMembers(readObject(tags.resolve("items/walls.json")))
        );

        Map<String, Boolean> needsStone = slitheriteMembers(
                readObject(tags.resolve("blocks/needs_stone_tool.json"))
        );
        for (String id : IDS) assertFalse(needsStone.containsKey(id), id);
    }

    private static void assertRecipe(String recipeId, JsonObject recipe) {
        boolean stonecutting = recipeId.endsWith("_stonecutting");
        String output = recipeId.startsWith("slitherite_slab")
                ? "slitherite_slab"
                : recipeId.startsWith("slitherite_stairs")
                        ? "slitherite_stairs"
                        : "slitherite_wall";
        int count = output.equals("slitherite_slab") ? 2 : 1;
        if (stonecutting) {
            assertEquals("minecraft:stonecutting", recipe.get("type").getAsString());
            assertEquals(
                    "etherology:slitherite",
                    recipe.getAsJsonObject("ingredient").get("item").getAsString()
            );
            assertEquals("etherology:" + output, recipe.get("result").getAsString());
            assertEquals(count, recipe.get("count").getAsInt());
            return;
        }

        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals(
                output.equals("slitherite_wall") ? "misc" : "building",
                recipe.get("category").getAsString()
        );
        assertEquals(
                "etherology:slitherite",
                recipe.getAsJsonObject("key").getAsJsonObject("#").get("item").getAsString()
        );
        assertEquals(
                output.equals("slitherite_stairs") ? 4 : 6,
                recipe.getAsJsonObject("result").get("count").getAsInt()
        );
        assertEquals(
                "etherology:" + output,
                recipe.getAsJsonObject("result").get("item").getAsString()
        );
        assertTrue(recipe.get("show_notification").getAsBoolean());
    }

    private static void assertLanguage(JsonObject language, Map<String, String> expected) {
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), language.get(entry.getKey()).getAsString());
        }
    }

    private static void assertContainsMembers(
            JsonObject tag,
            Map<String, Boolean> expected
    ) {
        Map<String, Boolean> members = slitheriteMembers(tag);
        for (Map.Entry<String, Boolean> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), members.get(entry.getKey()), entry.getKey());
        }
    }

    private static Map<String, Boolean> slitheriteMembers(JsonObject tag) {
        Map<String, Boolean> members = new LinkedHashMap<>();
        for (JsonElement value : tag.getAsJsonArray("values")) {
            String id;
            boolean required;
            if (value.isJsonPrimitive()) {
                id = value.getAsString();
                required = true;
            } else {
                JsonObject entry = value.getAsJsonObject();
                id = entry.get("id").getAsString();
                required = !entry.has("required") || entry.get("required").getAsBoolean();
            }
            if (id.startsWith("etherology:") && id.contains("slitherite")) {
                members.put(id.substring("etherology:".length()), required);
            }
        }
        return Map.copyOf(members);
    }

    private static JsonObject readObject(Path path) throws IOException {
        requireCanonicalFile(path);
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static void requireCanonicalFile(Path path) {
        assertTrue(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), path.toString());
        assertFalse(Files.isSymbolicLink(path), path.toString());
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle.kts"))
                    && Files.isDirectory(candidate.resolve("src/main/generated"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate the Etherology repository root");
    }
}
