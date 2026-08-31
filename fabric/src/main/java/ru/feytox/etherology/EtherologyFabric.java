package ru.feytox.etherology;

import net.fabricmc.api.ModInitializer;

public final class EtherologyFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Etherology.initialize();
    }
}
