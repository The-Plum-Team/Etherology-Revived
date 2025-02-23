package ru.feytox.etherology.client.item.revelationView;

import lombok.experimental.UtilityClass;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import ru.feytox.etherology.client.block.seal.SealBlockRenderer;

@UtilityClass
public class RevelationViewItemClient {

    public static void tickRevelationView(World world, PlayerEntity player) {
        SealBlockRenderer.refreshSeeSealsAbility(world.getTime(), false);
        RevelationViewRenderer.tickData(world, player);
    }
}
