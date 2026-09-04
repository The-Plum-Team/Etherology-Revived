package ru.feytox.etherology;

import net.fabricmc.api.ModInitializer;
import ru.feytox.etherology.block.pedestal.FabricPedestalBlockEntityRemovalBackend;
import ru.feytox.etherology.block.pedestal.PedestalBlockEntityRemoval;
import ru.feytox.etherology.item.FabricLensRuntimeBackend;
import ru.feytox.etherology.item.LensRuntime;
import ru.feytox.etherology.recipes.FabricRecipeResultComponentBackend;
import ru.feytox.etherology.recipes.RecipeResultComponents;
import ru.feytox.etherology.registry.particle.SharedParticleTypes;

public final class EtherologyFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        RecipeResultComponents.bind(
                FabricRecipeResultComponentBackend.INSTANCE
        );
        LensRuntime.bind(FabricLensRuntimeBackend.INSTANCE);
        PedestalBlockEntityRemoval.bind(
                FabricPedestalBlockEntityRemovalBackend.INSTANCE
        );
        SharedParticleTypes.register();
        Etherology.initialize();
    }
}
