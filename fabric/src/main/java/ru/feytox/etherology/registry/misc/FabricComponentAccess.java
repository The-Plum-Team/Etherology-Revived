package ru.feytox.etherology.registry.misc;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import ru.feytox.etherology.component.PersistentComponentState;

import java.util.Optional;

final class FabricComponentAccess<C extends PersistentComponentState, O, A extends FabricComponentAdapter<C>>
        implements ComponentAccess<C, O> {

    private final ComponentKey<A> componentKey;

    FabricComponentAccess(ComponentKey<A> componentKey) {
        this.componentKey = componentKey;
    }

    @Override
    public Optional<C> maybeGet(O owner) {
        Optional<A> adapter = componentKey.maybeGet(owner);
        if (adapter.isEmpty()) return Optional.empty();
        return Optional.of(adapter.get().getState());
    }

    @Override
    public C getNullable(O owner) {
        A adapter = componentKey.getNullable(owner);
        return adapter == null ? null : adapter.getState();
    }

    @Override
    public void sync(O owner) {
        componentKey.sync(owner);
    }
}
