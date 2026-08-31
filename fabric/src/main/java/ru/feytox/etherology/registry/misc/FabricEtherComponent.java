package ru.feytox.etherology.registry.misc;

import dev.onyxstudios.cca.api.v3.component.CopyableComponent;
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import dev.onyxstudios.cca.api.v3.component.tick.ServerTickingComponent;
import net.minecraft.entity.LivingEntity;
import ru.feytox.etherology.magic.ether.EtherComponent;

/**
 * Adapts living-entity ether state to Cardinal Components on Fabric.
 */
public final class FabricEtherComponent extends FabricComponentAdapter<EtherComponent>
        implements CopyableComponent<FabricEtherComponent>, ServerTickingComponent, AutoSyncedComponent {

    FabricEtherComponent(LivingEntity entity) {
        super(new EtherComponent(entity));
    }

    @Override
    public void copyFrom(FabricEtherComponent other) {
        getState().copyFrom(other.getState());
    }

    @Override
    public void serverTick() {
        getState().serverTick();
    }
}
