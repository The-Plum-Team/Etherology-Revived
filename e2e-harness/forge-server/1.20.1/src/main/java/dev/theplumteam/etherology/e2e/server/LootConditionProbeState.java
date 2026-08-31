package dev.theplumteam.etherology.e2e.server;

import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootDataKey;
import net.minecraft.loot.LootDataType;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

record LootConditionProbeState(
        Object conditionTypeIdentity,
        Object probeTableIdentity,
        String conditionId,
        List<String> etherologyConditionIds,
        String serializerClass,
        String probeTableId,
        List<String> emptyToolItems,
        List<String> fortuneOneItems
) {

    static final String LOOT_CONDITION_REGISTRY_ID = "minecraft:loot_condition_type";
    static final Identifier CONDITION_ID = Identifier.of(
            "etherology",
            "random_chance_with_fortune"
    );
    static final String EXPECTED_SERIALIZER_CLASS =
            "ru.feytox.etherology.util.misc.RandomChanceWithFortuneConditionSerializer";
    static final Identifier PROBE_TABLE_ID = Identifier.of(
            RegistryFoundationServerProbe.MOD_ID,
            "registry_foundation"
    );
    static final List<String> EXPECTED_EMPTY_TOOL_ITEMS = List.of(
            "minecraft:gold_ingot",
            "minecraft:stone"
    );
    static final List<String> EXPECTED_FORTUNE_ONE_ITEMS = List.of(
            "minecraft:diamond",
            "minecraft:gold_ingot",
            "minecraft:stone"
    );

    private static final long LOOT_RANDOM_SEED = 0x45544845524F4C4FL;

    static LootConditionProbeState capture(MinecraftServer server) {
        if (server == null) {
            return missing();
        }

        LootConditionType conditionType = Registries.LOOT_CONDITION_TYPE
                .getOrEmpty(CONDITION_ID)
                .orElse(null);
        String conditionId = conditionType == null
                ? ""
                : identifierString(Registries.LOOT_CONDITION_TYPE.getId(conditionType));
        String serializerClass = conditionType == null
                ? ""
                : conditionType.getJsonSerializer().getClass().getName();
        List<String> etherologyConditionIds = Registries.LOOT_CONDITION_TYPE.getIds().stream()
                .filter(identifier -> "etherology".equals(identifier.getNamespace()))
                .map(Identifier::toString)
                .sorted()
                .toList();

        LootTable probeTable = server.getLootManager().getElement(
                new LootDataKey<>(LootDataType.LOOT_TABLES, PROBE_TABLE_ID)
        );
        String probeTableId = probeTable == null
                ? ""
                : server.getLootManager().getIds(LootDataType.LOOT_TABLES).contains(PROBE_TABLE_ID)
                        ? PROBE_TABLE_ID.toString()
                        : "";
        List<String> emptyToolItems = generateLootItemIds(
                server,
                probeTable,
                ItemStack.EMPTY
        );
        ItemStack fortuneOneTool = new ItemStack(Items.IRON_PICKAXE);
        fortuneOneTool.addEnchantment(Enchantments.FORTUNE, 1);
        List<String> fortuneOneItems = generateLootItemIds(
                server,
                probeTable,
                fortuneOneTool
        );

        return new LootConditionProbeState(
                conditionType,
                probeTable,
                conditionId,
                etherologyConditionIds,
                serializerClass,
                probeTableId,
                emptyToolItems,
                fortuneOneItems
        );
    }

    static LootConditionProbeState missing() {
        return new LootConditionProbeState(
                null,
                null,
                "",
                List.of(),
                "",
                "",
                List.of(),
                List.of()
        );
    }

    boolean sameStateAtServerStarted(LootConditionProbeState startedState) {
        return hasSameRegistryAndBehavior(startedState)
                && probeTableIdentity == startedState.probeTableIdentity;
    }

    boolean hasSameRegistryAndBehavior(LootConditionProbeState reloadedState) {
        return conditionTypeIdentity != null
                && conditionTypeIdentity == reloadedState.conditionTypeIdentity
                && probeTableIdentity != null
                && reloadedState.probeTableIdentity != null
                && conditionId.equals(reloadedState.conditionId)
                && etherologyConditionIds.equals(reloadedState.etherologyConditionIds)
                && serializerClass.equals(reloadedState.serializerClass)
                && probeTableId.equals(reloadedState.probeTableId)
                && emptyToolItems.equals(reloadedState.emptyToolItems)
                && fortuneOneItems.equals(reloadedState.fortuneOneItems);
    }

    boolean hasReplacedProbeTableInstanceAfterReload(
            LootConditionProbeState reloadedState
    ) {
        return probeTableIdentity != null
                && reloadedState.probeTableIdentity != null
                && probeTableIdentity != reloadedState.probeTableIdentity;
    }

    private static List<String> generateLootItemIds(
            MinecraftServer server,
            LootTable lootTable,
            ItemStack tool
    ) {
        if (lootTable == null) {
            return List.of();
        }

        LootContextParameterSet parameters = new LootContextParameterSet.Builder(
                server.getOverworld()
        )
                .add(LootContextParameters.BLOCK_STATE, Blocks.STONE.getDefaultState())
                .add(LootContextParameters.ORIGIN, Vec3d.ZERO)
                .add(LootContextParameters.TOOL, tool)
                .build(LootContextTypes.BLOCK);
        List<String> itemIds = new ArrayList<>();
        lootTable.generateLoot(parameters, LOOT_RANDOM_SEED).forEach(stack -> {
            String itemId = identifierString(Registries.ITEM.getId(stack.getItem()));
            for (int count = 0; count < stack.getCount(); count++) {
                itemIds.add(itemId);
            }
        });
        itemIds.sort(String::compareTo);
        return List.copyOf(itemIds);
    }

    private static String identifierString(Identifier identifier) {
        return identifier == null ? "<unregistered>" : identifier.toString();
    }
}
