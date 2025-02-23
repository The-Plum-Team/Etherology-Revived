package ru.feytox.etherology.client.item;

import lombok.experimental.UtilityClass;
import net.minecraft.world.World;
import ru.feytox.etherology.client.block.seal.SealBlockRenderer;

@UtilityClass
public class OcularItemClient {

    public static void tickOcular(World world) {
        SealBlockRenderer.refreshSeeSealsAbility(world.getTime(), true);
    }
}
