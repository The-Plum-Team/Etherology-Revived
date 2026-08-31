package ru.feytox.etherology.forge;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.feytox.etherology.bootstrap.EtherologyBootstrap;
import ru.feytox.etherology.util.event.PlayerJumpCallback;

/**
 * Bridges Forge's player-jump notification into the loader-neutral callback.
 */
@Mod.EventBusSubscriber(
        modid = EtherologyBootstrap.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class ForgeForestLanternEvents {

    private ForgeForestLanternEvents() {
    }

    /**
     * Publishes player jumps to Common without retaining a Forge dependency there.
     *
     * @param event Forge's living-entity jump notification
     */
    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof PlayerEntity player) {
            PlayerJumpCallback.BEFORE_JUMP.invoker().beforeJump(player);
        }
    }
}
