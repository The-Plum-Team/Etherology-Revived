package ru.feytox.etherology.client.gui.teldecore.widget;

import net.minecraft.client.font.TextRenderer;
import ru.feytox.etherology.client.gui.teldecore.TeldecoreScreen;
import ru.feytox.etherology.gui.teldecore.content.AbstractContent;
import ru.feytox.etherology.gui.teldecore.content.ImageContent;
import ru.feytox.etherology.gui.teldecore.content.RecipeContent;
import ru.feytox.etherology.gui.teldecore.content.TextContent;

import java.util.Objects;

import static ru.feytox.etherology.client.gui.teldecore.widget.TextWidget.wrapText;

public class ContentWidgets {

    public static ParentedWidget getWidget(AbstractContent abstractContent, TeldecoreScreen parent, float x, float y) {
        Objects.requireNonNull(abstractContent);
        if (abstractContent instanceof TextContent content) return new TextWidget(parent, wrapText(content, parent.getTextRenderer()), x, y);
        if (abstractContent instanceof RecipeContent content) return RecipeWidget.toWidget(content, parent, x, y);
        if (abstractContent instanceof ImageContent content) return new ImageWidget(parent, content, x, y);
        throw new IllegalStateException("Unexpected value: " + abstractContent);
    }

    public static float getHeight(AbstractContent abstractContent, TextRenderer textRenderer) {
        Objects.requireNonNull(abstractContent);
        if (abstractContent instanceof TextContent content) return TextWidget.getHeight(content, textRenderer);
        if (abstractContent instanceof RecipeContent content) return RecipeWidget.getHeight(content, textRenderer);
        if (abstractContent instanceof ImageContent content) return content.getHeight();
        throw new IllegalStateException("Unexpected value: " + abstractContent);
    }
}
