package dev.theplumteam.etherology.e2e.fabric.mixin;

import dev.theplumteam.etherology.e2e.fabric.PhaseZeroHarness;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void etherologyE2e$onRenderCompleted(
            float tickDelta,
            long startTime,
            boolean tick,
            CallbackInfo callbackInfo
    ) {
        PhaseZeroHarness.onGameRenderCompleted();
    }
}
