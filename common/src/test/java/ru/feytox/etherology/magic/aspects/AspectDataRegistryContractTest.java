package ru.feytox.etherology.magic.aspects;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class AspectDataRegistryContractTest {

    private static final String RESOURCE_ROOT =
            "/data/etherology/etherology/aspects/";

    @Test
    void containerIdsPreserveTheLegacyTypedSerializationContract() {
        AspectContainerId item = AspectContainerId.of("minecraft:stone");
        assertEquals(new Identifier("minecraft", "stone"), item.getId());
        assertSame(AspectContainerType.ITEM, item.getContainerType());
        assertEquals("minecraft:stone", item.toString());
        assertEquals("minecraft:item_stone", item.toTypedId().toString());
        assertEquals(
                item,
                AspectContainerId.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString("\"minecraft:stone\"")
                ).result().orElseThrow()
        );
        assertEquals(
                "minecraft:stone",
                AspectContainerId.CODEC.encodeStart(JsonOps.INSTANCE, item)
                        .result()
                        .orElseThrow()
                        .getAsString()
        );

        Map<AspectContainerType, String> typedIds = Map.of(
                AspectContainerType.ENTITY, "entity:minecraft:zombie",
                AspectContainerType.POTION, "potion:minecraft:water",
                AspectContainerType.SPLASH_POTION,
                "splash_potion:minecraft:water",
                AspectContainerType.LINGERING_POTION,
                "lingering_potion:minecraft:water",
                AspectContainerType.TIPPED_ARROW,
                "tipped_arrow:minecraft:water"
        );
        typedIds.forEach((type, serialized) -> {
            AspectContainerId parsed = AspectContainerId.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseString(
                            "\"" + serialized + "\""
                    ))
                    .result()
                    .orElseThrow();
            assertSame(type, parsed.getContainerType());
            assertEquals(serialized, parsed.toString());
            assertEquals(
                    serialized,
                    AspectContainerId.CODEC
                            .encodeStart(JsonOps.INSTANCE, parsed)
                            .result()
                            .orElseThrow()
                            .getAsString()
            );
        });

        AspectContainerId compactPotion = AspectContainerId.of("potion:water");
        assertEquals(new Identifier("minecraft", "water"), compactPotion.getId());
        assertSame(AspectContainerType.POTION, compactPotion.getContainerType());
        assertEquals("potion:minecraft:water", compactPotion.toString());
        assertEquals("minecraft:potion_water", compactPotion.toTypedId().toString());

        assertSame(
                AspectContainerType.SPLASH_POTION,
                AspectContainerType.getByPrefix(
                        "SpLaSh_PoTiOn",
                        AspectContainerType.ITEM
                )
        );
        assertSame(
                AspectContainerType.ITEM,
                AspectContainerType.getByPrefix(
                        "minecraft",
                        AspectContainerType.ITEM
                )
        );
        for (String prefix : List.of(
                "entity",
                "potion",
                "splash_potion",
                "lingering_potion",
                "tipped_arrow"
        )) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> AspectContainerId.of(prefix),
                    prefix
            );
            assertThrows(
                    IllegalArgumentException.class,
                    () -> AspectContainerId.of(prefix + ":"),
                    prefix + ":"
            );
        }
    }

    @Test
    void entryCodecSeparatesMetadataFromAuthoredAspects() {
        JsonElement json = JsonParser.parseString("""
                {
                  "priority": 7,
                  "parents": ["minecraft:stone", "entity:zombie"],
                  "gema": 2,
                  "materra": 6
                }
                """);

        AspectEntry entry = AspectEntry.CODEC.parse(JsonOps.INSTANCE, json)
                .result()
                .orElseThrow();
        assertEquals(7, entry.priority());
        assertEquals(
                java.util.List.of(
                        AspectContainerId.of("minecraft:stone"),
                        AspectContainerId.of("entity:zombie")
                ),
                entry.parents()
        );
        assertEquals(
                Map.of(Aspect.GEMA, 2, Aspect.MATERRA, 6),
                entry.aspects().getAspects()
        );

        JsonElement encoded = AspectEntry.CODEC
                .encodeStart(JsonOps.INSTANCE, entry)
                .result()
                .orElseThrow();
        assertEquals(7, encoded.getAsJsonObject().get("priority").getAsInt());
        assertEquals(2, encoded.getAsJsonObject().getAsJsonArray("parents").size());
        assertEquals(2, encoded.getAsJsonObject().get("gema").getAsInt());
        assertEquals(6, encoded.getAsJsonObject().get("materra").getAsInt());
    }

    @Test
    void exactCanonicalRegistryPartsDecodeAndResolveEveryParent()
            throws IOException {
        AspectRegistryPart etherology = decode("etherology.json");
        AspectRegistryPart vanilla = decode("vanilla.json");

        assertEquals(141, etherology.aspectEntries().size());
        assertEquals(1_616, vanilla.aspectEntries().size());

        Map<AspectContainerId, AspectContainer> resolved = AspectRegistryPart
                .merge(vanilla, etherology)
                .applyParents();
        assertEquals(1_757, resolved.size());
        assertEquals(
                Map.of(Aspect.GEMA, 2, Aspect.MATERRA, 6),
                resolved.get(AspectContainerId.of("etherology:pedestal"))
                        .getAspects()
        );
        assertEquals(
                Map.of(Aspect.PLANTA, 4),
                resolved.get(AspectContainerId.of("etherology:peach_planks"))
                        .getAspects()
        );
        assertEquals(
                Map.of(Aspect.VITER, 2, Aspect.MATERRA, 2),
                resolved.get(AspectContainerId.of("potion:water"))
                        .getAspects()
        );
    }

    private static AspectRegistryPart decode(String resourceName) throws IOException {
        try (InputStream stream = AspectDataRegistryContractTest.class
                .getResourceAsStream(RESOURCE_ROOT + resourceName)) {
            assertNotNull(stream, resourceName);
            JsonElement json = JsonParser.parseReader(new InputStreamReader(
                    stream,
                    StandardCharsets.UTF_8
            ));
            return AspectRegistryPart.CODEC.parse(JsonOps.INSTANCE, json)
                    .result()
                    .orElseThrow();
        }
    }
}
