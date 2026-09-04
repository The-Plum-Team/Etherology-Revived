package ru.feytox.etherology.forge.mixin;

import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.DispenserBehavior;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPointerImpl;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.feytox.etherology.block.pedestal.PedestalDispenserBehavior;

/**
 * Selects the Pedestal behavior before Forge invokes vanilla dispenser logic.
 */
@Mixin(DispenserBlock.class)
public abstract class PedestalDispenserBlockMixin {

    /**
     * Calls the original behavior lookup when no Pedestal accepts the stack.
     *
     * @param stack dispensed stack
     * @return vanilla-selected behavior
     */
    @Shadow
    protected abstract DispenserBehavior getBehaviorForItem(ItemStack stack);

    @Redirect(
            method = "dispense(Lnet/minecraft/server/world/ServerWorld;"
                    + "Lnet/minecraft/util/math/BlockPos;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/DispenserBlock;"
                            + "getBehaviorForItem(Lnet/minecraft/item/ItemStack;)"
                            + "Lnet/minecraft/block/dispenser/DispenserBehavior;"
            )
    )
    private DispenserBehavior etherology$selectPedestalBehavior(
            DispenserBlock dispenser,
            ItemStack stack,
            ServerWorld world,
            BlockPos pos
    ) {
        BlockPointerImpl pointer = new BlockPointerImpl(world, pos);
        return PedestalDispenserBehavior.testDispenser(pointer, stack)
                ? PedestalDispenserBehavior.getInstance()
                : getBehaviorForItem(stack);
    }
}
