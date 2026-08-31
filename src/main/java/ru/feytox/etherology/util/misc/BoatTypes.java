package ru.feytox.etherology.util.misc;

import net.minecraft.block.Block;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.Item;
import ru.feytox.etherology.mixin.BoatEntityTypeAccessor;
import ru.feytox.etherology.registry.item.EItems;

public class BoatTypes {

    public static BoatEntity.Type PEACH;

    public static Item getBoatFromType(Item original, BoatEntity.Type type, boolean chest) {
        if (type.equals(PEACH)) return chest ? EItems.PEACH_CHEST_BOAT : EItems.PEACH_BOAT;
        return original;
    }

    public static void bindPeachBaseBlock(Block baseBlock) {
        if (PEACH == null) {
            throw new IllegalStateException("The peach boat type was not initialized");
        }
        ((BoatEntityTypeAccessor) (Object) PEACH).etherology$setBaseBlock(baseBlock);
    }

    static {
        BoatEntity.Type.values();
    }
}
