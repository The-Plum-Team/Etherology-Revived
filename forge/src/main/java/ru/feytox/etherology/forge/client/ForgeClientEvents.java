package ru.feytox.etherology.forge.client;

import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.registry.client.rendering.RenderTypeRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ru.feytox.etherology.bootstrap.EtherologyBootstrap;
import ru.feytox.etherology.item.EtherealStorageInputItem;
import ru.feytox.etherology.item.glints.GlintEtherData;
import ru.feytox.etherology.registry.block.SharedBlockEntities;
import ru.feytox.etherology.registry.block.SharedBlocks;
import ru.feytox.etherology.registry.item.SharedItems;
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
        event.enqueueWork(ForgeClientEvents::registerClientContent);
    }

    private static void registerClientContent() {
        HandledScreens.register(
                SharedScreenHandlers.ETHEREAL_STORAGE.get(),
                EtherealStorageFoundationScreen::new
        );
        BlockEntityRendererRegistry.register(
                SharedBlockEntities.ETHEREAL_STORAGE.get(),
                EtherealStorageFoundationRenderer::new
        );
        RenderTypeRegistry.register(
                RenderLayer.getCutout(),
                SharedBlocks.ETHEREAL_STORAGE.get(),
                SharedBlocks.ETHEREAL_CHANNEL.get()
        );
        ModelPredicateProviderRegistry.register(
                SharedItems.GLINT_SHARD.get(),
                new Identifier("ether_percentage"),
                (stack, world, entity, seed) -> GlintEtherData.getStoredEther(stack)
                        / ((EtherealStorageInputItem) stack.getItem()).getMaxEther()
        );
    }
}
