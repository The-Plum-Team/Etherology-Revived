package ru.feytox.etherology.registry.item;

import dev.architectury.registry.fuel.FuelRegistry;
import net.minecraft.item.BoatItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import ru.feytox.etherology.item.*;
import ru.feytox.etherology.item.glints.GlintItem;
import ru.feytox.etherology.util.misc.BoatTypes;
import ru.feytox.etherology.util.misc.EIdentifier;


public class EItems {

    public static final Item TELDECORE = registerItem("teldecore", new Teldecore());

    public static final Item PRIMOSHARD_KETA = SharedPrimoShardItems.PRIMOSHARD_KETA.get();
    public static final Item PRIMOSHARD_RELLA = SharedPrimoShardItems.PRIMOSHARD_RELLA.get();
    public static final Item PRIMOSHARD_CLOS = SharedPrimoShardItems.PRIMOSHARD_CLOS.get();
    public static final Item PRIMOSHARD_VIA = SharedPrimoShardItems.PRIMOSHARD_VIA.get();
    public static final GlintItem GLINT = (GlintItem) registerItem(
            "glint_shard",
            new GlintItem(EtherealStorageInputItem.MAX_ETHER)
    );
    public static final Item ETHER = registerSimple("ether");
    public static final Item CORRUPTION_BUCKET = registerItem("corruption_bucket", new CorruptionBucket());

    public static final Item ARISTOCRAT_PATTERN_TABLET = SharedPatternTabletItems.ARISTOCRAT_PATTERN_TABLET.get();
    public static final Item ASTRONOMY_PATTERN_TABLET = SharedPatternTabletItems.ASTRONOMY_PATTERN_TABLET.get();
    public static final Item HEAVENLY_PATTERN_TABLET = SharedPatternTabletItems.HEAVENLY_PATTERN_TABLET.get();
    public static final Item OCULAR_PATTERN_TABLET = SharedPatternTabletItems.OCULAR_PATTERN_TABLET.get();
    public static final Item RITUAL_PATTERN_TABLET = SharedPatternTabletItems.RITUAL_PATTERN_TABLET.get();
    public static final Item ROYAL_PATTERN_TABLET = SharedPatternTabletItems.ROYAL_PATTERN_TABLET.get();
    public static final Item TRADITIONAL_PATTERN_TABLET = SharedPatternTabletItems.TRADITIONAL_PATTERN_TABLET.get();
    public static final Item[] PATTERN_TABLETS = {ARISTOCRAT_PATTERN_TABLET, ASTRONOMY_PATTERN_TABLET, HEAVENLY_PATTERN_TABLET, OCULAR_PATTERN_TABLET, RITUAL_PATTERN_TABLET, ROYAL_PATTERN_TABLET, TRADITIONAL_PATTERN_TABLET};

    public static final Item UNADJUSTED_LENS = SharedLensItems.UNADJUSTED_LENS.get();
    public static final Item REDSTONE_LENS = registerItem("redstone_lens", new RedstoneLens());
    public static final Item[] LENSES = {REDSTONE_LENS};

    public static final Item FOREST_LANTERN_CRUMB =
            SharedFoodItems.FOREST_LANTERN_CRUMB.get();

    public static final Item PEACH_BOAT = registerItem("peach_boat", new BoatItem(false, BoatTypes.PEACH, new Item.Settings().maxCount(1)));
    public static final Item PEACH_CHEST_BOAT = registerItem("peach_chest_boat", new BoatItem(true, BoatTypes.PEACH, new Item.Settings().maxCount(1)));

    private static Item registerItem(String itemId, Item item) {
        return Registry.register(Registries.ITEM, EIdentifier.of(itemId), item);
    }

    private static Item registerSimple(String itemId) {
        return registerItem(itemId, new Item(new Item.Settings()));
    }

    public static void registerItems() {
        DecoBlockItems.registerAll();
        ToolItems.registerAll();
        ArmorItems.registerAll();
        registerFuel();
    }

    private static void registerFuel() {
        FuelRegistry.register(200, SharedMaterialItems.THUJA_OIL.get(), ToolItems.WOODEN_BATTLE_PICKAXE);
    }
}
