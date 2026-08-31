package ru.feytox.etherology.registry.block;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns the loader-neutral registrations for Etherology's Attrahite block family.
 */
public final class SharedAttrahiteBlocks {

    private static final SharedDeferredRegister<Block> BLOCKS =
            SharedDeferredRegister.create(RegistryKeys.BLOCK);

    /** Supplies raw Attrahite with its canonical gilded-blackstone sound group. */
    public static final RegistrySupplier<Block> ATTRAHITE = BLOCKS.register(
            "attrahite",
            () -> new Block(AbstractBlock.Settings.copy(Blocks.STONE)
                    .sounds(BlockSoundGroup.GILDED_BLACKSTONE))
    );

    /** Supplies the full Attrahite brick block. */
    public static final RegistrySupplier<Block> ATTRAHITE_BRICKS = BLOCKS.register(
            "attrahite_bricks",
            () -> new Block(AbstractBlock.Settings.copy(Blocks.STONE_BRICKS))
    );

    /** Supplies the Attrahite brick slab. */
    public static final RegistrySupplier<SlabBlock> ATTRAHITE_BRICK_SLAB = BLOCKS.register(
            "attrahite_brick_slab",
            () -> new SlabBlock(AbstractBlock.Settings.copy(Blocks.STONE_SLAB))
    );

    /** Supplies the stairs after their Attrahite brick base becomes available. */
    public static final RegistrySupplier<StairsBlock> ATTRAHITE_BRICK_STAIRS = BLOCKS.register(
            "attrahite_brick_stairs",
            () -> {
                Block baseBlock = ATTRAHITE_BRICKS.get();
                return new StairsBlock(
                        baseBlock.getDefaultState(),
                        AbstractBlock.Settings.copy(baseBlock)
                );
            }
    );

    private SharedAttrahiteBlocks() {
    }

    /**
     * Attaches this block registry exactly once during loader construction.
     */
    public static void register() {
        BLOCKS.attach();
    }
}
