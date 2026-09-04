package ru.feytox.etherology.block.pedestal;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Sends the loader-specific message that removes a stale client Pedestal entity.
 */
@FunctionalInterface
public interface PedestalBlockEntityRemovalBackend {

    /**
     * Notifies every client tracking the supplied server position.
     *
     * @param world authoritative server world
     * @param pos removed Pedestal position
     */
    void send(ServerWorld world, BlockPos pos);
}
