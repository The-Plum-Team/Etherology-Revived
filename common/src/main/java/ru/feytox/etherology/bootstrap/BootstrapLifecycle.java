package ru.feytox.etherology.bootstrap;

final class BootstrapLifecycle {

    private BootstrapState state = BootstrapState.NOT_STARTED;
    private Throwable failure;

    synchronized void initialize(PlatformRegistrar registrar) {
        if (state == BootstrapState.LOADER_HANDSHAKE_COMPLETE
                || state == BootstrapState.SCHEDULING_LOADER_HANDSHAKE
                || state == BootstrapState.WAITING_FOR_LOADER) {
            return;
        }
        if (state == BootstrapState.FAILED) {
            throw new IllegalStateException("Etherology loader handshake previously failed", failure);
        }

        state = BootstrapState.SCHEDULING_LOADER_HANDSHAKE;
        try {
            registrar.scheduleLoaderSetup(this::completeLoaderHandshake);
            if (state == BootstrapState.SCHEDULING_LOADER_HANDSHAKE) {
                state = BootstrapState.WAITING_FOR_LOADER;
            }
        } catch (RuntimeException | Error throwable) {
            fail(throwable);
            throw throwable;
        }
    }

    synchronized boolean isLoaderHandshakeComplete() {
        return state == BootstrapState.LOADER_HANDSHAKE_COMPLETE;
    }

    private synchronized void completeLoaderHandshake() {
        if (state == BootstrapState.LOADER_HANDSHAKE_COMPLETE) {
            return;
        }
        if (state != BootstrapState.SCHEDULING_LOADER_HANDSHAKE
                && state != BootstrapState.WAITING_FOR_LOADER) {
            if (state == BootstrapState.FAILED) {
                throw new IllegalStateException("Etherology loader handshake previously failed", failure);
            }
            throw new IllegalStateException("Etherology loader handshake ran outside loader setup");
        }

        state = BootstrapState.LOADER_HANDSHAKE_COMPLETE;
    }

    private void fail(Throwable throwable) {
        failure = throwable;
        state = BootstrapState.FAILED;
    }
}
