package ru.feytox.etherology.registry.misc;

import dev.onyxstudios.cca.api.v3.component.ComponentV3;
import net.minecraft.nbt.NbtCompound;
import ru.feytox.etherology.component.PersistentComponentState;

abstract class FabricComponentAdapter<C extends PersistentComponentState> implements ComponentV3 {

    private final C state;

    FabricComponentAdapter(C state) {
        this.state = state;
    }

    final C getState() {
        return state;
    }

    @Override
    public final void readFromNbt(NbtCompound tag) {
        state.readFromNbt(tag);
    }

    @Override
    public final void writeToNbt(NbtCompound tag) {
        state.writeToNbt(tag);
    }
}
