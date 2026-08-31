package ru.feytox.etherology;

import net.fabricmc.fabric.api.registry.SculkSensorFrequencyRegistry;
import ru.feytox.etherology.registry.misc.SharedGameEvents;

final class FabricGameEventHooks {

    private FabricGameEventHooks() {
    }

    static void registerSculkSensorFrequency() {
        SculkSensorFrequencyRegistry.register(SharedGameEvents.RESONANCE.get(), 10);
    }
}
