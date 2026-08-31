package ru.feytox.etherology.data.ethersource;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class EtherSourceLoaderTest {

    @Test
    void deserializesNamespacedItemIdsAndFloatValues() {
        Map<Identifier, Float> values = EtherSourcesDeserializer.deserialize(
                JsonParser.parseString("""
                        {
                          "etherology:primoshard_rella": 4,
                          "minecraft:redstone": 2.5
                        }
                        """)
        );

        assertEquals(
                Map.of(
                        Identifier.of("etherology", "primoshard_rella"), 4.0F,
                        Identifier.of("minecraft", "redstone"), 2.5F
                ),
                values
        );
    }

    @Test
    void rejectsPayloadsOutsideTheFlatItemValueSchema() {
        assertThrows(
                IllegalStateException.class,
                () -> EtherSourcesDeserializer.deserialize(JsonParser.parseString("[]"))
        );
        assertThrows(
                RuntimeException.class,
                () -> EtherSourcesDeserializer.deserialize(JsonParser.parseString("""
                        {"Invalid Namespace:item": 4}
                        """))
        );
        assertThrows(
                NumberFormatException.class,
                () -> EtherSourcesDeserializer.deserialize(JsonParser.parseString("""
                        {"minecraft:redstone": "not-a-number"}
                        """))
        );
    }

    @Test
    void startsEmptyThenReplacesTheSnapshotAndSkipsMalformedResources() {
        assertEquals(Map.of(), EtherSourceLoader.INSTANCE.getEtherItems());

        Map<Identifier, JsonElement> firstReload = new LinkedHashMap<>();
        firstReload.put(
                Identifier.of("etherology", "valid_pack"),
                JsonParser.parseString("""
                        {"minecraft:redstone": 2}
                        """)
        );
        firstReload.put(
                Identifier.of("etherology", "structurally_malformed_pack"),
                JsonParser.parseString("[]")
        );
        firstReload.put(
                Identifier.of("etherology", "invalid_identifier_pack"),
                JsonParser.parseString("""
                        {"Invalid Namespace:item": 4}
                        """)
        );
        firstReload.put(
                Identifier.of("etherology", "invalid_number_pack"),
                JsonParser.parseString("""
                        {"minecraft:quartz": "not-a-number"}
                        """)
        );
        firstReload.put(
                Identifier.of("etherology", "unsupported_value_pack"),
                JsonParser.parseString("""
                        {"minecraft:quartz": {}}
                        """)
        );

        EtherSourceLoader.INSTANCE.apply(firstReload, null, null);

        Map<Identifier, Float> firstSnapshot = EtherSourceLoader.INSTANCE.getEtherItems();
        assertEquals(
                Map.of(Identifier.of("minecraft", "redstone"), 2.0F),
                firstSnapshot
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> firstSnapshot.put(Identifier.of("minecraft", "quartz"), 1.0F)
        );

        EtherSourceLoader.INSTANCE.apply(
                Map.of(
                        Identifier.of("etherology", "replacement_pack"),
                        JsonParser.parseString("""
                                {"minecraft:quartz": 1}
                                """)
                ),
                null,
                null
        );

        Map<Identifier, Float> replacement = EtherSourceLoader.INSTANCE.getEtherItems();
        assertEquals(
                Map.of(Identifier.of("minecraft", "quartz"), 1.0F),
                replacement
        );
        assertFalse(replacement.containsKey(Identifier.of("minecraft", "redstone")));
    }
}
