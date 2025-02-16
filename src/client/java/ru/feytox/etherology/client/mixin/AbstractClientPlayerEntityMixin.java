package ru.feytox.etherology.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.feytox.etherology.client.gui.OcularOverlay;

@Mixin(AbstractClientPlayerEntity.class)
public class AbstractClientPlayerEntityMixin {

    @ModifyReturnValue(method = "getFovMultiplier", at = @At("RETURN"))
    private float injectOcularMultiplier(float original) {
        return OcularOverlay.shouldRenderOverlay() ? 0.5f : original;
    }
}
