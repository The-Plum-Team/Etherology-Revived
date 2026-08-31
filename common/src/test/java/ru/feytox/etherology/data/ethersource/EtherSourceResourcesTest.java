package ru.feytox.etherology.data.ethersource;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EtherSourceResourcesTest {

    private static final String DEFAULT_SOURCES =
            "/data/etherology/ether_sources/default.json";

    @Test
    void packagesTheExactCorrectedDefaultEtherSources() throws IOException {
        JsonElement json;
        InputStream stream = EtherSourceResourcesTest.class.getResourceAsStream(
                DEFAULT_SOURCES
        );
        assertNotNull(stream, "Missing Common Ether-source defaults");
        try (stream; InputStreamReader reader = new InputStreamReader(
                stream,
                StandardCharsets.UTF_8
        )) {
            json = JsonParser.parseReader(reader);
        }

        Map<Identifier, Float> actual = EtherSourcesDeserializer.deserialize(json);
        Map<Identifier, Float> expected = expectedSources();

        assertEquals(23, actual.size());
        assertEquals(expected, actual);
        assertTrue(actual.containsKey(Identifier.of("etherology", "primoshard_rella")));
        assertFalse(actual.containsKey(Identifier.of("etherology", "primoshard_rela")));
    }

    private static Map<Identifier, Float> expectedSources() {
        Map<Identifier, Float> sources = new LinkedHashMap<>();
        sources.put(Identifier.of("etherology", "primoshard_keta"), 4.0F);
        sources.put(Identifier.of("etherology", "primoshard_rella"), 4.0F);
        sources.put(Identifier.of("etherology", "primoshard_clos"), 4.0F);
        sources.put(Identifier.of("etherology", "primoshard_via"), 4.0F);
        sources.put(Identifier.of("minecraft", "redstone"), 2.0F);
        sources.put(Identifier.of("minecraft", "glowstone_dust"), 1.0F);
        sources.put(Identifier.of("minecraft", "lapis_lazuli"), 1.0F);
        sources.put(Identifier.of("minecraft", "quartz"), 1.0F);
        sources.put(Identifier.of("minecraft", "ender_pearl"), 4.0F);
        sources.put(Identifier.of("minecraft", "ender_eye"), 6.0F);
        sources.put(Identifier.of("minecraft", "blaze_powder"), 2.0F);
        sources.put(Identifier.of("minecraft", "ancient_debris"), 4.0F);
        sources.put(Identifier.of("minecraft", "chorus_fruit"), 2.0F);
        sources.put(Identifier.of("minecraft", "experience_bottle"), 8.0F);
        sources.put(Identifier.of("minecraft", "echo_shard"), 12.0F);
        sources.put(Identifier.of("minecraft", "sculk"), 12.0F);
        sources.put(Identifier.of("minecraft", "crying_obsidian"), 6.0F);
        sources.put(Identifier.of("minecraft", "magma_cream"), 2.0F);
        sources.put(Identifier.of("minecraft", "heart_of_the_sea"), 12.0F);
        sources.put(Identifier.of("minecraft", "gunpowder"), 1.0F);
        sources.put(Identifier.of("minecraft", "prismarine_crystals"), 1.0F);
        sources.put(Identifier.of("minecraft", "ghast_tear"), 4.0F);
        sources.put(Identifier.of("minecraft", "honeycomb"), 1.0F);
        return sources;
    }
}
