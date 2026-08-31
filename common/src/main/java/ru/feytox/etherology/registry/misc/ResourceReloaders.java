package ru.feytox.etherology.registry.misc;

import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import ru.feytox.etherology.bootstrap.EtherologyBootstrap;
import ru.feytox.etherology.data.ethersource.EtherSourceLoader;

/**
 * Owns loader-neutral server-data reload-listener attachment.
 */
public final class ResourceReloaders {

    private static final Identifier ETHER_SOURCES_ID = Identifier.of(
            EtherologyBootstrap.MOD_ID,
            "ether_sources"
    );

    private static boolean registered;

    private ResourceReloaders() {
    }

    /**
     * Attaches the Ether-source listener as {@code etherology:ether_sources} exactly once.
     */
    public static synchronized void registerServerData() {
        if (registered) {
            return;
        }

        ReloadListenerRegistry.register(
                ResourceType.SERVER_DATA,
                EtherSourceLoader.INSTANCE,
                ETHER_SOURCES_ID
        );
        registered = true;
    }
}
