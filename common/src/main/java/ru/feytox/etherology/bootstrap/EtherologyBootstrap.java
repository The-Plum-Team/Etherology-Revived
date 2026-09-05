package ru.feytox.etherology.bootstrap;

import ru.feytox.etherology.block.forestLantern.ForestLanternBlock;
import ru.feytox.etherology.registry.block.SharedAttrahiteBlocks;
import ru.feytox.etherology.registry.block.SharedBlockEntities;
import ru.feytox.etherology.registry.block.SharedBlocks;
import ru.feytox.etherology.registry.block.SharedForestLanternBlocks;
import ru.feytox.etherology.registry.block.SharedMetalBlocks;
import ru.feytox.etherology.registry.block.SharedPedestalBlockEntities;
import ru.feytox.etherology.registry.block.SharedPedestalBlocks;
import ru.feytox.etherology.registry.block.SharedSlitheriteBlocks;
import ru.feytox.etherology.registry.item.SharedAttrahiteBlockItems;
import ru.feytox.etherology.registry.item.SharedFoodItems;
import ru.feytox.etherology.registry.item.SharedForestLanternBlockItems;
import ru.feytox.etherology.registry.item.SharedItems;
import ru.feytox.etherology.registry.item.SharedLensItems;
import ru.feytox.etherology.registry.item.SharedMaterialItems;
import ru.feytox.etherology.registry.item.SharedMetalBlockItems;
import ru.feytox.etherology.registry.item.SharedPatternTabletItems;
import ru.feytox.etherology.registry.item.SharedPedestalBlockItems;
import ru.feytox.etherology.registry.item.SharedPrimoShardItems;
import ru.feytox.etherology.registry.item.SharedSlitheriteBlockItems;
import ru.feytox.etherology.registry.item.SharedToolItems;
import ru.feytox.etherology.registry.misc.LootTablesModifyRegistry;
import ru.feytox.etherology.registry.misc.ResourceReloaders;
import ru.feytox.etherology.registry.misc.SharedAlchemyRecipes;
import ru.feytox.etherology.registry.misc.SharedEnchantments;
import ru.feytox.etherology.registry.misc.SharedGameEvents;
import ru.feytox.etherology.registry.misc.SharedLootConditions;
import ru.feytox.etherology.registry.misc.SharedScreenHandlers;
import ru.feytox.etherology.registry.misc.SharedSounds;
import ru.feytox.etherology.registry.misc.TradeOffersModificationRegistry;
import ru.feytox.etherology.registry.particle.SharedParticleTypes;

/**
 * Installs loader-neutral Etherology registrations and coordinates the loader lifecycle handshake.
 */
public final class EtherologyBootstrap {

    /**
     * Identifies Etherology registrations consistently across loader entry points.
     */
    public static final String MOD_ID = "etherology";

    private static final BootstrapLifecycle LIFECYCLE = new BootstrapLifecycle();

    private EtherologyBootstrap() {
    }

    /**
     * Attaches shared registries before binding the loader handshake exactly once.
     *
     * @param registrar adapter for the active loader's setup lifecycle
     */
    public static void initialize(PlatformRegistrar registrar) {
        SharedBlocks.register();
        SharedMetalBlocks.register();
        SharedForestLanternBlocks.register();
        SharedAttrahiteBlocks.register();
        SharedSlitheriteBlocks.register();
        SharedPedestalBlocks.register();
        SharedMetalBlockItems.register();
        SharedForestLanternBlockItems.register();
        SharedAttrahiteBlockItems.register();
        SharedSlitheriteBlockItems.register();
        SharedPedestalBlockItems.register();
        SharedItems.register();
        SharedMaterialItems.register();
        SharedFoodItems.register();
        SharedToolItems.register();
        SharedLensItems.register();
        SharedPrimoShardItems.register();
        SharedAlchemyRecipes.register();
        SharedPatternTabletItems.register();
        SharedBlockEntities.register();
        SharedPedestalBlockEntities.register();
        SharedScreenHandlers.register();
        SharedSounds.register();
        SharedGameEvents.register();
        SharedLootConditions.register();
        SharedEnchantments.register();
        SharedParticleTypes.register();
        ResourceReloaders.registerServerData();
        ForestLanternBlock.registerJumpEvent();
        LootTablesModifyRegistry.registerAll();
        TradeOffersModificationRegistry.registerAll();
        LIFECYCLE.initialize(registrar);
    }
}
