package ru.feytox.etherology.block.pedestal;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import ru.feytox.etherology.network.interaction.RemoveBlockEntityS2C;

/**
 * Sends the existing Fabric removal packet to clients tracking the Pedestal.
 */
public enum FabricPedestalBlockEntityRemovalBackend
        implements PedestalBlockEntityRemovalBackend {
    INSTANCE;

    /** {@inheritDoc} */
    @Override
    public void send(ServerWorld world, BlockPos pos) {
        RemoveBlockEntityS2C packet = new RemoveBlockEntityS2C(pos);
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, packet);
        }
    }
}
