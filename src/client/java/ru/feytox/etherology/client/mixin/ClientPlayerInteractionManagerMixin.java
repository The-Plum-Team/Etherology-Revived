package ru.feytox.etherology.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import ru.feytox.etherology.item.BroadSwordItem;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Shadow @Final
    private MinecraftClient client;

    @ModifyReturnValue(method = "getReachDistance", at = @At("RETURN"))
    private float extendReachWithBroadSword(float original) {
        ClientPlayerEntity player = client.player;
        return player != null && BroadSwordItem.isUsing(player) ? original * 1.33f : original;
    }
}
