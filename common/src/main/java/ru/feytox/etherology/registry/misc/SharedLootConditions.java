package ru.feytox.etherology.registry.misc;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.registry.SharedDeferredRegister;
import ru.feytox.etherology.util.misc.RandomChanceWithFortuneConditionSerializer;

/**
 * Owns loader-neutral loot-condition registrations accepted by every active loader.
 */
public final class SharedLootConditions {

    private static final SharedDeferredRegister<LootConditionType> LOOT_CONDITIONS =
            SharedDeferredRegister.create(RegistryKeys.LOOT_CONDITION_TYPE);

    /**
     * Supplies Etherology's Fortune-scaled random-chance condition after loot conditions register.
     */
    public static final RegistrySupplier<LootConditionType> RANDOM_CHANCE_WITH_FORTUNE =
            LOOT_CONDITIONS.register(
                    "random_chance_with_fortune",
                    () -> new LootConditionType(
                            new RandomChanceWithFortuneConditionSerializer()
                    )
            );

    private SharedLootConditions() {
    }

    /**
     * Attaches the shared loot-condition registry once before loader registry events run.
     */
    public static void register() {
        LOOT_CONDITIONS.attach();
    }
}
