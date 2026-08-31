package ru.feytox.etherology.registry.misc;

import dev.architectury.registry.level.entity.trade.TradeRegistry;
import lombok.experimental.UtilityClass;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;
import ru.feytox.etherology.registry.item.EItems;

@UtilityClass
public class TradeOffersModificationRegistry {

    public static void registerAll() {
        TradeRegistry.registerVillagerTrade(
                VillagerProfession.TOOLSMITH,
                2,
                new TradeOffers.SellItemFactory(
                        EItems.TRADITIONAL_PATTERN_TABLET,
                        12,
                        1,
                        8,
                        2));
    }
}
