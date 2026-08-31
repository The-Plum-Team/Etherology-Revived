package ru.feytox.etherology.registry.misc;

import dev.onyxstudios.cca.api.v3.component.CopyableComponent;
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.feytox.etherology.gui.teldecore.data.TeldecoreComponent;

/**
 * Adapts player Teldecore progress to Cardinal Components on Fabric.
 */
public final class FabricTeldecoreComponent extends FabricComponentAdapter<TeldecoreComponent>
        implements CopyableComponent<FabricTeldecoreComponent>, AutoSyncedComponent {

    FabricTeldecoreComponent(PlayerEntity player) {
        super(new TeldecoreComponent(player));
    }

    @Override
    public void copyFrom(FabricTeldecoreComponent other) {
        getState().copyFrom(other.getState());
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity player) {
        return getState().shouldSyncWith(player);
    }
}
