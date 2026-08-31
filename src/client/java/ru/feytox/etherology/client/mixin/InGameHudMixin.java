package ru.feytox.etherology.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.feytox.etherology.client.gui.OcularOverlay;
import ru.feytox.etherology.client.gui.ether.DevastatingHearts;
import ru.feytox.etherology.client.gui.ether.ExhaustionOverlay;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Shadow private float spyglassScale;

    @Inject(method = "drawHeart", at = @At("HEAD"), cancellable = true)
    private void injectEtherHearts(DrawContext context, InGameHud.HeartType type, int x, int y, int textureV, boolean blinking, boolean halfHeart, CallbackInfo ci) {
        Identifier changedTexture = DevastatingHearts.getDevastatingTexture(type, textureV != 0, halfHeart, blinking);
        if (changedTexture == null) return;

        context.drawTexture(changedTexture, x, y, 0.0f, 0.0f, 9, 9, 9, 9);
        ci.cancel();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getFrozenTicks()I"))
    private void injectRevelationRendering(DrawContext context, float tickDelta, CallbackInfo ci) {
        ExhaustionOverlay.renderOverlay(context, ((InGameHud)(Object) this));
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z"))
    private boolean injectOcularOverlay(boolean original, @Local(argsOnly = true) DrawContext context) {
        if (!original)
            return false;

        if (!OcularOverlay.shouldRenderOverlay())
            return true;

        OcularOverlay.renderOverlay(context, spyglassScale);
        return false;
    }
}
