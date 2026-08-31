package ru.feytox.etherology.registry.block;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.block.etherealStorage.EtherealStorageFoundationBlockEntity;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns loader-neutral block-entity type registrations accepted by every active loader.
 */
public final class SharedBlockEntities {

    private static final SharedDeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            SharedDeferredRegister.create(RegistryKeys.BLOCK_ENTITY_TYPE);

    /**
     * Supplies the persistent ethereal-storage type after its registry event completes.
     */
    public static final RegistrySupplier<BlockEntityType<EtherealStorageFoundationBlockEntity>>
            ETHEREAL_STORAGE = BLOCK_ENTITIES.register(
                    "ethereal_storage_block_entity",
                    () -> BlockEntityType.Builder.create(
                            EtherealStorageFoundationBlockEntity::new,
                            SharedBlocks.ETHEREAL_STORAGE.get()
                    ).build(null)
            );

    private SharedBlockEntities() {
    }

    /**
     * Attaches the shared block-entity registry once before loader registry events run.
     */
    public static void register() {
        BLOCK_ENTITIES.attach();
    }
}
