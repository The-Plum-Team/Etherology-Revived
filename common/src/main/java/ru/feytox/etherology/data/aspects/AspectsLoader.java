package ru.feytox.etherology.data.aspects;

import com.google.common.collect.ImmutableMap;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.LingeringPotionItem;
import net.minecraft.item.PotionItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.item.TippedArrowItem;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.feytox.etherology.magic.aspects.AspectContainer;
import ru.feytox.etherology.magic.aspects.AspectContainerId;
import ru.feytox.etherology.magic.aspects.AspectContainerType;
import ru.feytox.etherology.magic.aspects.AspectRegistryPart;
import ru.feytox.etherology.registry.misc.SharedAspectRegistries;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

/**
 * Resolves the synchronized aspect datapack registry into item and entity aspect containers.
 */
public final class AspectsLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(AspectsLoader.class);

    @Nullable
    private static ImmutableMap<AspectContainerId, AspectContainer> cache;

    @Nullable
    private static CompletableFuture<ImmutableMap<AspectContainerId, AspectContainer>>
            cacheFuture;

    private AspectsLoader() {
    }

    private static Optional<AspectContainer> get(
            World world,
            AspectContainerId id,
            boolean force
    ) {
        Map<AspectContainerId, AspectContainer> loadedCache = getCache(world, force);
        if (loadedCache == null) return Optional.empty();

        return Optional.ofNullable(loadedCache.get(id));
    }

    /**
     * Finds aspects for an item stack, applying its count when requested.
     *
     * @param world world whose synchronized dynamic registry is authoritative
     * @param stack item stack to inspect
     * @param multiplyCount whether ordinary item aspects are multiplied by stack size
     * @param force whether to wait for an in-progress cache build
     * @return the resolved aspect container, when the datapack defines one
     */
    public static Optional<AspectContainer> getAspects(
            World world,
            ItemStack stack,
            boolean multiplyCount,
            boolean force
    ) {
        if (stack.getItem() instanceof PotionItem) {
            return getPotionAspects(world, stack, force);
        }
        if (stack.getItem() instanceof TippedArrowItem) {
            return getTippedAspects(world, stack, force);
        }

        AspectContainer itemAspects = getItemAspects(world, stack, force).orElse(null);
        if (itemAspects == null) return Optional.empty();

        if (multiplyCount) {
            itemAspects = itemAspects.map(value -> value * stack.getCount());
        }
        return Optional.of(itemAspects);
    }

    /**
     * Visits every resolved container when a world registry is available.
     *
     * @param world world whose datapack registry should be read
     * @param consumer consumer for typed container identifiers and values
     */
    public static void forEach(
            @Nullable World world,
            BiConsumer<AspectContainerId, AspectContainer> consumer
    ) {
        if (world != null) {
            Map<AspectContainerId, AspectContainer> loadedCache = getCache(world, true);
            if (loadedCache != null) {
                loadedCache.forEach(consumer);
                return;
            }
        }
        LOGGER.warn("Aspects were not loaded during the addition of REI/EMI entries.");
    }

    private static Optional<AspectContainer> getItemAspects(
            World world,
            ItemStack stack,
            boolean force
    ) {
        return get(
                world,
                AspectContainerId.of(
                        Registries.ITEM.getId(stack.getItem()),
                        AspectContainerType.ITEM
                ),
                force
        );
    }

    /**
     * Resolves the appropriately typed potion aspects for a potion stack.
     *
     * @param world world whose datapack registry should be read
     * @param potionStack potion, splash-potion, or lingering-potion stack
     * @param force whether to wait for an in-progress cache build
     * @return the matching potion aspect container
     */
    public static Optional<AspectContainer> getPotionAspects(
            World world,
            ItemStack potionStack,
            boolean force
    ) {
        AspectContainerType type = AspectContainerType.POTION;
        if (potionStack.getItem() instanceof SplashPotionItem) {
            type = AspectContainerType.SPLASH_POTION;
        }
        if (potionStack.getItem() instanceof LingeringPotionItem) {
            type = AspectContainerType.LINGERING_POTION;
        }

        Potion potion = PotionUtil.getPotion(potionStack);
        if (potion == Potions.EMPTY) return Optional.empty();

        Identifier id = Registries.POTION.getId(potion);
        if (id == null) return Optional.empty();

        return get(world, AspectContainerId.of(id, type), force);
    }

    /**
     * Resolves tipped-arrow aspects using the arrow's potion identifier.
     *
     * @param world world whose datapack registry should be read
     * @param tippedStack tipped-arrow stack
     * @param force whether to wait for an in-progress cache build
     * @return the matching tipped-arrow aspect container
     */
    public static Optional<AspectContainer> getTippedAspects(
            World world,
            ItemStack tippedStack,
            boolean force
    ) {
        Potion potion = PotionUtil.getPotion(tippedStack);
        if (potion == Potions.EMPTY) return Optional.empty();

        Identifier id = Registries.POTION.getId(potion);
        if (id == null) return Optional.empty();

        return get(
                world,
                AspectContainerId.of(id, AspectContainerType.TIPPED_ARROW),
                force
        );
    }

    /**
     * Resolves aspects for an entity type.
     *
     * @param world world whose datapack registry should be read
     * @param entity entity to inspect
     * @param force whether to wait for an in-progress cache build
     * @return the matching entity aspect container
     */
    public static Optional<AspectContainer> getEntityAspects(
            World world,
            Entity entity,
            boolean force
    ) {
        return get(
                world,
                AspectContainerId.of(
                        Registries.ENTITY_TYPE.getId(entity.getType()),
                        AspectContainerType.ENTITY
                ),
                force
        );
    }

    /**
     * Invalidates resolved datapack state after either loader reloads or synchronizes registries.
     */
    public static void clearCache() {
        cache = null;
        if (cacheFuture != null) {
            cacheFuture.cancel(true);
            cacheFuture = null;
        }
    }

    @Nullable
    private static Map<AspectContainerId, AspectContainer> getCache(
            World world,
            boolean force
    ) {
        if (cache != null) return cache;
        if (cacheFuture != null) {
            if (!cacheFuture.isDone() && !force) return null;
            cache = cacheFuture.isDone() ? cacheFuture.join() : forceGetCache();
            cacheFuture = null;
            return cache;
        }

        cacheFuture = CompletableFuture.supplyAsync(() -> world
                        .getRegistryManager()
                        .get(SharedAspectRegistries.ASPECTS))
                .thenApplyAsync(Registry::stream)
                .thenApplyAsync(stream -> stream.reduce(AspectRegistryPart::merge))
                .thenApplyAsync(optional -> optional.map(AspectRegistryPart::applyParents))
                .thenApplyAsync(optional -> optional
                        .map(ImmutableMap::copyOf)
                        .orElseThrow());

        if (!cacheFuture.isDone() && !force) return null;
        cache = cacheFuture.isDone() ? cacheFuture.join() : forceGetCache();
        cacheFuture = null;
        return cache;
    }

    @Nullable
    private static ImmutableMap<AspectContainerId, AspectContainer> forceGetCache() {
        if (cacheFuture == null) return null;
        try {
            return cacheFuture.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | CancellationException exception) {
            return null;
        }
    }
}
