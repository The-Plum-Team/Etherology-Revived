package ru.feytox.etherology.registry.misc;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import ru.feytox.etherology.magic.aspects.AspectRegistryPart;

/**
 * Owns loader-neutral keys for Etherology's synchronized datapack registries.
 */
public final class SharedAspectRegistries {

    /**
     * Identifies the {@code data/<pack>/etherology/aspects} folder on both loaders.
     */
    public static final RegistryKey<Registry<AspectRegistryPart>> ASPECTS =
            RegistryKey.ofRegistry(new Identifier("etherology", "aspects"));

    private SharedAspectRegistries() {
    }
}
