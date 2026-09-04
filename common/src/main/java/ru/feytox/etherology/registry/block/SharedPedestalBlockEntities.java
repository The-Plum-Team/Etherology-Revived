package ru.feytox.etherology.registry.block;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.block.pedestal.PedestalBlockEntity;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns the loader-neutral Pedestal block-entity registration.
 */
public final class SharedPedestalBlockEntities {

    private static final SharedDeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            SharedDeferredRegister.create(RegistryKeys.BLOCK_ENTITY_TYPE);

    /** Supplies the Pedestal block-entity type after block registration. */
    public static final RegistrySupplier<BlockEntityType<PedestalBlockEntity>> PEDESTAL =
            BLOCK_ENTITIES.register(
                    "pedestal_block_entity",
                    () -> BlockEntityType.Builder.create(
                            PedestalBlockEntity::new,
                            SharedPedestalBlocks.PEDESTAL.get()
                    ).build(null)
            );

    private SharedPedestalBlockEntities() {
    }

    /** Attaches the Pedestal block-entity registry exactly once. */
    public static void register() {
        BLOCK_ENTITIES.attach();
    }
}
