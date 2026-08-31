package ru.feytox.etherology.data.aspects;

import com.google.common.collect.ImmutableMap;
import lombok.val;
import net.minecraft.entity.Entity;
import net.minecraft.item.*;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.Etherology;
import ru.feytox.etherology.magic.aspects.AspectContainer;
import ru.feytox.etherology.magic.aspects.AspectContainerId;
import ru.feytox.etherology.magic.aspects.AspectContainerType;
import ru.feytox.etherology.magic.aspects.AspectRegistryPart;
import ru.feytox.etherology.registry.misc.RegistriesRegistry;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class AspectsLoader {

    // TODO: load aspects on client/server load
    @Nullable
    private static ImmutableMap<AspectContainerId, AspectContainer> cache = null;
    @Nullable
    private static CompletableFuture<ImmutableMap<AspectContainerId, AspectContainer>> cacheFuture = null;

    private static Optional<AspectContainer> get(World world, AspectContainerId id, boolean force) {
        Map<AspectContainerId, AspectContainer> cache = getCache(world, force);
        if (cache == null) return Optional.empty();

        return Optional.ofNullable(cache.get(id));
    }
    
    public static Optional<AspectContainer> getAspects(World world, ItemStack stack, boolean multiplyCount, boolean force) {
        if (stack.getItem() instanceof PotionItem)
            return getPotionAspects(world, stack, force);
        if (stack.getItem() instanceof TippedArrowItem)
            return getTippedAspects(world, stack, force);
        AspectContainer itemAspects = getAspects(world, stack, force).orElse(null);
        if (itemAspects == null)
            return Optional.empty();

        if (multiplyCount)
            itemAspects = itemAspects.map(value -> value * stack.getCount());
        return Optional.of(itemAspects);
    }

    public static void forEach(@Nullable World world, BiConsumer<AspectContainerId, AspectContainer> consumer) {
        if (world != null) {
            Map<AspectContainerId, AspectContainer> cache = getCache(world, true);
            if (cache != null) {
                cache.forEach(consumer);
                return;
            }
        }
        Etherology.ELOGGER.warn("Aspects were not loaded during the addition of REI/EMI entries.");
    }

    private static Optional<AspectContainer> getAspects(World world, ItemStack stack, boolean force) {
        return get(world, AspectContainerId.of(Registries.ITEM.getId(stack.getItem()), AspectContainerType.ITEM), force);
    }

    public static Optional<AspectContainer> getPotionAspects(World world, ItemStack potionStack, boolean force) {
        AspectContainerType type = AspectContainerType.POTION;
        if (potionStack.getItem() instanceof SplashPotionItem) type = AspectContainerType.SPLASH_POTION;
        if (potionStack.getItem() instanceof LingeringPotionItem) type = AspectContainerType.LINGERING_POTION;

        Potion potion = PotionUtil.getPotion(potionStack);
        if (potion == Potions.EMPTY) return Optional.empty();

        Identifier id = Registries.POTION.getId(potion);
        if (id == null) return Optional.empty();

        return get(world, AspectContainerId.of(id, type), force);
    }

    public static Optional<AspectContainer> getTippedAspects(World world, ItemStack tippedStack, boolean force) {
        Potion potion = PotionUtil.getPotion(tippedStack);
        if (potion == Potions.EMPTY) return Optional.empty();

        Identifier id = Registries.POTION.getId(potion);
        if (id == null) return Optional.empty();

        return get(world, AspectContainerId.of(id, AspectContainerType.TIPPED_ARROW), force);
    }

    public static Optional<AspectContainer> getEntityAspects(World world, Entity entity, boolean force) {
        return get(world, AspectContainerId.of(Registries.ENTITY_TYPE.getId(entity.getType()), AspectContainerType.ENTITY), force);
    }

    public static void clearCache() {
        cache = null;
        if (cacheFuture != null) {
            cacheFuture.cancel(true);
            cacheFuture = null;
        }
    }

    @Nullable
    private static Map<AspectContainerId, AspectContainer> getCache(World world, boolean force) {
        if (cache != null) return cache;
        if (cacheFuture != null) {
            if (!cacheFuture.isDone() && !force) return null;
            cache = cacheFuture.isDone() ? cacheFuture.join() : forceGetCache();
            cacheFuture = null;
            return cache;
        }

        cacheFuture = CompletableFuture.supplyAsync(() -> world.getRegistryManager().get(RegistriesRegistry.ASPECTS))
                .thenApplyAsync(Registry::stream)
                .thenApplyAsync(s -> s.reduce(AspectRegistryPart::merge))
                .thenApplyAsync(s -> s.map(AspectRegistryPart::applyParents))
                .thenApplyAsync(o -> o.map(ImmutableMap::copyOf).orElseThrow());

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
        } catch (Exception e) {
            return null;
        }
    }
}
