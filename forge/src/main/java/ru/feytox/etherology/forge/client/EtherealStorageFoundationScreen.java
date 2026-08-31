package ru.feytox.etherology.forge.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.feytox.etherology.block.etherealStorage.EtherealStorageFoundationScreenHandler;
import ru.feytox.etherology.bootstrap.EtherologyBootstrap;

/**
 * Renders the synchronized persistent storage-menu core with the canonical GUI texture.
 */
public final class EtherealStorageFoundationScreen
        extends HandledScreen<EtherealStorageFoundationScreenHandler> {

    private static final Identifier TEXTURE = Identifier.of(
            EtherologyBootstrap.MOD_ID,
            "textures/gui/ethereal_storage.png"
    );

    /**
     * Creates the client view for a server-owned ethereal-storage menu.
     *
     * @param handler synchronized storage handler
     * @param playerInventory local player inventory
     * @param title server-supplied localized title
     */
    public EtherealStorageFoundationScreen(
            EtherealStorageFoundationScreenHandler handler,
            PlayerInventory playerInventory,
            Text title
    ) {
        super(handler, playerInventory, title);
    }

    @Override
    protected void drawBackground(
            DrawContext context,
            float delta,
            int mouseX,
            int mouseY
    ) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight);
    }

    /**
     * Draws the synchronized menu and its item tooltip layer.
     *
     * @param context current GUI drawing context
     * @param mouseX cursor X coordinate
     * @param mouseY cursor Y coordinate
     * @param delta partial tick time
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
        titleY = 6;
        playerInventoryTitleY = backgroundHeight - 124;
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, titleX, titleY, 0xFFE5E5E5, false);
        context.drawText(
                textRenderer,
                playerInventoryTitle,
                playerInventoryTitleX,
                playerInventoryTitleY,
                0xFFE5E5E5,
                false
        );
    }
}
