package ru.feytox.etherology.registry.misc;

import dev.onyxstudios.cca.api.v3.component.CopyableComponent;
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import dev.onyxstudios.cca.api.v3.component.tick.ServerTickingComponent;
import net.minecraft.entity.player.PlayerEntity;
import ru.feytox.etherology.gui.teldecore.data.VisitedComponent;

/**
 * Adapts player location discoveries to Cardinal Components on Fabric.
 */
public final class FabricVisitedComponent extends FabricComponentAdapter<VisitedComponent>
        implements CopyableComponent<FabricVisitedComponent>, ServerTickingComponent, AutoSyncedComponent {

    FabricVisitedComponent(PlayerEntity player) {
        super(new VisitedComponent(player));
    }

    @Override
    public void copyFrom(FabricVisitedComponent other) {
        getState().copyFrom(other.getState());
    }

    @Override
    public void serverTick() {
        getState().serverTick();
    }
}
