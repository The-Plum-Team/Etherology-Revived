package ru.feytox.etherology.registry.item;

import lombok.experimental.UtilityClass;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import ru.feytox.etherology.item.*;
import ru.feytox.etherology.util.misc.EIdentifier;

import static net.minecraft.item.ToolMaterials.*;
import static ru.feytox.etherology.registry.misc.EtherToolMaterials.EBONY;

@UtilityClass
public class ToolItems {
    // ebony tools
    public static final Item EBONY_AXE = SharedToolItems.EBONY_AXE.get();
    public static final Item EBONY_PICKAXE = SharedToolItems.EBONY_PICKAXE.get();
    public static final Item EBONY_HOE = SharedToolItems.EBONY_HOE.get();
    public static final Item EBONY_SHOVEL = SharedToolItems.EBONY_SHOVEL.get();
    public static final Item EBONY_SWORD = SharedToolItems.EBONY_SWORD.get();

    // battle pickaxes
    public static final Item WOODEN_BATTLE_PICKAXE = register("wooden_battle_pickaxe", new BattlePickaxe(WOOD, 2, -2.6f));
    public static final Item STONE_BATTLE_PICKAXE = register("stone_battle_pickaxe", new BattlePickaxe(STONE, 2, -2.6f));
    public static final Item IRON_BATTLE_PICKAXE = register("iron_battle_pickaxe", new BattlePickaxe(IRON, 2, -2.6f));
    public static final Item GOLDEN_BATTLE_PICKAXE = register("golden_battle_pickaxe", new BattlePickaxe(GOLD, 2, -2.6f));
    public static final Item EBONY_BATTLE_PICKAXE = register("ebony_battle_pickaxe", new BattlePickaxe(EBONY, 1, -2.6f));
    public static final Item DIAMOND_BATTLE_PICKAXE = register("diamond_battle_pickaxe", new BattlePickaxe(DIAMOND, 2, -2.6f));
    public static final Item NETHERITE_BATTLE_PICKAXE = register("netherite_battle_pickaxe", new BattlePickaxe(NETHERITE, 2, -2.6f, new Item.Settings().fireproof()));
    public static final Item[] BATTLE_PICKAXES = {WOODEN_BATTLE_PICKAXE, STONE_BATTLE_PICKAXE, IRON_BATTLE_PICKAXE, GOLDEN_BATTLE_PICKAXE, EBONY_BATTLE_PICKAXE, DIAMOND_BATTLE_PICKAXE, NETHERITE_BATTLE_PICKAXE};

    // combat tools
    public static final Item IRON_SHIELD = register("iron_shield", new IronShield(new Item.Settings().maxDamage(452), 140, 14, Items.IRON_INGOT));
    public static final Item TUNING_MACE = register("tuning_mace", new TuningMaceItem());
    public static final Item BROADSWORD = register("broadsword", new BroadSwordItem());

    // single tools
    public static final Item OCULUS = register("oculus", new OculusItem());
    public static final Item OCULAR = register("ocular", new OcularItem());
    public static final Item STAFF = register("staff", new StaffItem());
    public static final Item STREAM_KEY = register("stream_key", new StreamKeyItem());
    public static final Item WARP_COUNTER = SharedToolItems.WARP_COUNTER.get();

    public static Item register(String id, Item item) {
        return Registry.register(Registries.ITEM, EIdentifier.of(id), item);
    }

    public static void registerAll() {}
}
