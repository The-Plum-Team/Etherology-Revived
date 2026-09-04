package ru.feytox.etherology;

import net.fabricmc.api.ModInitializer;
import ru.feytox.etherology.item.FabricLensRuntimeBackend;
import ru.feytox.etherology.item.LensRuntime;
import ru.feytox.etherology.registry.particle.SharedParticleTypes;

public final class EtherologyFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        LensRuntime.bind(FabricLensRuntimeBackend.INSTANCE);
        SharedParticleTypes.register();
        Etherology.initialize();
    }
}
