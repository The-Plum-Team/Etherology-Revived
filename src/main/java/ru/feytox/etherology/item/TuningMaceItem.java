package ru.feytox.etherology.item;

import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterials;
import net.minecraft.util.Rarity;
import net.minecraft.util.UseAction;
import ru.feytox.etherology.enums.EUseAction;

public class TuningMaceItem extends TwoHandheldSword {

    public TuningMaceItem() {
        super(ToolMaterials.IRON, 6, -3.2f, new Settings().maxDamage(476).rarity(Rarity.UNCOMMON));
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return EUseAction.TWOHANDHELD_ETHEROLOGY.getUseAction();
    }
}
