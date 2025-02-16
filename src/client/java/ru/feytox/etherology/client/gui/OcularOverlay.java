package ru.feytox.etherology.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import ru.feytox.etherology.registry.item.ToolItems;
import ru.feytox.etherology.util.misc.EIdentifier;

public class OcularOverlay {

    private static final Identifier OCULAR_SCOPE = EIdentifier.of("textures/misc/ocular_scope.png");

    public static boolean shouldRenderOverlay() {
        var player = MinecraftClient.getInstance().player;
        if (player == null)
            return false;

        return player.isUsingItem() && player.getActiveItem().isOf(ToolItems.OCULAR);
    }

    /**
     * @see net.minecraft.client.gui.hud.InGameHud#renderSpyglassOverlay(DrawContext, float)
     */
    public static void renderOverlay(DrawContext context, float scale) {
        context.push();
        context.translate(0, 0, -100);
        context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), 0x66663399);
        context.pop();

        var minScaledSize = (float) Math.min(context.getScaledWindowWidth(), context.getScaledWindowHeight());
        var sizeScaled = MathHelper.floor(minScaledSize * scale * 0.75);
        var x = (context.getScaledWindowWidth() - sizeScaled) / 2;
        var y = (context.getScaledWindowHeight() - sizeScaled) / 2;
        var endX = x + sizeScaled;
        var endY = y + sizeScaled;
        RenderSystem.enableBlend();
        context.drawTexture(OCULAR_SCOPE, x, y, -90, 0.0F, 0.0F, sizeScaled, sizeScaled, sizeScaled, sizeScaled);
        RenderSystem.disableBlend();
        context.fill(RenderLayer.getGuiOverlay(), 0, endY, context.getScaledWindowWidth(), context.getScaledWindowHeight(), -90, Colors.BLACK);
        context.fill(RenderLayer.getGuiOverlay(), 0, 0, context.getScaledWindowWidth(), y, -90, Colors.BLACK);
        context.fill(RenderLayer.getGuiOverlay(), 0, y, x, endY, -90, Colors.BLACK);
        context.fill(RenderLayer.getGuiOverlay(), endX, y, context.getScaledWindowWidth(), endY, -90, Colors.BLACK);
    }
}
