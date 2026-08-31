package ru.feytox.etherology.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.item.ShearsItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.feytox.etherology.registry.block.DecoBlocks;

@Mixin(ShearsItem.class)
public class ShearsItemMixin {

    @ModifyReturnValue(method = "postMine", at = @At("RETURN"))
    private boolean injectLightelet(boolean original, @Local(argsOnly = true) BlockState state) {
        return original || state.isOf(DecoBlocks.LIGHTELET);
    }
}
