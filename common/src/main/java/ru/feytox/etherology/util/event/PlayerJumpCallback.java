package ru.feytox.etherology.util.event;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;

/**
 * Receives loader-neutral player jump notifications before upward movement begins.
 */
@FunctionalInterface
public interface PlayerJumpCallback {

    /** Dispatches a jump to listeners until one returns a result other than pass. */
    Event<PlayerJumpCallback> BEFORE_JUMP = EventFactory.of(
            listeners -> player -> {
                for (PlayerJumpCallback listener : listeners) {
                    ActionResult result = listener.beforeJump(player);
                    if (result != ActionResult.PASS) {
                        return result;
                    }
                }

                return ActionResult.PASS;
            }
    );

    /**
     * Handles a player immediately before the jump is applied.
     *
     * @param player jumping player
     * @return pass to continue dispatching, or another result to stop dispatching
     */
    ActionResult beforeJump(PlayerEntity player);
}
