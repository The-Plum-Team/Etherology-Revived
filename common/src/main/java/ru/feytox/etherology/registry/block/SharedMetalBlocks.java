package ru.feytox.etherology.registry.block;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns the loader-neutral registrations for Etherology's behavior-free metal blocks.
 */
public final class SharedMetalBlocks {

    private static final SharedDeferredRegister<Block> BLOCKS =
            SharedDeferredRegister.create(RegistryKeys.BLOCK);

    /** Supplies the azel storage block with its canonical map color. */
    public static final RegistrySupplier<Block> AZEL_BLOCK = BLOCKS.register(
            "azel_block",
            () -> new Block(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)
                    .mapColor(MapColor.LAPIS_BLUE))
    );

    /** Supplies the ethril storage block. */
    public static final RegistrySupplier<Block> ETHRIL_BLOCK = BLOCKS.register(
            "ethril_block",
            () -> new Block(AbstractBlock.Settings.copy(Blocks.GOLD_BLOCK))
    );

    /** Supplies the ebony storage block with its canonical map color. */
    public static final RegistrySupplier<Block> EBONY_BLOCK = BLOCKS.register(
            "ebony_block",
            () -> new Block(AbstractBlock.Settings.copy(Blocks.DIAMOND_BLOCK)
                    .mapColor(MapColor.ORANGE))
    );

    private SharedMetalBlocks() {
    }

    /**
     * Attaches this block registry exactly once during loader construction.
     */
    public static void register() {
        BLOCKS.attach();
    }
}
