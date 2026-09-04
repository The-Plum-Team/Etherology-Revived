package ru.feytox.etherology.forge.block.pedestal;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import ru.feytox.etherology.block.pedestal.PedestalBlockEntityRemovalBackend;
import ru.feytox.etherology.forge.network.ForgePedestalNetwork;

/**
 * Routes shared Pedestal removals through the Forge tracking-chunk channel.
 */
public enum ForgePedestalBlockEntityRemovalBackend
        implements PedestalBlockEntityRemovalBackend {
    INSTANCE;

    /** {@inheritDoc} */
    @Override
    public void send(ServerWorld world, BlockPos pos) {
        ForgePedestalNetwork.send(world, pos);
    }
}
