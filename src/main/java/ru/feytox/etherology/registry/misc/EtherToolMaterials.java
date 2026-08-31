package ru.feytox.etherology.registry.misc;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import lombok.Getter;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;
import net.minecraft.recipe.Ingredient;
import ru.feytox.etherology.registry.item.SharedMaterialItems;

@SuppressWarnings("Guava")
public enum EtherToolMaterials implements ToolMaterial {
    ETHRIL(ToolMaterials.DIAMOND.getMiningLevel(), 1561, 8.0F, 3.0F, 10,
            () -> Ingredient.ofItems(SharedMaterialItems.ETHRIL_INGOT.get())),
    EBONY(ToolMaterials.IRON.getMiningLevel(), 320, 7, 3, 16,
            () -> Ingredient.ofItems(SharedMaterialItems.EBONY_INGOT.get()));

    @Getter
    private final int miningLevel;
    private final int itemDurability;
    private final float miningSpeed;
    @Getter
    private final float attackDamage;
    @Getter
    private final int enchantability;
    private final Supplier<Ingredient> repairIngredient;

    EtherToolMaterials(int miningLevel, int itemDurability, float miningSpeed, float attackDamage, int enchantability, Supplier<Ingredient> repairIngredient) {
        this.miningLevel = miningLevel;
        this.itemDurability = itemDurability;
        this.miningSpeed = miningSpeed;
        this.attackDamage = attackDamage;
        this.enchantability = enchantability;
        this.repairIngredient = Suppliers.memoize(repairIngredient);
    }

    public int getDurability() {
        return itemDurability;
    }

    public float getMiningSpeedMultiplier() {
        return miningSpeed;
    }

    public Ingredient getRepairIngredient() {
        return repairIngredient.get();
    }
}
