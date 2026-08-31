package ru.feytox.etherology.registry.misc;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class ComponentHandleTest {

    @Test
    void delegatesResolutionAndSynchronizationToBoundBackend() {
        Identifier id = new Identifier("etherology", "test");
        Object owner = new Object();
        AtomicReference<Object> synchronizedOwner = new AtomicReference<>();
        ComponentHandle<String, Object> handle = new ComponentHandle<>(id);

        handle.bind(new ComponentAccess<>() {
            @Override
            public Optional<String> maybeGet(Object requestedOwner) {
                assertSame(owner, requestedOwner);
                return Optional.of("state");
            }

            @Override
            public String getNullable(Object requestedOwner) {
                assertSame(owner, requestedOwner);
                return "state";
            }

            @Override
            public void sync(Object requestedOwner) {
                synchronizedOwner.set(requestedOwner);
            }
        });

        assertEquals(id, handle.getId());
        assertEquals(Optional.of("state"), handle.maybeGet(owner));
        assertEquals("state", handle.getNullable(owner));
        handle.sync(owner);
        assertSame(owner, synchronizedOwner.get());
    }
}
