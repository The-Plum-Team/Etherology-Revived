package ru.feytox.etherology.registry.block;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.block.forestLantern.ForestLanternBlock;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns the loader-neutral Forest Lantern block registration.
 */
public final class SharedForestLanternBlocks {

    private static final SharedDeferredRegister<Block> BLOCKS =
            SharedDeferredRegister.create(RegistryKeys.BLOCK);

    /** Supplies the Forest Lantern after the active loader registers blocks. */
    public static final RegistrySupplier<ForestLanternBlock> FOREST_LANTERN =
            BLOCKS.register("forest_lantern", ForestLanternBlock::new);

    private SharedForestLanternBlocks() {
    }

    /**
     * Attaches the Forest Lantern block registry exactly once during loader construction.
     */
    public static void register() {
        BLOCKS.attach();
    }
}
