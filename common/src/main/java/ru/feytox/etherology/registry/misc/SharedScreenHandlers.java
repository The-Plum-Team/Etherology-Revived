package ru.feytox.etherology.registry.misc;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import ru.feytox.etherology.block.etherealStorage.EtherealStorageFoundationScreenHandler;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns loader-neutral menu registrations accepted by every active loader.
 */
public final class SharedScreenHandlers {

    private static final SharedDeferredRegister<ScreenHandlerType<?>> SCREEN_HANDLERS =
            SharedDeferredRegister.create(RegistryKeys.SCREEN_HANDLER);

    /**
     * Supplies the ethereal-storage menu after the active loader registers menu types.
     */
    public static final RegistrySupplier<ScreenHandlerType<EtherealStorageFoundationScreenHandler>>
            ETHEREAL_STORAGE = SCREEN_HANDLERS.register(
                    "ethereal_storage_screen_handler",
                    () -> new ScreenHandlerType<>(
                            EtherealStorageFoundationScreenHandler::new,
                            FeatureFlags.VANILLA_FEATURES
                    )
            );

    private SharedScreenHandlers() {
    }

    /**
     * Attaches the shared menu registry once before loader registry events run.
     */
    public static void register() {
        SCREEN_HANDLERS.attach();
    }
}
