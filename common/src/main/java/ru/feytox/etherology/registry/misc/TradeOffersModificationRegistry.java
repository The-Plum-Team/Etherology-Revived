package ru.feytox.etherology.registry.misc;

import dev.architectury.registry.level.entity.trade.TradeRegistry;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;
import ru.feytox.etherology.registry.item.SharedPatternTabletItems;

public final class TradeOffersModificationRegistry {

    private static boolean registered;

    private TradeOffersModificationRegistry() {
    }

    /** Installs the trade only once its tablet is registered on the active loader. */
    public static synchronized void registerAll() {
        if (registered) {
            return;
        }
        SharedPatternTabletItems.TRADITIONAL_PATTERN_TABLET.listen(
                tablet -> TradeRegistry.registerVillagerTrade(
                        VillagerProfession.TOOLSMITH,
                        2,
                        new TradeOffers.SellItemFactory(tablet, 12, 1, 8, 2)));
        registered = true;
    }
}
