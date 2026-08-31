package ru.feytox.etherology.util.misc;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameter;
import net.minecraft.loot.context.LootContextParameters;
import ru.feytox.etherology.registry.misc.SharedLootConditions;

import java.util.Set;

/**
 * Passes when a random sample falls below a base chance increased by the tool's Fortune level.
 */
public final class RandomChanceWithFortuneCondition implements LootCondition {

    private final float chance;
    private final float fortuneMultiplier;

    RandomChanceWithFortuneCondition(float chance, float fortuneMultiplier) {
        this.chance = chance;
        this.fortuneMultiplier = fortuneMultiplier;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LootConditionType getType() {
        return SharedLootConditions.RANDOM_CHANCE_WITH_FORTUNE.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<LootContextParameter<?>> getRequiredParameters() {
        return Set.of(LootContextParameters.TOOL);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean test(LootContext lootContext) {
        ItemStack toolStack = lootContext.get(LootContextParameters.TOOL);
        int fortuneLevel = toolStack == null
                ? 0
                : EnchantmentHelper.getLevel(Enchantments.FORTUNE, toolStack);
        return passes(lootContext.getRandom().nextFloat(), fortuneLevel);
    }

    /**
     * Creates a loot-table builder with the exact base chance and per-Fortune-level increment.
     *
     * @param chance base probability threshold
     * @param fortuneMultiplier probability added for each Fortune level
     * @return a builder that creates this condition
     */
    public static LootCondition.Builder builder(float chance, float fortuneMultiplier) {
        return () -> new RandomChanceWithFortuneCondition(chance, fortuneMultiplier);
    }

    float chance() {
        return chance;
    }

    float fortuneMultiplier() {
        return fortuneMultiplier;
    }

    boolean passes(float randomSample, int fortuneLevel) {
        return randomSample < chance + fortuneLevel * fortuneMultiplier;
    }

}
