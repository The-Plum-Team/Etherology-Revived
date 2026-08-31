package ru.feytox.etherology.registry.misc;

import lombok.experimental.UtilityClass;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.BoatDispenserBehavior;
import ru.feytox.etherology.block.generators.GeneratorDispenserBehavior;
import ru.feytox.etherology.registry.item.EItems;
import ru.feytox.etherology.registry.item.SharedMaterialItems;
import ru.feytox.etherology.util.misc.BoatTypes;

@UtilityClass
public class DispenserBehaviors {

    public static void registerAll() {
        DispenserBlock.registerBehavior(SharedMaterialItems.THUJA_OIL.get(), new GeneratorDispenserBehavior());
        DispenserBlock.registerBehavior(EItems.PEACH_BOAT, new BoatDispenserBehavior(BoatTypes.PEACH));
        DispenserBlock.registerBehavior(EItems.PEACH_CHEST_BOAT, new BoatDispenserBehavior(BoatTypes.PEACH, true));
        // PedestalDispenserBehavior is registering in DispenserBlockMixin
    }
}
