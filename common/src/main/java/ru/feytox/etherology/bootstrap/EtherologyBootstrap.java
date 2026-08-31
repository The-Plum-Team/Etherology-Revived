package ru.feytox.etherology.bootstrap;

import ru.feytox.etherology.registry.block.SharedBlockEntities;
import ru.feytox.etherology.registry.block.SharedBlocks;
import ru.feytox.etherology.registry.item.SharedItems;
import ru.feytox.etherology.registry.misc.SharedScreenHandlers;
import ru.feytox.etherology.registry.misc.SharedSounds;

/**
 * Installs loader-neutral Etherology registrations and coordinates the loader lifecycle handshake.
 */
public final class EtherologyBootstrap {

    /**
     * Identifies Etherology registrations consistently across loader entry points.
     */
    public static final String MOD_ID = "etherology";

    private static final BootstrapLifecycle LIFECYCLE = new BootstrapLifecycle();

    private EtherologyBootstrap() {
    }

    /**
     * Attaches shared registries before binding the loader handshake exactly once.
     *
     * @param registrar adapter for the active loader's setup lifecycle
     */
    public static void initialize(PlatformRegistrar registrar) {
        SharedBlocks.register();
        SharedItems.register();
        SharedBlockEntities.register();
        SharedScreenHandlers.register();
        SharedSounds.register();
        LIFECYCLE.initialize(registrar);
    }
}
