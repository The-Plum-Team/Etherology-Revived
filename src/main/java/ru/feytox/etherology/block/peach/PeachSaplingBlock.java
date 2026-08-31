package ru.feytox.etherology.block.peach;

import net.minecraft.block.Blocks;
import net.minecraft.block.SaplingBlock;
import ru.feytox.etherology.util.misc.RegistrableBlock;

public class PeachSaplingBlock extends SaplingBlock implements RegistrableBlock {

    private static final PeachSaplingGenerator PEACH_GENERATOR = new PeachSaplingGenerator();

    public PeachSaplingBlock() {
        super(PEACH_GENERATOR, Settings.copy(Blocks.ACACIA_SAPLING));
    }

    @Override
    public String getBlockId() {
        return "peach_sapling";
    }
}
