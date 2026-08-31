package ru.feytox.etherology.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;

final class EtherItemResourcesTest {

    @Test
    void packagesTheEtherModelTextureAndTranslation() throws IOException {
        RuntimeResourceAssertions.assertTextContains(
                "/assets/etherology/models/item/ether.json",
                "\"layer0\": \"etherology:item/ether\""
        );
        RuntimeResourceAssertions.assertPng(
                "/assets/etherology/textures/item/ether.png"
        );
        RuntimeResourceAssertions.assertTextContains(
                "/assets/etherology/lang/en_us.json",
                "\"item.etherology.ether\": \"Ether\""
        );
    }
}
