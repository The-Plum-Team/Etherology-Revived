package ru.feytox.etherology.mixin;

import net.minecraft.block.Block;
import net.minecraft.entity.vehicle.BoatEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BoatEntity.Type.class)
public interface BoatEntityTypeAccessor {

    @Mutable
    @Accessor("baseBlock")
    void etherology$setBaseBlock(Block baseBlock);
}
