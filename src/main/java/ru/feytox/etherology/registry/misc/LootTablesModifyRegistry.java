package ru.feytox.etherology.registry.misc;

import dev.architectury.event.events.common.LootEvent;
import lombok.experimental.UtilityClass;
import net.minecraft.item.Item;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetCountLootFunction;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.util.Identifier;
import ru.feytox.etherology.registry.item.EItems;

import static net.minecraft.loot.LootTables.*;

@UtilityClass
public class LootTablesModifyRegistry {

    public static void registerAll() {
        LootEvent.MODIFY_LOOT_TABLE.register(
                (lootManager, id, context, builtin) -> registerModifications(id, context));
    }

    private static void registerModifications(
            Identifier id,
            LootEvent.LootTableModificationContext context) {
        boolean isBastionChest = id.equals(BASTION_OTHER_CHEST)
                || id.equals(BASTION_BRIDGE_CHEST)
                || id.equals(BASTION_TREASURE_CHEST)
                || id.equals(BASTION_HOGLIN_STABLE_CHEST);
        if (injectTabletPattern(isBastionChest, EItems.ROYAL_PATTERN_TABLET, context)) return;
        if (injectTabletPattern(id.equals(WOODLAND_MANSION_CHEST), EItems.ARISTOCRAT_PATTERN_TABLET, context)) return;
        if (injectTabletPattern(id.equals(DESERT_PYRAMID_CHEST), EItems.RITUAL_PATTERN_TABLET, context)) return;
        if (injectTabletPattern(id.equals(END_CITY_TREASURE_CHEST), EItems.OCULAR_PATTERN_TABLET, context)) return;
        if (injectTabletPattern(id.equals(SHIPWRECK_TREASURE_CHEST), EItems.HEAVENLY_PATTERN_TABLET, context)) return;
        injectTabletPattern(id.equals(JUNGLE_TEMPLE_CHEST), EItems.ASTRONOMY_PATTERN_TABLET, context);
    }

    private static boolean injectTabletPattern(
            boolean idTest,
            Item patternTablet,
            LootEvent.LootTableModificationContext context) {
        if (!idTest) return false;

        LootPool.Builder pool = LootPool.builder()
                .rolls(UniformLootNumberProvider.create(0, 1))
                .with(ItemEntry.builder(patternTablet).weight(1)
                .apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(1.0f, 3.0f))));
        context.addPool(pool);
        return true;
    }
}
