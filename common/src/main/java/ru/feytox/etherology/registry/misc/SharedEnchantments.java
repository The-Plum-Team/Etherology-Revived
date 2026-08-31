package ru.feytox.etherology.registry.misc;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns loader-neutral enchantment registrations accepted by every active loader.
 */
public final class SharedEnchantments {

    private static final SharedDeferredRegister<Enchantment> ENCHANTMENTS =
            SharedDeferredRegister.create(RegistryKeys.ENCHANTMENT);

    /**
     * Supplies the Peal enchantment after the active loader registers enchantments.
     */
    public static final RegistrySupplier<Enchantment> PEAL =
            ENCHANTMENTS.register("peal", () -> new PealEnchantment());

    /**
     * Supplies the Reflection enchantment after the active loader registers enchantments.
     */
    public static final RegistrySupplier<Enchantment> REFLECTION =
            ENCHANTMENTS.register("reflection", () -> new ReflectionEnchantment());

    private SharedEnchantments() {
    }

    /**
     * Attaches the shared enchantment registry once before loader registry events run.
     */
    public static void register() {
        ENCHANTMENTS.attach();
    }
}
