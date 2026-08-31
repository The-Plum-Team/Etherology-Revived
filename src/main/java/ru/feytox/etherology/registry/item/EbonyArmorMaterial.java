package ru.feytox.etherology.registry.item;

import com.google.common.base.Suppliers;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import ru.feytox.etherology.util.misc.EIdentifier;

import java.util.function.Supplier;

public final class EbonyArmorMaterial implements ArmorMaterial {

    public static final EbonyArmorMaterial INSTANCE = new EbonyArmorMaterial();

    private final Supplier<Ingredient> repairIngredient = Suppliers.memoize(() -> Ingredient.ofItems(DecoBlockItems.EBONY_INGOT));

    private EbonyArmorMaterial() {}

    @Override
    public int getDurability(ArmorItem.Type type) {
        return ArmorMaterials.IRON.getDurability(type);
    }

    @Override
    public int getProtection(ArmorItem.Type type) {
        return ArmorMaterials.IRON.getProtection(type);
    }

    @Override
    public int getEnchantability() {
        return 16;
    }

    @Override
    public SoundEvent getEquipSound() {
        return ArmorMaterials.IRON.getEquipSound();
    }

    @Override
    public Ingredient getRepairIngredient() {
        return repairIngredient.get();
    }

    @Override
    public String getName() {
        return EIdentifier.strId("ebony");
    }

    @Override
    public float getToughness() {
        return ArmorMaterials.IRON.getToughness();
    }

    @Override
    public float getKnockbackResistance() {
        return ArmorMaterials.IRON.getKnockbackResistance();
    }
}
