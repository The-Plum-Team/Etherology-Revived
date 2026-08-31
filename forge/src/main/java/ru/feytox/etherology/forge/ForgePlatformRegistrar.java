package ru.feytox.etherology.forge;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import ru.feytox.etherology.bootstrap.PlatformRegistrar;

final class ForgePlatformRegistrar implements PlatformRegistrar {

    private final IEventBus modEventBus;

    ForgePlatformRegistrar(IEventBus modEventBus) {
        this.modEventBus = modEventBus;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void scheduleLoaderSetup(Runnable loaderSetup) {
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(loaderSetup));
    }
}
