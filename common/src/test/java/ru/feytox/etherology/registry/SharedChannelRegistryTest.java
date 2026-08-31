package ru.feytox.etherology.registry;

import org.junit.jupiter.api.Test;

import java.io.IOException;

final class SharedChannelRegistryTest {

    @Test
    void registersTheCollisionFreeChannelFoundationAcrossTheSharedLifecycle()
            throws IOException {
        ClassFileAssertions.assertContains(
                "/ru/feytox/etherology/registry/block/SharedBlocks.class",
                "SharedDeferredRegister",
                "RegistrySupplier",
                "ethereal_channel",
                "EtherealChannelFoundationBlock"
        );
        ClassFileAssertions.assertContains(
                "/ru/feytox/etherology/registry/block/SharedBlockEntities.class",
                "SharedDeferredRegister",
                "RegistrySupplier",
                "ethereal_channel_block_entity",
                "EtherealChannelFoundationBlockEntity",
                "SharedBlocks"
        );
        ClassFileAssertions.assertContains(
                "/ru/feytox/etherology/registry/item/SharedItems.class",
                "RegistrySupplier",
                "BlockItem",
                "ethereal_channel",
                "ETHEREAL_CHANNEL"
        );
    }

    @Test
    void leavesTheCanonicalFabricChannelClassesOutOfTheCommonArtifact() {
        ClassFileAssertions.assertAbsent(
                "/ru/feytox/etherology/block/etherealChannel/EtherealChannel.class"
        );
        ClassFileAssertions.assertAbsent(
                "/ru/feytox/etherology/block/etherealChannel/EtherealChannelBlockEntity.class"
        );
    }
}
