package ru.feytox.etherology;

import com.mojang.logging.LogUtils;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import ru.feytox.etherology.block.etherealChannel.ChannelShapes;
import ru.feytox.etherology.block.forestLantern.ForestLanternBlock;
import ru.feytox.etherology.commands.DevCommands;
import ru.feytox.etherology.magic.lens.LensModifier;
import ru.feytox.etherology.magic.lens.RedstoneLensEffects;
import ru.feytox.etherology.magic.staff.StaffPatterns;
import ru.feytox.etherology.network.EtherologyNetwork;
import ru.feytox.etherology.registry.block.EBlockFamilies;
import ru.feytox.etherology.registry.block.EBlocks;
import ru.feytox.etherology.registry.block.ExtraBlocksRegistry;
import ru.feytox.etherology.registry.block.SharedAttrahiteBlocks;
import ru.feytox.etherology.registry.block.SharedForestLanternBlocks;
import ru.feytox.etherology.registry.block.SharedMetalBlocks;
import ru.feytox.etherology.registry.block.SharedPedestalBlockEntities;
import ru.feytox.etherology.registry.block.SharedPedestalBlocks;
import ru.feytox.etherology.registry.block.SharedSlitheriteBlocks;
import ru.feytox.etherology.registry.entity.EntityRegistry;
import ru.feytox.etherology.registry.item.EItemGroups;
import ru.feytox.etherology.registry.item.EItems;
import ru.feytox.etherology.registry.item.SharedAttrahiteBlockItems;
import ru.feytox.etherology.registry.item.SharedFoodItems;
import ru.feytox.etherology.registry.item.SharedForestLanternBlockItems;
import ru.feytox.etherology.registry.item.SharedLensItems;
import ru.feytox.etherology.registry.item.SharedMaterialItems;
import ru.feytox.etherology.registry.item.SharedMetalBlockItems;
import ru.feytox.etherology.registry.item.SharedPedestalBlockItems;
import ru.feytox.etherology.registry.item.SharedPrimoShardItems;
import ru.feytox.etherology.registry.item.SharedSlitheriteBlockItems;
import ru.feytox.etherology.registry.item.SharedToolItems;
import ru.feytox.etherology.registry.misc.*;
import ru.feytox.etherology.registry.world.WorldGenRegistry;
import ru.feytox.etherology.util.delayedTask.ServerTaskManager;

public class Etherology {

    public static final Logger ELOGGER = LogUtils.getLogger();
    public static final String MOD_ID = "etherology";
    private static final ObjectArrayList<ServerWorld> loadedWorlds = new ObjectArrayList<>();
    private static boolean initialized;

    /**
     * Registers loader-neutral content and server handlers. Subsequent calls have no effect.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        SharedAttrahiteBlocks.register();
        SharedSlitheriteBlocks.register();
        SharedPedestalBlocks.register();
        SharedAttrahiteBlockItems.register();
        SharedSlitheriteBlockItems.register();
        SharedPedestalBlockItems.register();
        SharedPedestalBlockEntities.register();
        SharedSounds.register();
        SharedGameEvents.register();
        SharedLootConditions.register();
        SharedEnchantments.register();
        FabricGameEventHooks.registerSculkSensorFrequency();
        ExtraBlocksRegistry.registerAll();
        RegistriesRegistry.registerAll();
        SharedMetalBlocks.register();
        SharedForestLanternBlocks.register();
        SharedMetalBlockItems.register();
        SharedForestLanternBlockItems.register();
        SharedMaterialItems.register();
        SharedFoodItems.register();
        SharedToolItems.register();
        SharedLensItems.register();
        SharedPrimoShardItems.register();
        SharedAlchemyRecipes.register();
        EItems.registerItems();
        EtherEnchantments.registerAll();
        EBlocks.registerAll();
        ResourceReloaders.registerServerData();
        EtherologyNetwork.registerCommonSide();
        EBlockFamilies.registerFamilies();
        DevCommands.register();
        RecipesRegistry.registerAll();
        ScreenHandlersRegistry.registerServerSide();
        WorldGenRegistry.registerWorldGen();
        StaffPatterns.registerAll();
        EItemGroups.registerAll();
        LootTablesModifyRegistry.registerAll();
        TradeOffersModificationRegistry.registerAll();
        EntityRegistry.registerServerSide();
        DispenserBehaviors.registerAll();
        LensModifier.registerAll();
        EffectsRegistry.registerAll();
        ComponentTypes.registerAll();
        ForestLanternBlock.registerJumpEvent();
        ChannelShapes.cacheAll();

        LifecycleEvent.SERVER_LEVEL_LOAD.register(loadedWorlds::add);
        LifecycleEvent.SERVER_LEVEL_UNLOAD.register(loadedWorlds::remove);

        TickEvent.SERVER_POST.register(server -> ServerTaskManager.INSTANCE.tickTasks());
        TickEvent.SERVER_LEVEL_POST.register(world -> RedstoneLensEffects.getServerState(world).tick(world));
    }

    // TODO: 16.07.2024 use something else
    @Nullable
    public static ServerWorld getAnyServerWorld() {
        return loadedWorlds.isEmpty() ? null : loadedWorlds.get(0);
    }
}
