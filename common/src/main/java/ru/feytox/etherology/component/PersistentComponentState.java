package ru.feytox.etherology.component;

import net.minecraft.nbt.NbtCompound;

/**
 * Defines the stable NBT boundary used by each loader's component storage backend.
 */
public interface PersistentComponentState {

    /**
     * Replaces the current state with values read from persistent component data.
     */
    void readFromNbt(NbtCompound tag);

    /**
     * Writes the complete persistent state without assuming which loader owns the storage.
     */
    void writeToNbt(NbtCompound tag);
}
