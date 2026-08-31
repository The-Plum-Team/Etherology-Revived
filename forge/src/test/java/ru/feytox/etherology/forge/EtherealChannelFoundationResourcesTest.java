package ru.feytox.etherology.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;

final class EtherealChannelFoundationResourcesTest {

    @Test
    void packagesTheCanonicalMultipartChannelPresentation() throws IOException {
        RuntimeResourceAssertions.assertTextContains(
                "/assets/etherology/blockstates/ethereal_channel.json",
                "\"north\": \"in\"",
                "\"north\": \"out\"",
                "\"facing\": \"north\"",
                "\"is_cross\": \"true\"",
                "\"in_case\": \"true\"",
                "etherology:block/ethereal_channel_input",
                "etherology:block/ethereal_channel_output",
                "etherology:block/ethereal_channel_central_line",
                "etherology:block/ethereal_channel_central_cross",
                "etherology:block/ethereal_channel_in_case"
        );
        RuntimeResourceAssertions.assertTextContains(
                "/assets/etherology/models/block/ethereal_channel_input.json",
                "etherology:block/ethereal_channel_inside",
                "etherology:block/ethereal_channel_input"
        );
        RuntimeResourceAssertions.assertTextContains(
                "/assets/etherology/models/block/ethereal_channel_output.json",
                "etherology:block/ethereal_channel_inside",
                "etherology:block/ethereal_channel_output"
        );
        RuntimeResourceAssertions.assertTextContains(
                "/assets/etherology/models/block/ethereal_channel_central_line.json",
                "etherology:block/ethereal_channel_central_line"
        );
        RuntimeResourceAssertions.assertTextContains(
                "/assets/etherology/models/block/ethereal_channel_central_cross.json",
                "etherology:block/ethereal_channel_central_cross"
        );
        RuntimeResourceAssertions.assertTextContains(
                "/assets/etherology/models/block/ethereal_channel_in_case.json",
                "etherology:block/channel_case_front",
                "etherology:block/channel_case"
        );
    }

    @Test
    void packagesTheChannelItemTexturesAndTranslation() throws IOException {
        RuntimeResourceAssertions.assertTextContains(
                "/assets/etherology/models/item/ethereal_channel.json",
                "etherology:item/ethereal_channel"
        );
        RuntimeResourceAssertions.assertPng(
                "/assets/etherology/textures/item/ethereal_channel.png"
        );
        RuntimeResourceAssertions.assertPng(
                "/assets/etherology/textures/block/ethereal_channel_input.png"
        );
        RuntimeResourceAssertions.assertPng(
                "/assets/etherology/textures/block/ethereal_channel_output.png"
        );
        RuntimeResourceAssertions.assertPng(
                "/assets/etherology/textures/block/ethereal_channel_inside.png"
        );
        RuntimeResourceAssertions.assertPng(
                "/assets/etherology/textures/block/ethereal_channel_central_line.png"
        );
        RuntimeResourceAssertions.assertPng(
                "/assets/etherology/textures/block/ethereal_channel_central_cross.png"
        );
        RuntimeResourceAssertions.assertPng(
                "/assets/etherology/textures/block/channel_case.png"
        );
        RuntimeResourceAssertions.assertPng(
                "/assets/etherology/textures/block/channel_case_front.png"
        );
        RuntimeResourceAssertions.assertTextContains(
                "/assets/etherology/lang/en_us.json",
                "\"block.etherology.ethereal_channel\": \"Ethereal Channel\""
        );
    }

    @Test
    void keepsUnacceptedChannelServerDataOutOfTheForgeSlice() {
        RuntimeResourceAssertions.assertAbsent(
                "/data/etherology/loot_tables/blocks/ethereal_channel.json"
        );
        RuntimeResourceAssertions.assertAbsent(
                "/data/etherology/recipes/ethereal_channel.json"
        );
        RuntimeResourceAssertions.assertAbsent(
                "/data/etherology/advancements/recipes/misc/ethereal_channel.json"
        );
    }
}
