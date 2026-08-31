package ru.feytox.etherology.registry.misc;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.event.GameEvent;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns loader-neutral game-event registrations accepted by every active loader.
 */
public final class SharedGameEvents {

    private static final SharedDeferredRegister<GameEvent> GAME_EVENTS =
            SharedDeferredRegister.create(RegistryKeys.GAME_EVENT);

    /**
     * Supplies Etherology's tuning-fork resonance event after game events register.
     */
    public static final RegistrySupplier<GameEvent> RESONANCE =
            register("etherology_resonance", 16);

    private SharedGameEvents() {
    }

    /**
     * Attaches the shared game-event registry once before loader registry events run.
     */
    public static void register() {
        GAME_EVENTS.attach();
    }

    private static RegistrySupplier<GameEvent> register(String id, int range) {
        return GAME_EVENTS.register(id, () -> new GameEvent(id, range));
    }
}
