package ru.feytox.etherology.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import ru.feytox.etherology.bootstrap.EtherologyBootstrap;

import java.util.function.Supplier;

/**
 * Owns one Architectury deferred registry and its failure-preserving attachment lifecycle.
 *
 * @param <T> base value type stored by the Minecraft registry
 */
public final class SharedDeferredRegister<T> {

    private final DeferredRegister<T> deferredRegister;
    private boolean attached;
    private Throwable attachmentFailure;

    private SharedDeferredRegister(RegistryKey<Registry<T>> registryKey) {
        deferredRegister = DeferredRegister.create(EtherologyBootstrap.MOD_ID, registryKey);
    }

    /**
     * Creates an unattached registry owned by Etherology's shared bootstrap.
     *
     * @param registryKey Minecraft registry populated by this instance
     * @param <T> base value type stored by the Minecraft registry
     * @return a deferred registry ready to accept entries before attachment
     */
    public static <T> SharedDeferredRegister<T> create(
            RegistryKey<Registry<T>> registryKey
    ) {
        return new SharedDeferredRegister<>(registryKey);
    }

    /**
     * Declares one value without resolving it before the loader's registry event.
     *
     * @param id path below Etherology's namespace
     * @param supplier value factory called by the active loader
     * @param <R> concrete registered value type
     * @return a supplier that resolves only after the registry event
     */
    public <R extends T> RegistrySupplier<R> register(
            String id,
            Supplier<? extends R> supplier
    ) {
        return deferredRegister.register(id, supplier);
    }

    /**
     * Attaches the registry exactly once and preserves the first attachment failure.
     */
    public synchronized void attach() {
        if (attached) {
            return;
        }
        if (attachmentFailure != null) {
            throw new IllegalStateException(
                    "Etherology deferred registry attachment previously failed",
                    attachmentFailure
            );
        }

        try {
            deferredRegister.register();
            attached = true;
        } catch (RuntimeException | Error throwable) {
            attachmentFailure = throwable;
            throw throwable;
        }
    }
}
