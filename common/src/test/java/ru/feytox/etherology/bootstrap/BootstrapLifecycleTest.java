package ru.feytox.etherology.bootstrap;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BootstrapLifecycleTest {

    @Test
    void schedulesAndCompletesTheLoaderHandshakeOnce() {
        BootstrapLifecycle lifecycle = new BootstrapLifecycle();
        AtomicInteger schedules = new AtomicInteger();
        AtomicReference<Runnable> loaderSetup = new AtomicReference<>();
        PlatformRegistrar registrar = callback -> {
            schedules.incrementAndGet();
            loaderSetup.set(callback);
        };

        lifecycle.initialize(registrar);
        lifecycle.initialize(registrar);

        assertEquals(1, schedules.get());
        assertFalse(lifecycle.isLoaderHandshakeComplete());

        loaderSetup.get().run();
        loaderSetup.get().run();

        assertTrue(lifecycle.isLoaderHandshakeComplete());
    }

    @Test
    void acceptsAPlatformThatRunsSetupSynchronously() {
        BootstrapLifecycle lifecycle = new BootstrapLifecycle();

        lifecycle.initialize(Runnable::run);

        assertTrue(lifecycle.isLoaderHandshakeComplete());
    }

    @Test
    void preservesTheFirstSchedulingFailure() {
        BootstrapLifecycle lifecycle = new BootstrapLifecycle();
        IllegalStateException schedulingFailure = new IllegalStateException("scheduling failed");

        IllegalStateException firstFailure = assertThrows(
                IllegalStateException.class,
                () -> lifecycle.initialize(callback -> {
                    throw schedulingFailure;
                })
        );
        IllegalStateException repeatedFailure = assertThrows(
                IllegalStateException.class,
                () -> lifecycle.initialize(callback -> {
                    throw new AssertionError("Failed loader handshake was scheduled again");
                })
        );

        assertSame(schedulingFailure, firstFailure);
        assertSame(schedulingFailure, repeatedFailure.getCause());
    }
}
