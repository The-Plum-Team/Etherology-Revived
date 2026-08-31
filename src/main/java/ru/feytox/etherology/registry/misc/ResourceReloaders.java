package ru.feytox.etherology.registry.misc;

import dev.architectury.registry.ReloadListenerRegistry;
import lombok.experimental.UtilityClass;
import net.minecraft.resource.ResourceType;
import ru.feytox.etherology.data.ethersource.EtherSourceLoader;
import ru.feytox.etherology.util.misc.EIdentifier;

@UtilityClass
public class ResourceReloaders {

    public static void registerServerData() {
        ReloadListenerRegistry.register(
                ResourceType.SERVER_DATA,
                EtherSourceLoader.INSTANCE,
                EIdentifier.of("ether_sources"));
    }
}
