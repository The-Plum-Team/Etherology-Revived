package ru.feytox.etherology.magic.ether;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Defines one server-owned node in a directed Ether network.
 *
 * <p>Implementations expose capacity, current Ether units, accepted faces, and one optional
 * output. Mutations must remain bounded and must publish persistent changes when the
 * implementation is a block entity.
 */
public interface EtherStorage {
    /**
     * Returns the maximum Ether units held by this node's primary buffer.
     *
     * @return non-negative primary-buffer capacity
     */
    float getMaxEther();

    /**
     * Returns the Ether units in this node's primary buffer.
     *
     * @return bounded current Ether units
     */
    float getStoredEther();

    /**
     * Returns the maximum Ether units accepted or supplied in one exchange.
     *
     * @return non-negative per-exchange limit
     */
    float getTransferSize();

    /**
     * Replaces the primary buffer after shared bounded arithmetic has resolved a mutation.
     *
     * @param value bounded resulting Ether units
     * @apiNote Consumers should use {@link #increment(float)} or {@link #decrement(float)}.
     */
    @ApiStatus.OverrideOnly
    void setStoredEther(float value);

    /**
     * Reports whether a supplier may enter through the queried outward face of this node.
     *
     * @param side this node's face toward the supplier
     * @return whether that face accepts Ether
     */
    boolean isInputSide(Direction side);

    /**
     * Returns the one outward face used to locate the next consumer.
     *
     * @return output face, or {@code null} when this node cannot currently output
     */
    @Nullable
    Direction getOutputSide();

    /**
     * Returns the world position from which the output face is offset.
     *
     * @return owning block position
     */
    BlockPos getStoragePos();

    /**
     * Advances this node on its implementation-defined server cadence.
     *
     * @param world logical server world; callers must invoke this on the server thread
     */
    void transferTick(ServerWorld world);

    /**
     * Returns all Ether available to the network, including auxiliary buffers.
     *
     * @return transportable Ether units
     */
    default float getTransportableEther() {
        return getStoredEther();
    }

    /**
     * Reports whether input from one face must evaporate instead of transferring.
     *
     * @param fromSide this node's face toward the supplier
     * @return whether the crossing is incompatible
     */
    default boolean isCrossEvaporate(Direction fromSide) {
        return false;
    }

    /**
     * Gives loader client code an optional hook for a cross-evaporation presentation.
     *
     * @param pos particle origin
     * @param world presentation world
     * @param direction outgoing direction
     * @return whether the implementation emitted its presentation
     */
    default boolean spawnCrossParticles(BlockPos pos, World world, Direction direction) {
        return false;
    }

    /**
     * Reports whether the queried face is the current output.
     *
     * @param direction queried outward face
     * @return whether it equals the non-null output face
     */
    default boolean isOutputSide(Direction direction) {
        Direction outputSide = getOutputSide();
        return outputSide != null && outputSide.equals(direction);
    }

    /**
     * Reports whether this node accepts Ether from a specific supplier implementation.
     *
     * @param supplier adjacent supplier
     * @return whether supplier compatibility allows input
     */
    default boolean canInputFrom(EtherStorage supplier) {
        return true;
    }

    /**
     * Reports whether this node may output to a specific consumer implementation.
     *
     * @param consumer adjacent consumer
     * @return whether consumer compatibility allows output
     */
    default boolean canOutputTo(EtherStorage consumer) {
        return consumer instanceof EtherPipe;
    }

    /**
     * Reports whether gameplay state currently pauses outgoing transfer.
     *
     * @return whether this node is gated or otherwise inactive
     */
    boolean isActivated();

    /**
     * Attempts one bounded transfer through the current output without loading chunks.
     *
     * @param world logical server world on the server thread
     */
    default void transfer(ServerWorld world) {
        float etherValue = getTransportableEther();
        if (etherValue == 0 || isActivated()) {
            clearEvaporationState();
            return;
        }

        Direction outputSide = getOutputSide();
        if (outputSide != null) transferTo(world, outputSide);
    }

    /**
     * Attempts one bounded exchange toward an already-resolved output face.
     *
     * <p>Unloaded destinations pause transfer. Missing loaded destinations evaporate pipe
     * Ether, while rejected or full consumers leave the supplier balance conserved.
     *
     * @param world logical server world on the server thread
     * @param outputSide outward face used to locate the consumer
     */
    default void transferTo(ServerWorld world, Direction outputSide) {
        BlockPos pos = getStoragePos();
        Vec3i outputVec = outputSide.getVector();
        BlockPos nextPos = pos.add(outputVec);

        WorldChunk nextChunk = world.getChunkManager().getWorldChunk(
                nextPos.getX() >> 4,
                nextPos.getZ() >> 4
        );
        if (nextChunk == null) {
            clearEvaporationState();
            return;
        }

        if (nextChunk.getBlockEntity(
                nextPos,
                WorldChunk.CreationType.CHECK
        ) instanceof EtherStorage consumer) {
            if (this instanceof EvaporatingEtherPipe pipe) {
                pipe.setEvaporating(false);
                pipe.setCrossEvaporating(false);
            }

            if (consumer.isCrossEvaporate(outputSide.getOpposite())) {
                evaporate(true);
                return;
            }
            if (!consumer.isInputSide(outputSide.getOpposite())) return;
            if (!consumer.canInputFrom(this)) return;
            if (!canOutputTo(consumer)) return;

            EtherTransfer.moveAvailable(this, consumer);
        } else evaporate(false);
    }

    private void evaporate(boolean crossUseless) {
        if (this instanceof EvaporatingEtherPipe pipe) {
            pipe.setEvaporating(getStoredEther() != 0);
            pipe.setCrossEvaporating(crossUseless);
            decrement(0.2f);
        }
    }

    private void clearEvaporationState() {
        if (this instanceof EvaporatingEtherPipe pipe) {
            pipe.setEvaporating(false);
            pipe.setCrossEvaporating(false);
        }
    }

    /**
     * Adds Ether to the primary buffer and returns only the amount that did not fit.
     *
     * @param value non-negative Ether units offered
     * @return excess Ether units for supplier rollback
     */
    default float increment(float value) {
        float storedEther = getStoredEther();
        float maxEther = getMaxEther();
        double offeredEther = (double) storedEther + (double) value;
        float newVal = (float) Math.min(offeredEther, (double) maxEther);
        setStoredEther(newVal);
        return (float) Math.max(0.0, offeredEther - (double) maxEther);
    }

    /**
     * Removes up to the requested Ether units, repairing only float-rounding dust.
     *
     * @param value non-negative maximum Ether units to remove
     * @return Ether units actually removed
     */
    default float decrement(float value) {
        float storedEther = getStoredEther();
        float newVal = Math.max(storedEther - value, 0);
        float roundingTolerance = 2.0f * Math.ulp(
                Math.max(Math.abs(storedEther), Math.abs(value))
        );
        if (newVal > 0 && newVal <= roundingTolerance) {
            newVal = 0;
        }
        setStoredEther(newVal);
        return storedEther - newVal;
    }
}
