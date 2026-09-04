package ru.feytox.etherology.magic.aspects;

import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.data.aspects.AspectsLoader;

/**
 * Supplies aspects revealed when an Oculus targets a world object.
 */
public interface RevelationAspectProvider {

    /**
     * Resolves the aspects currently exposed by this provider.
     *
     * @param world world containing the provider
     * @return revealed aspects, or {@code null} when nothing can be revealed
     */
    @Nullable
    AspectContainer getRevelationAspects(World world);

    /**
     * Limits the number of aspects shown by Revelation rendering.
     *
     * @return maximum aspect count, or {@code -1} for no limit
     */
    default int getRevelationAspectsLimit() {
        return -1;
    }

    /**
     * Resolves Revelation data for supported block entities and item frames.
     *
     * @param world queried world
     * @param hitResult targeted block or entity
     * @return aspects and display limit, or {@code null} for unsupported targets
     */
    @Nullable
    static Pair<AspectContainer, Integer> getData(
            World world,
            HitResult hitResult
    ) {
        if (hitResult instanceof BlockHitResult blockHitResult) {
            BlockPos pos = blockHitResult.getBlockPos();
            if (world.getBlockEntity(pos) instanceof RevelationAspectProvider provider) {
                return Pair.of(
                        provider.getRevelationAspects(world),
                        provider.getRevelationAspectsLimit()
                );
            }
        }

        if (!(hitResult instanceof EntityHitResult entityHitResult)) return null;
        Entity entity = entityHitResult.getEntity();
        if (!(entity instanceof ItemFrameEntity itemFrame)) return null;

        return Pair.of(
                AspectsLoader.getAspects(
                        world,
                        itemFrame.getHeldItemStack(),
                        false,
                        false
                ).orElse(null),
                -1
        );
    }
}
