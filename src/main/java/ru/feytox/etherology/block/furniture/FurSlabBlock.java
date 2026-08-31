package ru.feytox.etherology.block.furniture;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.enums.FurnitureType;

public class FurSlabBlock extends AbstractFurSlabBlock {

    public FurSlabBlock() {
        super("furniture_slab", AbstractBlock.Settings.copy(Blocks.CHEST), FurnitureType.FURNITURE);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }
}
