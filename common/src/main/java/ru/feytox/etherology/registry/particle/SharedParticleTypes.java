package ru.feytox.etherology.registry.particle;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.particle.effects.ElectricityParticleEffect;
import ru.feytox.etherology.particle.effects.ItemParticleEffect;
import ru.feytox.etherology.particle.effects.LightParticleEffect;
import ru.feytox.etherology.particle.effects.MovingParticleEffect;
import ru.feytox.etherology.particle.effects.ScalableParticleEffect;
import ru.feytox.etherology.particle.effects.SealParticleEffect;
import ru.feytox.etherology.particle.effects.SimpleParticleEffect;
import ru.feytox.etherology.particle.effects.SparkParticleEffect;
import ru.feytox.etherology.particle.effects.misc.FeyParticleEffect;
import ru.feytox.etherology.particle.effects.misc.FeyParticleType;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns Etherology's loader-neutral particle-type registrations in canonical ID order.
 */
public final class SharedParticleTypes {

    private static final SharedDeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            SharedDeferredRegister.create(RegistryKeys.PARTICLE_TYPE);

    public static final RegistrySupplier<FeyParticleType<LightParticleEffect>> LIGHT =
            register("light", LightParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<SimpleParticleEffect>> STEAM =
            register("steam", SimpleParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<SparkParticleEffect>> SPARK =
            register("spark", SparkParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<ElectricityParticleEffect>> ELECTRICITY1 =
            register("electricity1", ElectricityParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<ElectricityParticleEffect>> ELECTRICITY2 =
            register("electricity2", ElectricityParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<ItemParticleEffect>> ITEM =
            register("item", ItemParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<SimpleParticleEffect>> RISING =
            register("rising", SimpleParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<MovingParticleEffect>> VITAL =
            register("vital", MovingParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<SimpleParticleEffect>> SHOCKWAVE =
            register("shockwave", SimpleParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<MovingParticleEffect>> GLINT =
            register("glint_particle", MovingParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<SimpleParticleEffect>> ENERGY_ABSORPTION =
            register("energy_absorption", SimpleParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<MovingParticleEffect>> ARMILLARY_SPHERE =
            register("armillary_sphere", MovingParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<SimpleParticleEffect>> HAZE =
            register("haze", SimpleParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<SimpleParticleEffect>> ALCHEMY =
            register("alchemy", SimpleParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<MovingParticleEffect>> ETHER_STAR =
            register("ether_star", MovingParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<MovingParticleEffect>> ETHER_DOT =
            register("ether_dot", MovingParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<ScalableParticleEffect>> RESONATION =
            register("resonation", ScalableParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<ScalableParticleEffect>> LIGHTNING_BOLT =
            register("lightning_bolt", ScalableParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<ScalableParticleEffect>> SCALABLE_SWEEP =
            register("scalable_sweep", ScalableParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<SimpleParticleEffect>> REDSTONE_FLASH =
            register("redstone_flash", SimpleParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<SimpleParticleEffect>> REDSTONE_STREAM =
            register("redstone_stream", SimpleParticleEffect::new);
    public static final RegistrySupplier<FeyParticleType<SealParticleEffect>> SEAL =
            register("seal", SealParticleEffect::new);

    private SharedParticleTypes() {
    }

    /**
     * Attaches the shared particle-type registry before loader registry events run.
     */
    public static void register() {
        PARTICLE_TYPES.attach();
    }

    private static <T extends ParticleEffect> RegistrySupplier<FeyParticleType<T>> register(
            String id,
            FeyParticleEffect.DummyConstructor<T> dummyConstructor
    ) {
        return PARTICLE_TYPES.register(
                id,
                () -> new FeyParticleType<>(false, dummyConstructor)
        );
    }
}
