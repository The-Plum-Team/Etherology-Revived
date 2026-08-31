package dev.theplumteam.etherology.baseline.fabric.mixin;

import dev.theplumteam.etherology.baseline.fabric.OriginalPhaseZeroHarness;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void etherologyOriginalBaseline$onRenderCompleted(
            RenderTickCounter tickCounter,
            boolean tick,
            CallbackInfo callbackInfo
    ) {
        OriginalPhaseZeroHarness.onGameRenderCompleted();
    }
}
