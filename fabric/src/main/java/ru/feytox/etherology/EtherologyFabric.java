package ru.feytox.etherology;

import net.fabricmc.api.ModInitializer;
import ru.feytox.etherology.registry.particle.SharedParticleTypes;

public final class EtherologyFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        SharedParticleTypes.register();
        Etherology.initialize();
    }
}
