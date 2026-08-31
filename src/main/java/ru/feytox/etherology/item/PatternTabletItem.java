package ru.feytox.etherology.item;

import lombok.Getter;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.magic.staff.StaffStyles;
import ru.feytox.etherology.mixin.SmithingTemplateItemAccessor;

import java.util.List;

@Getter
public class PatternTabletItem extends Item {

    private static final Text APPLIES = Text.translatable("item.etherology.pattern_tablet.applies_to").formatted(SmithingTemplateItemAccessor.getDescriptionFormatting());
    private static final Text INGREDIENTS = Text.translatable("item.etherology.pattern_tablet.ingredients").formatted(SmithingTemplateItemAccessor.getDescriptionFormatting());

    private final StaffStyles staffStyle;

    public PatternTabletItem(StaffStyles staffStyle) {
        super(new Settings());
        this.staffStyle = staffStyle;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable(getTranslationKey()).formatted(SmithingTemplateItemAccessor.getTitleFormatting()));
        tooltip.add(ScreenTexts.EMPTY);
        tooltip.add(SmithingTemplateItemAccessor.getAppliesToText());
        tooltip.add(ScreenTexts.space().append(APPLIES));
        tooltip.add(SmithingTemplateItemAccessor.getIngredientsText());
        tooltip.add(ScreenTexts.space().append(INGREDIENTS));
    }

    @Override
    public Text getName() {
        return Text.translatable("item.etherology.pattern_tablet");
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable("item.etherology.pattern_tablet");
    }
}
