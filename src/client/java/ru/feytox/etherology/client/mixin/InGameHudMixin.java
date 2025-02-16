package ru.feytox.etherology.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
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

    @WrapOperation(method = "drawHeart", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud$HeartType;getTexture(ZZZ)Lnet/minecraft/util/Identifier;"))
    private Identifier injectEtherHearts(InGameHud.HeartType instance, boolean hardcore, boolean half, boolean blinking, Operation<Identifier> original) {
        Identifier changedTexture = DevastatingHearts.getDevastatingTexture(instance, hardcore, half, blinking);
        return changedTexture != null ? changedTexture : original.call(instance, hardcore, half, blinking);
    }

    @Inject(method = "renderMiscOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getFrozenTicks()I"))
    private void injectRevelationRendering(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ExhaustionOverlay.renderOverlay(context, ((InGameHud)(Object) this));
    }

    @ModifyExpressionValue(method = "renderMiscOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z"))
    private boolean injectOcularOverlay(boolean original, @Local(argsOnly = true) DrawContext context) {
        if (!original)
            return false;

        if (!OcularOverlay.shouldRenderOverlay())
            return true;

        OcularOverlay.renderOverlay(context, spyglassScale);
        return false;
    }
}
