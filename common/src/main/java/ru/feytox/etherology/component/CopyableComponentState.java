package ru.feytox.etherology.component;

/**
 * Defines state transfer used when a loader recreates a component owner.
 */
public interface CopyableComponentState<C> {

    /**
     * Replaces the current values with the source component's values.
     */
    void copyFrom(C other);
}
