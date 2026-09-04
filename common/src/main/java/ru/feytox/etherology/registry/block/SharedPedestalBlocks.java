package ru.feytox.etherology.registry.block;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.block.pedestal.PedestalBlock;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns the loader-neutral Pedestal block registration.
 */
public final class SharedPedestalBlocks {

    private static final SharedDeferredRegister<Block> BLOCKS =
            SharedDeferredRegister.create(RegistryKeys.BLOCK);

    /** Supplies the Pedestal after the active loader registers blocks. */
    public static final RegistrySupplier<PedestalBlock> PEDESTAL =
            BLOCKS.register("pedestal", PedestalBlock::new);

    private SharedPedestalBlocks() {
    }

    /** Attaches the Pedestal block registry exactly once. */
    public static void register() {
        BLOCKS.attach();
    }
}
