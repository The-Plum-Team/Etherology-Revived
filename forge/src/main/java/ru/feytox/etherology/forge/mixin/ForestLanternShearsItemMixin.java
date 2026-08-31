package ru.feytox.etherology.forge.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShearsItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.feytox.etherology.block.forestLantern.ForestLanternBlock;
import ru.feytox.etherology.registry.block.SharedForestLanternBlocks;

@Mixin(ShearsItem.class)
public abstract class ForestLanternShearsItemMixin {

    @Inject(method = "getMiningSpeedMultiplier", at = @At("HEAD"), cancellable = true)
    private void etherology$useForestLanternSpeed(
            ItemStack stack,
            BlockState state,
            CallbackInfoReturnable<Float> callback
    ) {
        if (state.isOf(SharedForestLanternBlocks.FOREST_LANTERN.get())) {
            callback.setReturnValue(ForestLanternBlock.SHEARS_MINING_SPEED);
        }
    }
}
