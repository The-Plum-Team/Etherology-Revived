package dev.theplumteam.etherology.baseline.fabric;

import net.minecraft.block.Block;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

record AttrahiteBlockFixture(
        Identifier id,
        Class<? extends Block> blockClass,
        BlockPos position,
        Map<String, String> defaultProperties,
        int stateCount
) {
}
