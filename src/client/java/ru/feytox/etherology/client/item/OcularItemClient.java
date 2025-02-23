package ru.feytox.etherology.client.item;

import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import ru.feytox.etherology.client.block.seal.SealBlockRenderer;

@UtilityClass
public class OcularItemClient {

    public static void tickOcular(World world) {
        if (MinecraftClient.getInstance().options.getPerspective().isFirstPerson())
            SealBlockRenderer.refreshSeeSealsAbility(world.getTime(), true);
    }
}
