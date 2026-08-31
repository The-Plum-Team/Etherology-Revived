package ru.feytox.etherology.registry.item;

import net.minecraft.block.Block;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import ru.feytox.etherology.registry.block.DecoBlocks;
import ru.feytox.etherology.util.misc.EIdentifier;

// TODO: 16.06.2023 rename
// TODO: 29.02.2024 move to EItems
public class DecoBlockItems {

    // peach wood
    public static final Item PEACH_DOOR = registerBlockItem(new TallBlockItem(DecoBlocks.PEACH_DOOR, new Item.Settings()));
    public static final Item PEACH_SIGN = registerBlockItem(new SignItem(new Item.Settings().maxCount(16), DecoBlocks.PEACH_SIGN, DecoBlocks.PEACH_WALL_SIGN));
    public static final Item PEACH_HANGING_SIGN = registerBlockItem(new HangingSignItem(DecoBlocks.PEACH_HANGING_SIGN, DecoBlocks.PEACH_WALL_HANGING_SIGN, new Item.Settings().maxCount(16)));

    // plants
    public static final Item BEAMER_SEEDS = registerAliasedBlockItem("beamer_seeds", DecoBlocks.BEAMER);
    public static final Item BEAM_FRUIT = registerAliasedBlockItem("beam_fruit", DecoBlocks.BEAMER);
    public static final Item THUJA_SEEDS = registerAliasedBlockItem("thuja_seeds", DecoBlocks.THUJA);

    private static Item registerBlockItem(BlockItem blockItem) {
        blockItem.appendBlocks(Item.BLOCK_ITEMS, blockItem);
        return Registry.register(Registries.ITEM, Registries.BLOCK.getId(blockItem.getBlock()), blockItem);
    }

    public static Item registerAliasedBlockItem(String id, Block block) {
        AliasedBlockItem aliasedBlockItem = new AliasedBlockItem(block, new Item.Settings());
        Registry.register(Registries.ITEM, EIdentifier.of(id), aliasedBlockItem);
        return aliasedBlockItem;
    }

    public static void registerAll() {}
}
