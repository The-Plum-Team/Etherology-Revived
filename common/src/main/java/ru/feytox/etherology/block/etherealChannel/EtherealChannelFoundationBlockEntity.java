package ru.feytox.etherology.block.etherealChannel;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.enums.PipeSide;
import ru.feytox.etherology.magic.ether.EtherDisplay;
import ru.feytox.etherology.magic.ether.EvaporatingEtherPipe;
import ru.feytox.etherology.registry.block.SharedBlockEntities;

/**
 * Persists and transfers the single Ether unit carried by a directed channel.
 */
public final class EtherealChannelFoundationBlockEntity extends BlockEntity
        implements EvaporatingEtherPipe, EtherDisplay {

    private static final String STORED_ETHER_KEY = "stored_ether";
    private static final String EVAPORATING_KEY = "evaporating";
    private static final String CROSS_EVAPORATING_KEY = "cross_evaporating";
    private static final float CAPACITY = 1.0f;
    private static final float TRANSFER_SIZE = 1.0f;

    private float storedEther;
    private boolean evaporating;
    private boolean crossEvaporating;

    /**
     * Associates a placed channel with the exact shared block-entity type.
     *
     * @param pos block position supplied by the world
     * @param state placed channel state
     */
    public EtherealChannelFoundationBlockEntity(BlockPos pos, BlockState state) {
        super(SharedBlockEntities.ETHEREAL_CHANNEL.get(), pos, state);
    }

    /**
     * Reports whether redstone currently gates this channel's outgoing transfer.
     *
     * @return whether the channel is strongly powered
     */
    @Override
    public boolean isActivated() {
        return getCachedState().get(EtherealChannelFoundationBlock.ACTIVATED);
    }

    /**
     * Returns the channel's one-unit Ether capacity.
     *
     * @return one Ether unit
     */
    @Override
    public float getMaxEther() {
        return CAPACITY;
    }

    /**
     * Returns the Ether currently carried by this channel.
     *
     * @return stored Ether units
     */
    @Override
    public float getStoredEther() {
        return storedEther;
    }

    /**
     * Returns the channel's one-unit transfer limit.
     *
     * @return one Ether unit per transfer
     */
    @Override
    public float getTransferSize() {
        return TRANSFER_SIZE;
    }

    /**
     * Replaces the carried Ether as directed by the shared bounded transfer arithmetic.
     *
     * @param value resulting carried Ether units
     */
    @Override
    public void setStoredEther(float value) {
        float normalizedValue = normalizeEther(value);
        if (Float.compare(storedEther, normalizedValue) == 0) {
            return;
        }

        storedEther = normalizedValue;
        publishMutation();
    }

    /**
     * Accepts Ether from every face that is not currently marked as this channel's output.
     *
     * @param side queried channel face
     * @return whether that face is not the output face
     */
    @Override
    public boolean isInputSide(Direction side) {
        BlockState state = getCachedState();
        return !state.get(EtherealChannelFoundationBlock.outputProperty(side)).isOutput();
    }

    /**
     * Resolves the first canonical side property currently marked as output.
     *
     * @return the selected output direction, or no direction for an incomplete state
     */
    @Nullable
    @Override
    public Direction getOutputSide() {
        return outputDirection(getCachedState());
    }

    /**
     * Returns the world position used to find the next storage in the directed network.
     *
     * @return this block entity's position
     */
    @Override
    public BlockPos getStoragePos() {
        return pos;
    }

    /**
     * Runs directed transfer on every fifth server tick.
     *
     * @param world logical server world carrying the network
     */
    @Override
    public void transferTick(ServerWorld world) {
        if (world.getTime() % 5 != 0) {
            return;
        }

        transfer(world);
    }

    /**
     * Returns whether this channel is losing Ether into an absent or unusable destination.
     *
     * @return whether ordinary evaporation is active
     */
    public boolean isEvaporating() {
        return evaporating;
    }

    /**
     * Records whether ordinary evaporation should be rendered by loader client code.
     *
     * @param value whether ordinary evaporation is active
     */
    @Override
    public void setEvaporating(boolean value) {
        if (evaporating == value) {
            return;
        }

        evaporating = value;
        publishMutation();
    }

    /**
     * Returns whether the current loss was caused by a cross-evaporation destination.
     *
     * @return whether cross evaporation is active
     */
    public boolean isCrossEvaporating() {
        return crossEvaporating;
    }

    /**
     * Records whether cross-evaporation particles should be rendered by loader client code.
     *
     * @param value whether cross evaporation is active
     */
    @Override
    public void setCrossEvaporating(boolean value) {
        if (crossEvaporating == value) {
            return;
        }

        crossEvaporating = value;
        publishMutation();
    }

    /**
     * Exposes carried Ether to the revelation display contract.
     *
     * @return stored Ether units
     */
    @Override
    public float getDisplayEther() {
        return getStoredEther();
    }

    /**
     * Exposes the one-unit channel capacity to the revelation display contract.
     *
     * @return one Ether unit
     */
    @Override
    public float getDisplayMaxEther() {
        return getMaxEther();
    }

    /**
     * Creates the vanilla block-entity update packet consumed by tracking clients.
     *
     * @return an update packet containing the three persistent channel values
     */
    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    /**
     * Includes the complete persistent channel state when a client begins tracking its chunk.
     *
     * @return serialized channel state for initial chunk synchronization
     */
    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putFloat(STORED_ETHER_KEY, storedEther);
        nbt.putBoolean(EVAPORATING_KEY, evaporating);
        nbt.putBoolean(CROSS_EVAPORATING_KEY, crossEvaporating);
    }

    /**
     * Restores carried Ether and both canonical evaporation flags from persistent state.
     *
     * @param nbt serialized channel state
     */
    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        storedEther = normalizeEther(nbt.getFloat(STORED_ETHER_KEY));
        evaporating = nbt.getBoolean(EVAPORATING_KEY);
        crossEvaporating = nbt.getBoolean(CROSS_EVAPORATING_KEY);
    }

    static void serverTick(
            World world,
            BlockPos pos,
            BlockState state,
            EtherealChannelFoundationBlockEntity channel
    ) {
        channel.transferTick((ServerWorld) world);
    }

    @Nullable
    static Direction outputDirection(BlockState state) {
        for (int index = 0;
                index < EtherealChannelFoundationShape.SIDE_PROPERTIES.size();
                index++) {
            EnumProperty<PipeSide> property =
                    EtherealChannelFoundationShape.SIDE_PROPERTIES.get(index);
            if (state.get(property).isOutput()) {
                return Direction.byName(property.getName());
            }
        }
        return null;
    }

    static float normalizeEther(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }

        return MathHelper.clamp(value, 0.0f, CAPACITY);
    }

    private void publishMutation() {
        markDirty();
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.getChunkManager().markForUpdate(pos);
        }
    }
}
