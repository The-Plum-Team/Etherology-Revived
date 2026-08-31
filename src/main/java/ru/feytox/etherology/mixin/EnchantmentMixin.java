package ru.feytox.etherology.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.feytox.etherology.registry.misc.EtherEnchantments;

@Mixin(Enchantment.class)
public class EnchantmentMixin {

    @ModifyReturnValue(method = "isAcceptableItem", at = @At("RETURN"))
    private boolean applyItemEnchantability(boolean original, @Local(argsOnly = true) ItemStack stack) {
        return EtherEnchantments.isAcceptableItem((Enchantment) (Object) this, stack.getItem(), original);
    }
}
