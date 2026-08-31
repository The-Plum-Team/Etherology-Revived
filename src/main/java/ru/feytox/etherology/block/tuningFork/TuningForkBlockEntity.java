package ru.feytox.etherology.block.tuningFork;

import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.VibrationParticleEffect;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.*;
import net.minecraft.world.event.BlockPositionSource;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.event.listener.GameEventListener;
import ru.feytox.etherology.mixin.VibrationListenerAccessor;
import ru.feytox.etherology.registry.misc.EtherSounds;
import ru.feytox.etherology.registry.misc.EventsRegistry;
import ru.feytox.etherology.util.misc.TickableBlockEntity;

import static ru.feytox.etherology.registry.block.EBlocks.TUNING_FORK_BLOCK_ENTITY;

public class TuningForkBlockEntity extends TickableBlockEntity implements GameEventListener {

    private static final int RESONANCE_COOLDOWN = 30;
    private static final ChunkTicketType<BlockPos> RESONANCE_TICKET = ChunkTicketType.create("etherology_resonance", Vec3i::compareTo, 100);

    @Getter
    private final BlockPositionSource positionSource;
    private int resonatingTicks = 0;
    private int delay = -1;
    private int receivedNote = -1;

    public TuningForkBlockEntity(BlockPos pos, BlockState state) {
        super(TUNING_FORK_BLOCK_ENTITY, pos, state);
        positionSource = new BlockPositionSource(pos);
    }

    @Override
    public void serverTick(ServerWorld world, BlockPos blockPos, BlockState state) {
        tickDelay(world, state);
        boolean shouldSync = tickReloading(world, blockPos, state);
        if (shouldSync) syncData(world);
    }

    public void tryActivate(ServerWorld world, BlockState state, boolean isResonance, int sourceNote) {
        if (isResonating()) return;

        int note = state.get(TuningFork.NOTE);
        if (sourceNote != -1 && note != sourceNote) return;

        if (!state.get(TuningFork.WATERLOGGED)) {
            float pitch = (float) Math.pow(2.0, (note - 12) / 12.0);
            SoundEvent sound = isResonance ? EtherSounds.TUNING_FORK_RESONANCE : EtherSounds.TUNING_FORK_ACTIVATE;
            world.playSound(null, pos, sound, SoundCategory.BLOCKS, 0.5f, pitch);
        }

        resonatingTicks = RESONANCE_COOLDOWN;
        world.setBlockState(pos, state.with(TuningFork.RESONATING, true));
        updateNeighbors(world, state);
        syncData(world);
        world.emitGameEvent(EventsRegistry.RESONANCE, pos, GameEvent.Emitter.of(state));
    }

    private boolean canAccept(BlockState state, int receivedNote) {
        return delay == -1 && !isResonating() && state.get(TuningFork.NOTE) == receivedNote;
    }

    private void updateNeighbors(ServerWorld world, BlockState state) {
        Direction bottomFacing = state.get(TuningFork.VERTICAL_FACING).getOpposite();
        BlockPos bottomPos = pos.add(bottomFacing.getVector());
        world.updateNeighbors(bottomPos, world.getBlockState(bottomPos).getBlock());
    }

    private boolean tickReloading(ServerWorld world, BlockPos pos, BlockState state) {
        if (!isResonating()) return false;
        resonatingTicks--;
        if (isResonating()) return true;

        world.setBlockState(pos, state.with(TuningFork.RESONATING, false));
        updateNeighbors(world, state);
        return true;
    }

    private void tickDelay(ServerWorld world, BlockState state) {
        if (delay == -1) return;
        if (delay-- > 0) {
            markDirty();
            return;
        }

        tryActivate(world, state, true, receivedNote);
        delay = -1;
        receivedNote = -1;
        markDirty();
    }

    public boolean isResonating() {
        return resonatingTicks > 0;
    }

    @Override
    public boolean listen(ServerWorld world, GameEvent event, GameEvent.Emitter emitter, Vec3d emitterPos) {
        if (!event.equals(EventsRegistry.RESONANCE)) return false;
        BlockState sourceState = emitter.affectedState();
        if (sourceState == null) return false;

        Vec3d forkPos = positionSource.getPos(world).orElse(null);
        if (forkPos == null) return false;
        if (VibrationListenerAccessor.callIsOccluded(world, emitterPos, forkPos)) return false;

        int note = sourceState.get(TuningFork.NOTE);
        if (!canAccept(getCachedState(), note)) return false;

        receivedNote = note;
        delay = MathHelper.floor(emitterPos.distanceTo(forkPos));
        markDirty();
        world.spawnParticles(new VibrationParticleEffect(positionSource, delay), emitterPos.x, emitterPos.y, emitterPos.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.getChunkManager().addTicket(RESONANCE_TICKET, world.getChunk(pos).getPos(), 2, pos);

        return true;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        nbt.putInt("reloading_ticks", resonatingTicks);
        nbt.putInt("delay", delay);
        nbt.putInt("received_note", receivedNote);

        super.writeNbt(nbt);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);

        resonatingTicks = nbt.getInt("reloading_ticks");
        delay = nbt.contains("delay", NbtElement.INT_TYPE) ? nbt.getInt("delay") : -1;
        receivedNote = nbt.contains("received_note", NbtElement.INT_TYPE) ? nbt.getInt("received_note") : -1;
    }

    @Override
    public int getRange() {
        return 16;
    }

    @Override
    public TriggerOrder getTriggerOrder() {
        return TriggerOrder.BY_DISTANCE;
    }
}
