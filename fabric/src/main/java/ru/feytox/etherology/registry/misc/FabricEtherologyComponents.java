package ru.feytox.etherology.registry.misc;

import dev.onyxstudios.cca.api.v3.chunk.ChunkComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.chunk.ChunkComponentInitializer;
import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistryV3;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Registers and binds the Cardinal Components backend used by Fabric.
 */
public final class FabricEtherologyComponents implements EntityComponentInitializer, ChunkComponentInitializer {

    private static final ComponentKey<FabricCorruptionComponent> CORRUPTION_KEY =
            ComponentRegistryV3.INSTANCE.getOrCreate(
                    EtherologyComponents.CORRUPTION.getId(), FabricCorruptionComponent.class);
    private static final ComponentKey<FabricEtherComponent> ETHER_KEY =
            ComponentRegistryV3.INSTANCE.getOrCreate(
                    EtherologyComponents.ETHER.getId(), FabricEtherComponent.class);
    private static final ComponentKey<FabricTeldecoreComponent> TELDECORE_KEY =
            ComponentRegistryV3.INSTANCE.getOrCreate(
                    EtherologyComponents.TELDECORE.getId(), FabricTeldecoreComponent.class);
    private static final ComponentKey<FabricVisitedComponent> VISITED_KEY =
            ComponentRegistryV3.INSTANCE.getOrCreate(
                    EtherologyComponents.VISITED.getId(), FabricVisitedComponent.class);

    static {
        EtherologyComponents.CORRUPTION.bind(new FabricComponentAccess<>(CORRUPTION_KEY));
        EtherologyComponents.ETHER.bind(new FabricComponentAccess<>(ETHER_KEY));
        EtherologyComponents.TELDECORE.bind(new FabricComponentAccess<>(TELDECORE_KEY));
        EtherologyComponents.VISITED.bind(new FabricComponentAccess<>(VISITED_KEY));
    }

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerFor(LivingEntity.class, ETHER_KEY, FabricEtherComponent::new);
        registry.registerForPlayers(TELDECORE_KEY, FabricTeldecoreComponent::new, RespawnCopyStrategy.ALWAYS_COPY);
        registry.registerForPlayers(VISITED_KEY, FabricVisitedComponent::new, RespawnCopyStrategy.ALWAYS_COPY);
    }

    @Override
    public void registerChunkComponentFactories(@NotNull ChunkComponentFactoryRegistry registry) {
        registry.register(CORRUPTION_KEY, FabricCorruptionComponent::new);
    }
}
