package ru.feytox.etherology.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.magic.seal.SealType;

import java.util.List;

/**
 * Identifies the seal aspect carried by one canonical Primoshard item.
 */
public class PrimoShard extends Item {

    private final String sealId;

    /**
     * Creates a default-stack item whose tooltip names the supplied seal aspect.
     *
     * @param sealType seal aspect represented by this item
     */
    public PrimoShard(SealType sealType) {
        super(new Item.Settings());
        this.sealId = StringUtils.capitalize(sealType.asString());
    }

    /**
     * Inserts the translated seal name directly after the item's primary tooltip line.
     */
    @Override
    public void appendTooltip(
            ItemStack stack,
            @Nullable World world,
            List<Text> tooltip,
            TooltipContext context
    ) {
        super.appendTooltip(stack, world, tooltip, context);
        Text lore = Text.translatable("lore.etherology.primoshard", sealId)
                .formatted(Formatting.DARK_PURPLE);
        tooltip.add(1, lore);
    }
}
