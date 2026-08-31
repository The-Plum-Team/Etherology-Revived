package ru.feytox.etherology.forge;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class ForgePlatformRegistrarTest {

    @Test
    @SuppressWarnings("unchecked")
    void schedulesTheLoaderHandshakeOnTheCommonSetupWorkQueue() {
        AtomicReference<Consumer<FMLCommonSetupEvent>> listener = new AtomicReference<>();
        IEventBus eventBus = (IEventBus) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{IEventBus.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("addListener") && arguments.length == 1) {
                        listener.set((Consumer<FMLCommonSetupEvent>) arguments[0]);
                        return null;
                    }
                    throw new AssertionError("Unexpected event-bus call: " + method);
                }
        );
        AtomicInteger loaderSetups = new AtomicInteger();

        new ForgePlatformRegistrar(eventBus).scheduleLoaderSetup(loaderSetups::incrementAndGet);

        assertNotNull(listener.get());
        listener.get().accept(new ImmediateCommonSetupEvent());
        assertEquals(1, loaderSetups.get());
    }
}
