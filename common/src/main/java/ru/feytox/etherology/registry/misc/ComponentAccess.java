package ru.feytox.etherology.registry.misc;

import java.util.Optional;

interface ComponentAccess<C, O> {

    /**
     * Resolves state only when the owner supports this component.
     */
    Optional<C> maybeGet(O owner);

    /**
     * Resolves state or returns {@code null} when the owner does not provide it.
     */
    C getNullable(O owner);

    /**
     * Sends the owner's current state through the loader's synchronization backend.
     */
    void sync(O owner);
}
