package ru.feytox.etherology.component;

/**
 * Defines component work that a loader backend invokes once per server tick.
 */
public interface ServerTickingComponentState {

    /**
     * Advances this state for its owning server-side object.
     */
    void serverTick();
}
