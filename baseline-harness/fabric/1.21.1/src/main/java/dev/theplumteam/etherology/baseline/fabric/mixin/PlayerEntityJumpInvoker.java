package dev.theplumteam.etherology.baseline.fabric.mixin;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerEntity.class)
public interface PlayerEntityJumpInvoker {

    @Invoker("jump")
    void etherologyOriginalBaseline$invokeJump();
}
