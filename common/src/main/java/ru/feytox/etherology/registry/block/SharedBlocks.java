package ru.feytox.etherology.registry.block;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.block.etherealChannel.EtherealChannelFoundationBlock;
import ru.feytox.etherology.block.etherealStorage.EtherealStorageFoundationBlock;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns loader-neutral block registrations accepted by every active loader.
 */
public final class SharedBlocks {

    private static final SharedDeferredRegister<Block> BLOCKS =
            SharedDeferredRegister.create(RegistryKeys.BLOCK);

    /**
     * Supplies the persistent ethereal-storage block after the block registry event completes.
     */
    public static final RegistrySupplier<EtherealStorageFoundationBlock> ETHEREAL_STORAGE =
            BLOCKS.register("ethereal_storage", EtherealStorageFoundationBlock::new);

    /**
     * Supplies the directed Ether channel after the active loader registers blocks.
     */
    public static final RegistrySupplier<EtherealChannelFoundationBlock> ETHEREAL_CHANNEL =
            BLOCKS.register("ethereal_channel", EtherealChannelFoundationBlock::new);

    private SharedBlocks() {
    }

    /**
     * Attaches the shared block registry once before loader registry events run.
     */
    public static void register() {
        BLOCKS.attach();
    }
}
