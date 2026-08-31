package ru.feytox.etherology.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;

final class EtherealStorageFoundationResourcesTest {

    @Test
    void packagesThePersistentStorageMenuCoreResources()
            throws IOException {
        RuntimeResourceAssertions.assertTextContains(
                "/assets/etherology/blockstates/ethereal_storage.json",
                "etherology:block/ethereal_storage"
        );
        RuntimeResourceAssertions.assertTextContains(
                "/assets/etherology/models/block/ethereal_storage.json",
                "etherology:block/ethereal_storage"
        );
        RuntimeResourceAssertions.assertTextContains(
                "/assets/etherology/models/item/ethereal_storage.json",
                "\"parent\": \"etherology:block/ethereal_storage\""
        );
        RuntimeResourceAssertions.assertPng(
                "/assets/etherology/textures/block/ethereal_storage.png"
        );
        RuntimeResourceAssertions.assertPng(
                "/assets/etherology/textures/gui/ethereal_storage.png"
        );
        RuntimeResourceAssertions.assertTextContains(
                "/assets/etherology/models/item/glint_shard.json",
                "etherology:item/glint_shard_0"
        );
        RuntimeResourceAssertions.assertPng(
                "/assets/etherology/textures/item/glint_shard_0.png"
        );
        RuntimeResourceAssertions.assertTextContains(
                "/assets/etherology/lang/en_us.json",
                "\"block.etherology.ethereal_storage\": \"Ethereal Storage\"",
                "\"block.etherology.ethereal_storage.title\": \"Ethereal Storage\"",
                "\"item.etherology.glint_shard\": \"Glint\""
        );
        RuntimeResourceAssertions.assertTextContains(
                "/data/etherology/loot_tables/blocks/ethereal_storage.json",
                "\"name\": \"etherology:ethereal_storage\""
        );
        RuntimeResourceAssertions.assertTextContains(
                "/data/etherology/recipes/ethereal_storage.json",
                "\"item\": \"etherology:ethereal_storage\"",
                "\"item\": \"etherology:etheroscope\"",
                "\"item\": \"etherology:glint_shard\""
        );
    }
}
