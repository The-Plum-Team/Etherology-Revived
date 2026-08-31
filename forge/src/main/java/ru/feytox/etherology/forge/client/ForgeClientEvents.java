package ru.feytox.etherology.forge.client;

import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ru.feytox.etherology.bootstrap.EtherologyBootstrap;
import ru.feytox.etherology.registry.misc.SharedScreenHandlers;

/**
 * Installs Forge-only client registrations without linking client classes from the common entrypoint.
 */
@Mod.EventBusSubscriber(
        modid = EtherologyBootstrap.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class ForgeClientEvents {

    private ForgeClientEvents() {
    }

    /**
     * Enqueues the screen binding after Forge finishes constructing client registries.
     *
     * @param event Forge client-setup work queue
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> HandledScreens.register(
                SharedScreenHandlers.ETHEREAL_STORAGE.get(),
                EtherealStorageFoundationScreen::new
        ));
    }
}
