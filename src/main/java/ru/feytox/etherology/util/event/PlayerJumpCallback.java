package ru.feytox.etherology.util.event;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;

public interface PlayerJumpCallback {

    Event<PlayerJumpCallback> BEFORE_JUMP = EventFactory.of(
            listeners -> player -> {
                for (var listener : listeners) {
                    var result = listener.beforeJump(player);
                    if (result != ActionResult.PASS)
                        return result;
                }

                return ActionResult.PASS;
            });

    ActionResult beforeJump(PlayerEntity player);
}
