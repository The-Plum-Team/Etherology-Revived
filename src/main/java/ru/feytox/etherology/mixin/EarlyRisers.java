package ru.feytox.etherology.mixin;

import com.chocohead.mm.api.ClassTinkerers;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;

public class EarlyRisers implements Runnable {
    @Override
    public void run() {
        var remapper = FabricLoader.getInstance().getMappingResolver();

        var grassColor = remapper.mapClassName("intermediary", "net.minecraft.class_4763$class_5486");
        ClassTinkerers.enumBuilder(grassColor, String.class).addEnumSubclass("ETHEROLOGY_GOLDEN_FOREST", "ru.feytox.etherology.util.misc.GoldenForestGrassModifier", "etherology_golden_forest").build();

        var envType = FabricLoader.getInstance().getEnvironmentType();
        var useAction = remapper.mapClassName("intermediary", "net.minecraft.class_1839");

        addArmPose(remapper, envType, useAction, "OCULUS_ETHEROLOGY", true);
        addArmPose(remapper, envType, useAction, "STAFF_ETHEROLOGY", false);
        addArmPose(remapper, envType, useAction, "TWOHANDHELD_ETHEROLOGY", true);
    }

    private void addArmPose(MappingResolver remapper, EnvType envType, String actionPath, String name, boolean twohanded) {
        ClassTinkerers.enumBuilder(actionPath).addEnum(name).build();
        if (envType.equals(EnvType.SERVER))
            return;

        var armPath = remapper.mapClassName("intermediary", "net.minecraft.class_572$class_573");
        ClassTinkerers.enumBuilder(armPath, boolean.class).addEnum(name, twohanded).build();
    }
}
