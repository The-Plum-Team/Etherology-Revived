package ru.feytox.etherology.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(BlockEntityType.class)
public interface BlockEntityTypeMixin {

    @Accessor("blocks")
    Set<Block> etherology$getBlocks();

    @Mutable
    @Accessor("blocks")
    void etherology$setBlocks(Set<Block> blocks);
}
