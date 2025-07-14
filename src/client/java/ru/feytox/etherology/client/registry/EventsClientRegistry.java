package ru.feytox.etherology.client.registry;

import ru.feytox.etherology.client.block.seal.SealBlockRenderer;
import ru.feytox.etherology.client.item.pranaVision.RevelationRenderer;

public class EventsClientRegistry {

    public static void register() {
        RevelationRenderer.registerRendering();
        SealBlockRenderer.registerRendering();
    }
}
