package ru.feytox.etherology.registry;

import org.junit.jupiter.api.Test;

import java.io.IOException;

final class SharedDeferredRegisterTest {

    @Test
    void centralizesArchitecturyAttachmentAndFailureState() throws IOException {
        ClassFileAssertions.assertContains(
                "/ru/feytox/etherology/registry/SharedDeferredRegister.class",
                "dev/architectury/registry/registries/DeferredRegister",
                "EtherologyBootstrap",
                "register",
                "attach",
                "attachmentFailure"
        );
    }
}
