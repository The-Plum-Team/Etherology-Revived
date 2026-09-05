package ru.feytox.etherology.registry.misc;

import dev.architectury.event.events.common.LootEvent;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetCountLootFunction;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.util.Identifier;
import ru.feytox.etherology.item.PatternTabletItem;
import ru.feytox.etherology.registry.item.SharedPatternTabletItems;

import static net.minecraft.loot.LootTables.*;

public final class LootTablesModifyRegistry {

    private static boolean registered;

    private LootTablesModifyRegistry() {
    }

    /** Installs the canonical chest-loot additions once on either loader. */
    public static synchronized void registerAll() {
        if (registered) {
            return;
        }
        LootEvent.MODIFY_LOOT_TABLE.register(
                (lootManager, id, context, builtin) -> registerModifications(id, context));
        registered = true;
    }

    private static void registerModifications(
            Identifier id,
            LootEvent.LootTableModificationContext context) {
        boolean isBastionChest = id.equals(BASTION_OTHER_CHEST)
                || id.equals(BASTION_BRIDGE_CHEST)
                || id.equals(BASTION_TREASURE_CHEST)
                || id.equals(BASTION_HOGLIN_STABLE_CHEST);
        if (injectTabletPattern(isBastionChest, SharedPatternTabletItems.ROYAL_PATTERN_TABLET, context)) return;
        if (injectTabletPattern(id.equals(WOODLAND_MANSION_CHEST), SharedPatternTabletItems.ARISTOCRAT_PATTERN_TABLET, context)) return;
        if (injectTabletPattern(id.equals(DESERT_PYRAMID_CHEST), SharedPatternTabletItems.RITUAL_PATTERN_TABLET, context)) return;
        if (injectTabletPattern(id.equals(END_CITY_TREASURE_CHEST), SharedPatternTabletItems.OCULAR_PATTERN_TABLET, context)) return;
        if (injectTabletPattern(id.equals(SHIPWRECK_TREASURE_CHEST), SharedPatternTabletItems.HEAVENLY_PATTERN_TABLET, context)) return;
        injectTabletPattern(id.equals(JUNGLE_TEMPLE_CHEST), SharedPatternTabletItems.ASTRONOMY_PATTERN_TABLET, context);
    }

    private static boolean injectTabletPattern(
            boolean idTest,
            RegistrySupplier<PatternTabletItem> patternTablet,
            LootEvent.LootTableModificationContext context) {
        if (!idTest) return false;

        LootPool.Builder pool = LootPool.builder()
                .rolls(UniformLootNumberProvider.create(0, 1))
                .with(ItemEntry.builder(patternTablet.get()).weight(1)
                .apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(1.0f, 3.0f))));
        context.addPool(pool);
        return true;
    }
}
