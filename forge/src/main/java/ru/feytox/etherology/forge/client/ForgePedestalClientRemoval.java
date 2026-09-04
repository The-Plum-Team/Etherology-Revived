package ru.feytox.etherology.forge.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.function.Consumer;

/**
 * Removes a stale Pedestal block entity from the physical client world.
 */
public enum ForgePedestalClientRemoval implements Consumer<BlockPos> {
    INSTANCE;

    /** {@inheritDoc} */
    @Override
    public void accept(BlockPos pos) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world != null) world.removeBlockEntity(pos);
    }
}
