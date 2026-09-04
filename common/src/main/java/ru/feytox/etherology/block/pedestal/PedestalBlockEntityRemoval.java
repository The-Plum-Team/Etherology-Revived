package ru.feytox.etherology.block.pedestal;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;

/**
 * Holds the loader backend for explicit stale Pedestal block-entity removal.
 */
public final class PedestalBlockEntityRemoval {

    private static final PedestalBlockEntityRemovalBackend UNAVAILABLE =
            (world, pos) -> {
                throw new IllegalStateException(
                        "The Pedestal block-entity removal backend is not bound"
                );
            };

    private static volatile PedestalBlockEntityRemovalBackend backend = UNAVAILABLE;

    private PedestalBlockEntityRemoval() {
    }

    /**
     * Binds one loader backend, permitting only an idempotent repeat with the same instance.
     *
     * @param backend active loader implementation
     */
    public static synchronized void bind(
            PedestalBlockEntityRemovalBackend backend
    ) {
        Objects.requireNonNull(backend, "backend");
        if (PedestalBlockEntityRemoval.backend != UNAVAILABLE
                && PedestalBlockEntityRemoval.backend != backend) {
            throw new IllegalStateException(
                    "A Pedestal block-entity removal backend is already bound"
            );
        }
        PedestalBlockEntityRemoval.backend = backend;
    }

    /**
     * Sends an explicit removal message through the bound loader backend.
     *
     * @param world authoritative server world
     * @param pos removed Pedestal position
     */
    public static void send(ServerWorld world, BlockPos pos) {
        backend.send(world, pos);
    }
}
