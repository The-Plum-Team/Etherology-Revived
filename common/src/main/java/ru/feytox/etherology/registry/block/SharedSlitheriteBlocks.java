package ru.feytox.etherology.registry.block;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns the loader-neutral registrations for Etherology's base Slitherite family.
 */
public final class SharedSlitheriteBlocks {

    private static final SharedDeferredRegister<Block> BLOCKS =
            SharedDeferredRegister.create(RegistryKeys.BLOCK);

    /** Supplies the full Slitherite block. */
    public static final RegistrySupplier<Block> SLITHERITE = BLOCKS.register(
            "slitherite",
            () -> new Block(AbstractBlock.Settings.copy(Blocks.STONE))
    );

    /** Supplies the Slitherite stairs after their base block becomes available. */
    public static final RegistrySupplier<StairsBlock> SLITHERITE_STAIRS = BLOCKS.register(
            "slitherite_stairs",
            () -> {
                Block baseBlock = SLITHERITE.get();
                return new StairsBlock(
                        baseBlock.getDefaultState(),
                        AbstractBlock.Settings.copy(baseBlock)
                );
            }
    );

    /** Supplies the Slitherite slab with its canonical stone-stairs settings. */
    public static final RegistrySupplier<SlabBlock> SLITHERITE_SLAB = BLOCKS.register(
            "slitherite_slab",
            () -> new SlabBlock(AbstractBlock.Settings.copy(Blocks.STONE_STAIRS))
    );

    /** Supplies the Slitherite wall with its canonical stone-brick-wall settings. */
    public static final RegistrySupplier<WallBlock> SLITHERITE_WALL = BLOCKS.register(
            "slitherite_wall",
            () -> new WallBlock(AbstractBlock.Settings.copy(Blocks.STONE_BRICK_WALL))
    );

    private SharedSlitheriteBlocks() {
    }

    /**
     * Attaches this block registry exactly once during loader construction.
     */
    public static void register() {
        BLOCKS.attach();
    }
}
