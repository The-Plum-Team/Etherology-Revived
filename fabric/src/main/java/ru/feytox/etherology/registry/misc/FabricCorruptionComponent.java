package ru.feytox.etherology.registry.misc;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import dev.onyxstudios.cca.api.v3.component.tick.ServerTickingComponent;
import net.minecraft.world.chunk.Chunk;
import ru.feytox.etherology.magic.corruption.CorruptionComponent;

/**
 * Adapts chunk corruption state to Cardinal Components on Fabric.
 */
public final class FabricCorruptionComponent extends FabricComponentAdapter<CorruptionComponent>
        implements ServerTickingComponent, AutoSyncedComponent {

    FabricCorruptionComponent(Chunk chunk) {
        super(new CorruptionComponent(chunk));
    }

    @Override
    public void serverTick() {
        getState().serverTick();
    }
}
