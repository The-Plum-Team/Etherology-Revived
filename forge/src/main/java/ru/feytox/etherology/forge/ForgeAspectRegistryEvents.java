package ru.feytox.etherology.forge;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DataPackRegistryEvent;
import ru.feytox.etherology.bootstrap.EtherologyBootstrap;
import ru.feytox.etherology.magic.aspects.AspectRegistryPart;
import ru.feytox.etherology.registry.misc.SharedAspectRegistries;

/**
 * Registers Etherology's synchronized aspects datapack registry on Forge.
 */
@Mod.EventBusSubscriber(
        modid = EtherologyBootstrap.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class ForgeAspectRegistryEvents {

    private ForgeAspectRegistryEvents() {
    }

    /**
     * Installs the same registry key and codec used by Fabric, including client synchronization.
     *
     * @param event Forge datapack-registry construction event
     */
    @SubscribeEvent
    public static void registerRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(
                SharedAspectRegistries.ASPECTS,
                AspectRegistryPart.CODEC,
                AspectRegistryPart.CODEC
        );
    }
}
