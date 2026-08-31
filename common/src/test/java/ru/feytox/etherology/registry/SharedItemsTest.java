package ru.feytox.etherology.registry;

import org.junit.jupiter.api.Test;

import java.io.IOException;

final class SharedItemsTest {

    private static final String SHARED_ITEMS =
            "/ru/feytox/etherology/registry/item/SharedItems.class";

    @Test
    void declaresItemsThroughArchitecturyWithoutInitializingMinecraftRegistries()
            throws IOException {
        ClassFileAssertions.assertContains(
                SHARED_ITEMS,
                "SharedDeferredRegister",
                "RegistrySupplier",
                "ether",
                "glint_shard",
                "EtherealStorageInputItem",
                "BlockItem",
                "ethereal_storage",
                "SharedBlocks",
                "register"
        );
    }

    @Test
    void doesNotReplaceTheCanonicalFabricItemRegistryClass() {
        ClassFileAssertions.assertAbsent(
                "/ru/feytox/etherology/registry/item/EItems.class"
        );
    }
}
