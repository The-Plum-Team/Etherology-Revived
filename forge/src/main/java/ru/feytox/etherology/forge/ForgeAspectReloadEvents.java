package ru.feytox.etherology.forge;

import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.feytox.etherology.bootstrap.EtherologyBootstrap;
import ru.feytox.etherology.data.aspects.AspectsLoader;

/**
 * Invalidates resolved aspect data after Forge replaces server or client datapack registries.
 */
@Mod.EventBusSubscriber(
        modid = EtherologyBootstrap.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class ForgeAspectReloadEvents {

    private ForgeAspectReloadEvents() {
    }

    /**
     * Clears static aspect state once for an integrated reload and on remote client sync.
     *
     * @param event notification that dynamic registries and tags were replaced
     */
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.shouldUpdateStaticData()) {
            AspectsLoader.clearCache();
        }
    }
}
