package ru.feytox.etherology.bootstrap;

/**
 * Schedules Etherology's loader handshake at the active loader's setup phase.
 */
@FunctionalInterface
public interface PlatformRegistrar {

    /**
     * Registers the callback without invoking it more than once for one loader lifecycle.
     *
     * @param loaderSetup callback to run on the loader's setup thread
     */
    void scheduleLoaderSetup(Runnable loaderSetup);
}
