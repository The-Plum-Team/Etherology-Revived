package ru.feytox.etherology.magic.ether;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

class MutableEtherStorage implements EtherStorage {

    private final float maxEther;
    private float storedEther;

    MutableEtherStorage(float maxEther, float storedEther) {
        this.maxEther = maxEther;
        this.storedEther = storedEther;
    }

    @Override
    public float getMaxEther() {
        return maxEther;
    }

    @Override
    public float getStoredEther() {
        return storedEther;
    }

    @Override
    public float getTransferSize() {
        return 1.0f;
    }

    @Override
    public void setStoredEther(float value) {
        storedEther = value;
    }

    @Override
    public boolean isInputSide(Direction side) {
        return true;
    }

    @Nullable
    @Override
    public Direction getOutputSide() {
        return null;
    }

    @Override
    public BlockPos getStoragePos() {
        return BlockPos.ORIGIN;
    }

    @Override
    public void transferTick(ServerWorld world) {
    }

    @Override
    public boolean isActivated() {
        return false;
    }
}
