package ru.feytox.etherology.forge.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.feytox.etherology.registry.block.SharedBlocks;

/**
 * Preserves the canonical lever-only support exception for thin Ethereal Channels.
 */
@Mixin(WallMountedBlock.class)
public abstract class ChannelLeverSupportMixin {

    @Shadow
    protected static Direction getDirection(BlockState state) {
        throw new AssertionError();
    }

    @Inject(
            method = "canPlaceAt(Lnet/minecraft/block/BlockState;"
                    + "Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void etherology$allowLeverOnChannel(
            BlockState state,
            WorldView world,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (!((Object) this instanceof LeverBlock)) {
            return;
        }

        Direction supportDirection = getDirection(state).getOpposite();
        if (world.getBlockState(pos.offset(supportDirection)).isOf(
                SharedBlocks.ETHEREAL_CHANNEL.get()
        )) {
            callback.setReturnValue(true);
        }
    }
}
