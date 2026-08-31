package ru.feytox.etherology.registry.misc;

import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * Provides loader-neutral typed access to one registered Etherology component.
 */
public final class ComponentHandle<C, O> {

    private final Identifier id;
    private ComponentAccess<C, O> access;

    ComponentHandle(Identifier id) {
        this.id = id;
    }

    /**
     * Returns the logical component id shared by every loader backend.
     */
    public Identifier getId() {
        return id;
    }

    /**
     * Resolves state only when the owner supports this component.
     */
    public Optional<C> maybeGet(O owner) {
        return access.maybeGet(owner);
    }

    /**
     * Resolves state or returns {@code null} when the owner does not provide it.
     */
    public C getNullable(O owner) {
        return access.getNullable(owner);
    }

    /**
     * Sends the owner's current state through the active loader backend.
     */
    public void sync(O owner) {
        access.sync(owner);
    }

    void bind(ComponentAccess<C, O> access) {
        this.access = access;
    }
}
