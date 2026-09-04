package ru.feytox.etherology.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import ru.feytox.etherology.bootstrap.EtherologyBootstrap;
import ru.feytox.etherology.block.pedestal.PedestalBlockEntityRemoval;
import ru.feytox.etherology.forge.block.pedestal.ForgePedestalBlockEntityRemovalBackend;
import ru.feytox.etherology.forge.network.ForgePedestalNetwork;
import software.bernie.geckolib.GeckoLib;

/**
 * Installs the Forge lifecycle adapter for Etherology.
 */
@Mod(EtherologyBootstrap.MOD_ID)
public final class EtherologyForge {

    /**
     * Schedules the loader handshake on the JavaFML common-setup work queue.
     *
     * @param loadingContext JavaFML context that owns this mod's lifecycle bus
     */
    public EtherologyForge(FMLJavaModLoadingContext loadingContext) {
        IEventBus modEventBus = loadingContext.getModEventBus();
        EventBuses.registerModEventBus(EtherologyBootstrap.MOD_ID, modEventBus);
        GeckoLib.initialize();
        ForgePedestalNetwork.register();
        PedestalBlockEntityRemoval.bind(
                ForgePedestalBlockEntityRemovalBackend.INSTANCE
        );
        EtherologyBootstrap.initialize(
                new ForgePlatformRegistrar(modEventBus)
        );
    }
}
