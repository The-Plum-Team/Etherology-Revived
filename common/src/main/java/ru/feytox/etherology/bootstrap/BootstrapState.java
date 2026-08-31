package ru.feytox.etherology.bootstrap;

enum BootstrapState {
    NOT_STARTED,
    SCHEDULING_LOADER_HANDSHAKE,
    WAITING_FOR_LOADER,
    LOADER_HANDSHAKE_COMPLETE,
    FAILED
}
